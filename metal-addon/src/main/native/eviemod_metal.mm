// SPDX-License-Identifier: GPL-3.0-only
// Derived from Im-Fran/MetalCraft, commit a2cc82780d01a51d00a297f75cf07c221d7c8700.
// EvieMetal native layer: a thin JNI veneer over Metal.
// Metal objects cross into Java as retained pointers (jlong) and are released with Mtl.release().
// JNI calls run on the render thread; drawable/completion callbacks use the locks below.
// Every JNI entry owns a local autorelease pool: a pool in present() cannot drain objects
// created by earlier JNI calls. Retained handles and strong context fields survive these pools.

#import <Cocoa/Cocoa.h>
#import <Metal/Metal.h>
#import <QuartzCore/CAMetalLayer.h>
#include <atomic>
#include <mutex>
#include <string>
#include "dev_eviemod_metal_mtl_Mtl.h"

#define RETAIN(obj) ((jlong)(__bridge_retained void*)(obj))
#define OBJ(type, h) ((__bridge type)(void*)(h))

// ponytail: single global context, Minecraft only ever has one window/device.
static id<MTLDevice> gDevice;
static id<MTLCommandQueue> gQueue;
static CAMetalLayer* gLayer;
static BOOL gVsync = YES;  // Minecraft sets vsync before the layer exists.
// Vsync off: drawables are prefetched on a GCD queue so the render thread never blocks on the compositor.
static dispatch_queue_t gDrawableQueue;
static std::mutex gDrawableLock;
static id<CAMetalDrawable> gReadyDrawable;
static bool gFetchingDrawable;
static id<MTLSharedEvent> gEvent;
static uint64_t gEventValue;
static id<MTLCommandBuffer> gCmd, gLastCommand;
static NSView* gView;
static CALayer* gPreviousLayer;
static BOOL gPreviousWantsLayer;
static std::mutex gErrorLock;
static std::string gError;
static std::atomic<double> gGpuSeconds{0};  // GPU execution time of completed command buffers, for profiling.
// GPU time of recently completed command buffers by fence value, for Blaze3D timer queries (F3's GPU utilization).
static const uint64_t kHistorySize = 1024;
static struct { uint64_t fence; double seconds; } gHistory[kHistorySize];
static std::mutex gHistoryLock;
static const int kFramesInFlight = 3;
static uint64_t gFrameFence[kFramesInFlight];
static int gFrame;
static id<MTLBlitCommandEncoder> gBlit;
static id<MTLRenderCommandEncoder> gRender;
static id<MTLRenderPipelineState> gPresentPipeline;
static id<MTLSamplerState> gPresentSampler;
static id<MTLLibrary> gBuiltins;
static id<MTLDepthStencilState> gClearDepthState;
static NSMutableDictionary<NSNumber*, id<MTLRenderPipelineState>>* gClearPipelines;

static const char* kPresentShader = R"(
#include <metal_stdlib>
using namespace metal;
struct V { float4 pos [[position]]; float2 uv; };
vertex V present_vs(uint id [[vertex_id]]) {
    float2 p = float2((id << 1) & 2, id & 2);
    V v; v.pos = float4(p * 2.0 - 1.0, 0.0, 1.0);
    // Render targets keep OpenGL's bottom-up row order; the drawable is top-down.
    v.uv = float2(p.x, p.y);
    return v;
}
struct ClearParams { float4 color; float depth; };
struct CV { float4 pos [[position]]; };
vertex CV clear_vs(uint id [[vertex_id]], constant ClearParams& p [[buffer(0)]]) {
    float2 q = float2((id << 1) & 2, id & 2);
    CV v; v.pos = float4(q * 2.0 - 1.0, p.depth, 1.0);
    return v;
}
fragment float4 clear_fs(constant ClearParams& p [[buffer(0)]]) { return p.color; }
fragment float4 present_fs(V v [[stage_in]], texture2d<float> t [[texture(0)]], sampler s [[sampler(0)]]) {
    return float4(t.sample(s, v.uv).rgb, 1.0);
}
)";

static id<MTLCommandBuffer> cmd() {
    if (!gCmd) gCmd = [gQueue commandBuffer];
    return gCmd;
}

static void endBlit() {
    if (gBlit) { [gBlit endEncoding]; gBlit = nil; }
}

// Render encoders outlive Blaze3D render passes: a following pass on the same attachments without clears reuses the
// open encoder instead of storing and reloading the whole target (expensive on Apple's tile-based GPUs).
static bool gInPass;
static uint64_t gRenderGeneration;
static id<MTLTexture> gPassColor, gPassDepth;

// Dev-only GPU profiling: every render encoder records GPU timestamps at the start of its vertex stage and the end of
// its fragment stage (stage-boundary counters), labelled with the Blaze3D pass name(s) it contains. Results are
// resolved when the command buffer completes and accumulated per label.
static bool gProfile;
static NSString* gNextLabel;
static std::mutex gProfileLock;
static NSMutableDictionary<NSString*, NSNumber*>* gProfileSeconds;
static id<MTLCounterSet> gTimestampSet;
static id<MTLCounterSampleBuffer> gSamples;      // For the command buffer being recorded.
static NSMutableArray<NSMutableString*>* gSampleLabels;
static const NSUInteger kMaxProfiledEncoders = 1024;

/** Attaches timestamp sampling to a render pass descriptor; returns false when this frame's sample budget is used up. */
static void profilePass(MTLRenderPassDescriptor* d, NSString* label) {
    if (!gProfile || !gTimestampSet) return;
    if (!gSamples) {
        MTLCounterSampleBufferDescriptor* sd = [MTLCounterSampleBufferDescriptor new];
        sd.counterSet = gTimestampSet;
        sd.sampleCount = kMaxProfiledEncoders * 2;
        sd.storageMode = MTLStorageModeShared;
        gSamples = [gDevice newCounterSampleBufferWithDescriptor:sd error:nil];
        gSampleLabels = [NSMutableArray new];
    }
    NSUInteger k = gSampleLabels.count;
    if (!gSamples || k >= kMaxProfiledEncoders) return;
    d.sampleBufferAttachments[0].sampleBuffer = gSamples;
    d.sampleBufferAttachments[0].startOfVertexSampleIndex = 2 * k;
    d.sampleBufferAttachments[0].endOfVertexSampleIndex = MTLCounterDontSample;
    d.sampleBufferAttachments[0].startOfFragmentSampleIndex = MTLCounterDontSample;
    d.sampleBufferAttachments[0].endOfFragmentSampleIndex = 2 * k + 1;
    [gSampleLabels addObject:[NSMutableString stringWithString:label ?: @"(unlabelled)"]];
}

/** A merged pass adds its name to the encoder's label. */
static void profileMerge(NSString* label) {
    if (!gProfile || !gSampleLabels.count || !label) return;
    NSMutableString* current = gSampleLabels.lastObject;
    if (current.length < 1024 && ![current containsString:label]) [current appendFormat:@" + %@", label];
}

static void endRender() {
    if (gRender) { [gRender endEncoding]; gRender = nil; }
    gPassColor = gPassDepth = nil;
}

static id<MTLBlitCommandEncoder> blit() {
    endRender();
    if (!gBlit) gBlit = [cmd() blitCommandEncoder];
    return gBlit;
}

static void commit() {
    endRender();
    endBlit();
    [cmd() encodeSignalEvent:gEvent value:++gEventValue];
    uint64_t fenceValue = gEventValue;
    id<MTLCounterSampleBuffer> samples = gSamples;
    NSArray<NSMutableString*>* labels = gSampleLabels;
    gSamples = nil;
    gSampleLabels = nil;
    [gCmd addCompletedHandler:^(id<MTLCommandBuffer> cb) {
        @autoreleasepool {
            if (cb.status == MTLCommandBufferStatusError) {
                std::lock_guard<std::mutex> lock(gErrorLock);
                gError = cb.error.localizedDescription.UTF8String ?: "Metal command buffer failed";
            }
            gGpuSeconds.fetch_add(cb.GPUEndTime - cb.GPUStartTime);
            {
                std::lock_guard<std::mutex> lock(gHistoryLock);
                gHistory[fenceValue % kHistorySize] = {fenceValue, cb.GPUEndTime - cb.GPUStartTime};
            }
            if (!samples || !labels.count) return;
            NSData* data = [samples resolveCounterRange:NSMakeRange(0, labels.count * 2)];
            if (data.length < labels.count * 2 * sizeof(MTLCounterResultTimestamp)) return;
            const MTLCounterResultTimestamp* t = (const MTLCounterResultTimestamp*)data.bytes;
            std::lock_guard<std::mutex> lock(gProfileLock);
            // Encoders overlap (the next vertex stage starts while the previous fragments drain), so each encoder is
            // charged only the time it extends the timeline by: its end minus max(its start, the previous end).
            uint64_t previousEnd = 0;
            for (NSUInteger k = 0; k < labels.count; k++) {
                uint64_t start = t[2 * k].timestamp, end = t[2 * k + 1].timestamp;
                if (start == MTLCounterErrorValue || end == MTLCounterErrorValue || end < start) continue;
                start = MAX(start, previousEnd);
                if (end < start) continue;
                previousEnd = end;
                NSString* key = labels[k];
                if (!gProfileSeconds[key] && gProfileSeconds.count >= 2048) continue;
                gProfileSeconds[key] = @(gProfileSeconds[key].doubleValue + (end - start) * 1e-9);  // Apple GPU timestamps are ns.
                NSString* countKey = [key stringByAppendingString:@" #"];
                gProfileSeconds[countKey] = @(gProfileSeconds[countKey].doubleValue + 1);
            }
        }
    }];
    gLastCommand = gCmd;
    [gCmd commit];
    gCmd = nil;
}

static void throwJava(JNIEnv* env, NSString* msg) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), msg.UTF8String);
}

static MTLPixelFormat pixelFormat(jint f) {
    switch (f) {  // Mirrors TextureFormat ordinals: RGBA8, RED8, RED8I, DEPTH32.
        case 0: return MTLPixelFormatRGBA8Unorm;
        case 1: return MTLPixelFormatR8Unorm;
        case 2: return MTLPixelFormatR8Sint;
        case 3: return MTLPixelFormatDepth32Float;
        default: return MTLPixelFormatInvalid;
    }
}

static void prefetchDrawable() {
    std::lock_guard<std::mutex> lock(gDrawableLock);
    if (gFetchingDrawable || gReadyDrawable) return;
    gFetchingDrawable = true;
    if (!gDrawableQueue) gDrawableQueue = dispatch_queue_create("eviemod-metal.drawables", DISPATCH_QUEUE_SERIAL);
    dispatch_async(gDrawableQueue, ^{
        @autoreleasepool {
            id<CAMetalDrawable> d = [gLayer nextDrawable];
            std::lock_guard<std::mutex> l(gDrawableLock);
            gReadyDrawable = d;
            gFetchingDrawable = false;
        }
    });
}

/** Takes the prefetched drawable if it matches the frame size (a resize makes it stale). */
static id<CAMetalDrawable> takeReadyDrawable(id<MTLTexture> frame) {
    std::lock_guard<std::mutex> lock(gDrawableLock);
    id<CAMetalDrawable> d = gReadyDrawable;
    gReadyDrawable = nil;
    if (d && (d.texture.width != frame.width || d.texture.height != frame.height)) return nil;
    return d;
}

extern "C" {

JNIEXPORT jstring JNICALL Java_dev_eviemod_metal_mtl_Mtl_deviceName(JNIEnv* env, jclass) {
    @autoreleasepool {
        id<MTLDevice> device = gDevice ?: MTLCreateSystemDefaultDevice();
        return env->NewStringUTF(device ? device.name.UTF8String : "none");
    }
}

JNIEXPORT void JNICALL Java_dev_eviemod_metal_mtl_Mtl_init(JNIEnv* env, jclass, jlong nsWindow) {
    @autoreleasepool {
        gDevice = MTLCreateSystemDefaultDevice();
        if (!gDevice) return throwJava(env, @"No Metal device available");
        gQueue = [gDevice newCommandQueue];
        gEvent = [gDevice newSharedEvent];
        if (!gQueue || !gEvent) return throwJava(env, @"Metal queue/event allocation failed");

        NSError* error = nil;
        id<MTLLibrary> lib = gBuiltins = [gDevice newLibraryWithSource:@(kPresentShader) options:nil error:&error];
        if (!lib) return throwJava(env, error.localizedDescription);
        MTLDepthStencilDescriptor* dsd = [MTLDepthStencilDescriptor new];
        dsd.depthCompareFunction = MTLCompareFunctionAlways;
        dsd.depthWriteEnabled = YES;
        gClearDepthState = [gDevice newDepthStencilStateWithDescriptor:dsd];
        MTLRenderPipelineDescriptor* desc = [MTLRenderPipelineDescriptor new];
        desc.vertexFunction = [lib newFunctionWithName:@"present_vs"];
        desc.fragmentFunction = [lib newFunctionWithName:@"present_fs"];
        desc.colorAttachments[0].pixelFormat = MTLPixelFormatBGRA8Unorm;
        gPresentPipeline = [gDevice newRenderPipelineStateWithDescriptor:desc error:&error];
        if (!gPresentPipeline) return throwJava(env, error.localizedDescription);
        MTLSamplerDescriptor* sd = [MTLSamplerDescriptor new];
        gPresentSampler = [gDevice newSamplerStateWithDescriptor:sd];
        if (!gPresentSampler || !gClearDepthState) return throwJava(env, @"Metal presentation allocation failed");
    }
}

// Attachment is the final startup step, after native and shader preflight.
JNIEXPORT void JNICALL Java_dev_eviemod_metal_mtl_Mtl_attach(JNIEnv* env, jclass, jlong nsWindow) {
    @autoreleasepool {
        if (![NSThread isMainThread] || !nsWindow || !gPresentPipeline)
            return throwJava(env, @"Metal attachment requires a ready device and the macOS main thread");
        NSWindow* window = OBJ(NSWindow*, nsWindow);
        gView = window.contentView;
        gPreviousLayer = gView.layer;
        gPreviousWantsLayer = gView.wantsLayer;
        gLayer = [CAMetalLayer layer];
        gLayer.device = gDevice;
        gLayer.pixelFormat = MTLPixelFormatBGRA8Unorm;
        gLayer.contentsScale = window.backingScaleFactor;
        gLayer.frame = gView.bounds;
        gLayer.autoresizingMask = kCALayerWidthSizable | kCALayerHeightSizable;
        gLayer.framebufferOnly = YES;
        gLayer.displaySyncEnabled = gVsync;
        gView.wantsLayer = YES;
        gView.layer = gLayer;
    }
}

JNIEXPORT void JNICALL Java_dev_eviemod_metal_mtl_Mtl_checkError(JNIEnv* env, jclass) {
    @autoreleasepool {
        std::lock_guard<std::mutex> lock(gErrorLock);
        if (!gError.empty()) throwJava(env, @(gError.c_str()));
    }
}

JNIEXPORT void JNICALL Java_dev_eviemod_metal_mtl_Mtl_shutdown(JNIEnv*, jclass) {
    @autoreleasepool {
        if (gCmd) commit();
        if (gLastCommand) [gLastCommand waitUntilCompleted];
        if (gDrawableQueue) dispatch_sync(gDrawableQueue, ^{});
        {
            std::lock_guard<std::mutex> lock(gDrawableLock);
            gReadyDrawable = nil;
            gFetchingDrawable = false;
        }
        if (gView) {
            gView.layer = gPreviousLayer;
            gView.wantsLayer = gPreviousWantsLayer;
        }
        gView = nil; gPreviousLayer = nil; gLayer = nil;
        gQueue = nil; gEvent = nil; gDevice = nil; gDrawableQueue = nil;
        gLastCommand = nil; gBuiltins = nil; gPresentPipeline = nil;
        gPresentSampler = nil; gClearDepthState = nil;
        gClearPipelines = nil;
        gSamples = nil; gSampleLabels = nil; gTimestampSet = nil; gNextLabel = nil;
        { std::lock_guard<std::mutex> profileLock(gProfileLock); gProfileSeconds = nil; }
        gProfile = false; gInPass = false;
        gGpuSeconds.store(0);
        { std::lock_guard<std::mutex> historyLock(gHistoryLock); memset(gHistory, 0, sizeof(gHistory)); }
        memset(gFrameFence, 0, sizeof(gFrameFence)); gFrame = 0;
        gEventValue = 0;
        std::lock_guard<std::mutex> lock(gErrorLock);
        gError.clear();
    }
}

JNIEXPORT void JNICALL Java_dev_eviemod_metal_mtl_Mtl_setVsync(JNIEnv*, jclass, jboolean enabled) {
    @autoreleasepool {
        gVsync = enabled;
        gLayer.displaySyncEnabled = enabled;
    }
}

JNIEXPORT jint JNICALL Java_dev_eviemod_metal_mtl_Mtl_maxTextureSize(JNIEnv*, jclass) {
    @autoreleasepool {
        return [gDevice supportsFamily:MTLGPUFamilyApple3] ? 16384 : 8192;
    }
}

JNIEXPORT void JNICALL Java_dev_eviemod_metal_mtl_Mtl_release(JNIEnv*, jclass, jlong handle) {
    @autoreleasepool {
        if (handle) CFRelease((CFTypeRef)(void*)handle);
    }
}

JNIEXPORT jlong JNICALL Java_dev_eviemod_metal_mtl_Mtl_allocatedBytes(JNIEnv*, jclass) {
    @autoreleasepool {
        if (gLastCommand) [gLastCommand waitUntilCompleted];
        return (jlong)gDevice.currentAllocatedSize;
    }
}

// ---- Buffers ----------------------------------------------------------------

JNIEXPORT jlong JNICALL Java_dev_eviemod_metal_mtl_Mtl_newBuffer(JNIEnv*, jclass, jlong size) {
    @autoreleasepool {
        // Unified memory: shared storage lets Java map the contents directly with no staging copy.
        return RETAIN([gDevice newBufferWithLength:(NSUInteger)size options:MTLResourceStorageModeShared]);
    }
}

JNIEXPORT jlong JNICALL Java_dev_eviemod_metal_mtl_Mtl_bufferContents(JNIEnv*, jclass, jlong buffer) {
    @autoreleasepool {
        return (jlong)OBJ(id<MTLBuffer>, buffer).contents;
    }
}

JNIEXPORT void JNICALL Java_dev_eviemod_metal_mtl_Mtl_copyBuffer(JNIEnv*, jclass, jlong src, jlong srcOffset, jlong dst, jlong dstOffset, jlong length) {
    @autoreleasepool {
        [blit() copyFromBuffer:OBJ(id<MTLBuffer>, src) sourceOffset:srcOffset toBuffer:OBJ(id<MTLBuffer>, dst) destinationOffset:dstOffset size:length];
    }
}

JNIEXPORT jlong JNICALL Java_dev_eviemod_metal_mtl_Mtl_newTextureBuffer(JNIEnv*, jclass, jlong buffer, jint format, jlong length, jint pixelSize) {
    @autoreleasepool {
        NSUInteger width = (NSUInteger)(length / pixelSize);
        MTLTextureDescriptor* d = [MTLTextureDescriptor textureBufferDescriptorWithPixelFormat:pixelFormat(format) width:width
                                                                              resourceOptions:MTLResourceStorageModeShared usage:MTLTextureUsageShaderRead];
        return RETAIN([OBJ(id<MTLBuffer>, buffer) newTextureWithDescriptor:d offset:0 bytesPerRow:(NSUInteger)length]);
    }
}

// Terrain-specific signed 32-bit timestamps; returned view has independent retained ownership.
JNIEXPORT jlong JNICALL Java_dev_eviemod_metal_mtl_Mtl_newTerrainTimeView(JNIEnv*, jclass, jlong buffer, jlong length) {
    @autoreleasepool {
        MTLTextureDescriptor* d = [MTLTextureDescriptor textureBufferDescriptorWithPixelFormat:MTLPixelFormatR32Sint width:(NSUInteger)length / 4
                resourceOptions:MTLResourceStorageModeShared usage:MTLTextureUsageShaderRead];
        return RETAIN([OBJ(id<MTLBuffer>, buffer) newTextureWithDescriptor:d offset:0 bytesPerRow:(NSUInteger)length]);
    }
}

// ---- Textures ---------------------------------------------------------------

JNIEXPORT jlong JNICALL Java_dev_eviemod_metal_mtl_Mtl_newTexture(JNIEnv* env, jclass, jint format, jint width, jint height, jint mips, jboolean cube, jboolean renderTarget, jstring label) {
    @autoreleasepool {
        MTLTextureDescriptor* d = [MTLTextureDescriptor new];
        d.textureType = cube ? MTLTextureTypeCube : MTLTextureType2D;
        d.pixelFormat = pixelFormat(format);
        d.width = width;
        d.height = height;
        d.mipmapLevelCount = mips;
        d.storageMode = MTLStorageModePrivate;
        d.usage = MTLTextureUsageShaderRead | MTLTextureUsagePixelFormatView | (renderTarget ? MTLTextureUsageRenderTarget : 0);
        id<MTLTexture> tex = [gDevice newTextureWithDescriptor:d];
        if (!tex) return 0;
        if (label) {
            const char* s = env->GetStringUTFChars(label, nullptr);
            tex.label = @(s);
            env->ReleaseStringUTFChars(label, s);
        }
        return RETAIN(tex);
    }
}

JNIEXPORT jlong JNICALL Java_dev_eviemod_metal_mtl_Mtl_newTextureView(JNIEnv*, jclass, jlong texture, jint baseMip, jint mipCount) {
    @autoreleasepool {
        id<MTLTexture> tex = OBJ(id<MTLTexture>, texture);
        NSUInteger slices = tex.textureType == MTLTextureTypeCube ? 6 : 1;
        return RETAIN([tex newTextureViewWithPixelFormat:tex.pixelFormat textureType:tex.textureType
                                                  levels:NSMakeRange(baseMip, mipCount) slices:NSMakeRange(0, slices)]);
    }
}

JNIEXPORT void JNICALL Java_dev_eviemod_metal_mtl_Mtl_copyBufferToTexture(JNIEnv*, jclass, jlong buffer, jlong offset, jint bytesPerRow, jlong texture, jint slice, jint mip, jint x, jint y, jint w, jint h) {
    @autoreleasepool {
        [blit() copyFromBuffer:OBJ(id<MTLBuffer>, buffer) sourceOffset:offset sourceBytesPerRow:bytesPerRow sourceBytesPerImage:(NSUInteger)bytesPerRow * h
                    sourceSize:MTLSizeMake(w, h, 1) toTexture:OBJ(id<MTLTexture>, texture) destinationSlice:slice destinationLevel:mip
             destinationOrigin:MTLOriginMake(x, y, 0)];
    }
}

JNIEXPORT void JNICALL Java_dev_eviemod_metal_mtl_Mtl_copyTextureToBuffer(JNIEnv*, jclass, jlong texture, jint mip, jint x, jint y, jint w, jint h, jlong buffer, jlong offset, jint bytesPerRow) {
    @autoreleasepool {
        [blit() copyFromTexture:OBJ(id<MTLTexture>, texture) sourceSlice:0 sourceLevel:mip sourceOrigin:MTLOriginMake(x, y, 0) sourceSize:MTLSizeMake(w, h, 1)
                       toBuffer:OBJ(id<MTLBuffer>, buffer) destinationOffset:offset destinationBytesPerRow:bytesPerRow destinationBytesPerImage:(NSUInteger)bytesPerRow * h];
    }
}

JNIEXPORT void JNICALL Java_dev_eviemod_metal_mtl_Mtl_copyTextureToTexture(JNIEnv*, jclass, jlong src, jlong dst, jint mip, jint dstX, jint dstY, jint srcX, jint srcY, jint w, jint h) {
    @autoreleasepool {
        [blit() copyFromTexture:OBJ(id<MTLTexture>, src) sourceSlice:0 sourceLevel:mip sourceOrigin:MTLOriginMake(srcX, srcY, 0) sourceSize:MTLSizeMake(w, h, 1)
                      toTexture:OBJ(id<MTLTexture>, dst) destinationSlice:0 destinationLevel:mip destinationOrigin:MTLOriginMake(dstX, dstY, 0)];
    }
}

JNIEXPORT jlong JNICALL Java_dev_eviemod_metal_mtl_Mtl_newSampler(JNIEnv*, jclass, jboolean repeatU, jboolean repeatV, jboolean linearMin, jboolean linearMag, jint maxAnisotropy, jfloat maxLod) {
    @autoreleasepool {
        MTLSamplerDescriptor* d = [MTLSamplerDescriptor new];
        d.sAddressMode = repeatU ? MTLSamplerAddressModeRepeat : MTLSamplerAddressModeClampToEdge;
        d.tAddressMode = repeatV ? MTLSamplerAddressModeRepeat : MTLSamplerAddressModeClampToEdge;
        d.rAddressMode = MTLSamplerAddressModeClampToEdge;
        d.minFilter = linearMin ? MTLSamplerMinMagFilterLinear : MTLSamplerMinMagFilterNearest;
        d.magFilter = linearMag ? MTLSamplerMinMagFilterLinear : MTLSamplerMinMagFilterNearest;
        d.mipFilter = MTLSamplerMipFilterLinear;  // Blaze3D always uses *_MIPMAP_LINEAR minification.
        d.maxAnisotropy = maxAnisotropy;
        if (maxLod >= 0) d.lodMaxClamp = maxLod;
        return RETAIN([gDevice newSamplerStateWithDescriptor:d]);
    }
}

// ---- Shaders & pipelines ----------------------------------------------------

/** Returns an MTLLibrary handle, or throws with the compiler log. */
JNIEXPORT jlong JNICALL Java_dev_eviemod_metal_mtl_Mtl_newLibrary(JNIEnv* env, jclass, jstring source) {
    @autoreleasepool {
        id<MTLDevice> device = gDevice ?: MTLCreateSystemDefaultDevice();
        const char* s = env->GetStringUTFChars(source, nullptr);
        NSString* src = @(s);
        env->ReleaseStringUTFChars(source, s);
        MTLCompileOptions* opts = [MTLCompileOptions new];
        opts.languageVersion = MTLLanguageVersion2_4;
        NSError* error = nil;
        id<MTLLibrary> lib = [device newLibraryWithSource:src options:opts error:&error];
        if (!lib) {
            throwJava(env, error.localizedDescription);
            return 0;
        }
        return RETAIN(lib);
    }
}

JNIEXPORT jlong JNICALL Java_dev_eviemod_metal_mtl_Mtl_newFunction(JNIEnv* env, jclass, jlong library, jstring name) {
    @autoreleasepool {
        const char* s = env->GetStringUTFChars(name, nullptr);
        id<MTLFunction> fn = [OBJ(id<MTLLibrary>, library) newFunctionWithName:@(s)];
        env->ReleaseStringUTFChars(name, s);
        return fn ? RETAIN(fn) : 0;
    }
}

/**
 * attribs: flattened (location, MTLVertexFormat, offset) triples read from vertex buffer index vertexBufferIndex.
 * missing: locations the shader declares but the vertex format lacks; fed zeros from a constant buffer at index vertexBufferIndex - 1.
 */
JNIEXPORT jlong JNICALL Java_dev_eviemod_metal_mtl_Mtl_newRenderPipeline(JNIEnv* env, jclass, jlong vs, jlong fs, jint colorFormat, jint depthFormat,
        jboolean blend, jint srcRgb, jint dstRgb, jint srcAlpha, jint dstAlpha, jint writeMask,
        jintArray attribs, jintArray missing, jint stride, jint vertexBufferIndex, jstring label) {
    @autoreleasepool {
        MTLRenderPipelineDescriptor* d = [MTLRenderPipelineDescriptor new];
        d.vertexFunction = OBJ(id<MTLFunction>, vs);
        d.fragmentFunction = OBJ(id<MTLFunction>, fs);
        d.colorAttachments[0].pixelFormat = pixelFormat(colorFormat);
        d.colorAttachments[0].writeMask = writeMask;
        if (blend) {
            d.colorAttachments[0].blendingEnabled = YES;
            d.colorAttachments[0].sourceRGBBlendFactor = (MTLBlendFactor)srcRgb;
            d.colorAttachments[0].destinationRGBBlendFactor = (MTLBlendFactor)dstRgb;
            d.colorAttachments[0].sourceAlphaBlendFactor = (MTLBlendFactor)srcAlpha;
            d.colorAttachments[0].destinationAlphaBlendFactor = (MTLBlendFactor)dstAlpha;
        }
        if (depthFormat >= 0) d.depthAttachmentPixelFormat = pixelFormat(depthFormat);

        MTLVertexDescriptor* vd = [MTLVertexDescriptor vertexDescriptor];
        jsize n = env->GetArrayLength(attribs);
        jint* a = env->GetIntArrayElements(attribs, nullptr);
        for (jsize i = 0; i + 2 < n; i += 3) {
            vd.attributes[a[i]].format = (MTLVertexFormat)a[i + 1];
            vd.attributes[a[i]].offset = a[i + 2];
            vd.attributes[a[i]].bufferIndex = vertexBufferIndex;
        }
        env->ReleaseIntArrayElements(attribs, a, JNI_ABORT);
        if (n > 0) vd.layouts[vertexBufferIndex].stride = stride;

        jsize m = env->GetArrayLength(missing);
        if (m > 0) {
            jint* ms = env->GetIntArrayElements(missing, nullptr);
            for (jsize i = 0; i < m; i++) {
                vd.attributes[ms[i]].format = MTLVertexFormatFloat4;
                vd.attributes[ms[i]].offset = 0;
                vd.attributes[ms[i]].bufferIndex = vertexBufferIndex - 1;
            }
            env->ReleaseIntArrayElements(missing, ms, JNI_ABORT);
            vd.layouts[vertexBufferIndex - 1].stride = 16;
            vd.layouts[vertexBufferIndex - 1].stepFunction = MTLVertexStepFunctionConstant;
            vd.layouts[vertexBufferIndex - 1].stepRate = 0;
        }
        d.vertexDescriptor = vd;

        if (label) {
            const char* s = env->GetStringUTFChars(label, nullptr);
            d.label = @(s);
            env->ReleaseStringUTFChars(label, s);
        }
        NSError* error = nil;
        id<MTLRenderPipelineState> pso = [gDevice newRenderPipelineStateWithDescriptor:d error:&error];
        if (!pso) {
            throwJava(env, error.localizedDescription);
            return 0;
        }
        return RETAIN(pso);
    }
}

JNIEXPORT jlong JNICALL Java_dev_eviemod_metal_mtl_Mtl_newDepthStencilState(JNIEnv*, jclass, jint compare, jboolean write) {
    @autoreleasepool {
        MTLDepthStencilDescriptor* d = [MTLDepthStencilDescriptor new];
        d.depthCompareFunction = (MTLCompareFunction)compare;
        d.depthWriteEnabled = write;
        return RETAIN([gDevice newDepthStencilStateWithDescriptor:d]);
    }
}

// ---- Render passes ----------------------------------------------------------

/** Views of the same texture level are the same attachment even though Blaze3D hands out distinct view objects. */
static bool sameAttachment(id<MTLTexture> a, id<MTLTexture> b) {
    if (a == b) return true;
    if (!a || !b) return false;
    return (a.parentTexture ?: a) == (b.parentTexture ?: b) && a.parentRelativeLevel == b.parentRelativeLevel;
}

JNIEXPORT void JNICALL Java_dev_eviemod_metal_mtl_Mtl_beginPass(JNIEnv*, jclass, jlong color, jboolean clearColor, jfloat r, jfloat g, jfloat b, jfloat a,
        jlong depth, jboolean clearDepth, jdouble depthValue) {
    @autoreleasepool {
        id<MTLTexture> colorView = OBJ(id<MTLTexture>, color), depthView = OBJ(id<MTLTexture>, depth);
        gInPass = true;
        if (gRender && !clearColor && !clearDepth && sameAttachment(colorView, gPassColor) && sameAttachment(depthView, gPassDepth)) {
            profileMerge(gNextLabel);
            return;
        }
        endRender();
        endBlit();
        gPassColor = colorView;
        gPassDepth = depthView;
        MTLRenderPassDescriptor* d = [MTLRenderPassDescriptor renderPassDescriptor];
        id<MTLTexture> colorTex = OBJ(id<MTLTexture>, color);
        if (colorTex) {
            d.colorAttachments[0].texture = colorTex;
            d.colorAttachments[0].loadAction = clearColor ? MTLLoadActionClear : MTLLoadActionLoad;
            d.colorAttachments[0].clearColor = MTLClearColorMake(r, g, b, a);
            d.colorAttachments[0].storeAction = MTLStoreActionStore;
        }
        if (depth) {
            d.depthAttachment.texture = OBJ(id<MTLTexture>, depth);
            d.depthAttachment.loadAction = clearDepth ? MTLLoadActionClear : MTLLoadActionLoad;
            d.depthAttachment.clearDepth = depthValue;
            d.depthAttachment.storeAction = MTLStoreActionStore;
        }
        profilePass(d, gNextLabel);
        gRender = [cmd() renderCommandEncoderWithDescriptor:d];
        ++gRenderGeneration;
        // Front faces are counter-clockwise in GL; the Y flip in the vertex stage mirrors them to clockwise.
        [gRender setFrontFacingWinding:MTLWindingClockwise];
    }
}

JNIEXPORT void JNICALL Java_dev_eviemod_metal_mtl_Mtl_endPass(JNIEnv*, jclass) {
    @autoreleasepool {
        gInPass = false;  // The encoder stays open for a possible merge; endRender() closes it when anything else needs the command buffer.
    }
}

JNIEXPORT jlong JNICALL Java_dev_eviemod_metal_mtl_Mtl_renderEncoderGeneration(JNIEnv*, jclass) {
    return gRender ? (jlong)gRenderGeneration : 0;
}

JNIEXPORT void JNICALL Java_dev_eviemod_metal_mtl_Mtl_setPipelineState(JNIEnv*, jclass, jlong pso, jlong depthState, jint cull, jboolean wireframe, jfloat depthBiasConstant, jfloat depthBiasSlope) {
    @autoreleasepool {
        [gRender setRenderPipelineState:OBJ(id<MTLRenderPipelineState>, pso)];
        [gRender setDepthStencilState:OBJ(id<MTLDepthStencilState>, depthState)];
        [gRender setCullMode:(MTLCullMode)cull];
        [gRender setTriangleFillMode:wireframe ? MTLTriangleFillModeLines : MTLTriangleFillModeFill];
        [gRender setDepthBias:depthBiasConstant slopeScale:depthBiasSlope clamp:0];
    }
}

JNIEXPORT void JNICALL Java_dev_eviemod_metal_mtl_Mtl_setScissor(JNIEnv*, jclass, jint x, jint y, jint w, jint h) {
    @autoreleasepool {
        [gRender setScissorRect:(MTLScissorRect){(NSUInteger)x, (NSUInteger)y, (NSUInteger)w, (NSUInteger)h}];
    }
}

JNIEXPORT void JNICALL Java_dev_eviemod_metal_mtl_Mtl_setBuffer(JNIEnv*, jclass, jboolean fragment, jint index, jlong buffer, jlong offset) {
    @autoreleasepool {
        if (fragment) [gRender setFragmentBuffer:OBJ(id<MTLBuffer>, buffer) offset:offset atIndex:index];
        else [gRender setVertexBuffer:OBJ(id<MTLBuffer>, buffer) offset:offset atIndex:index];
    }
}

JNIEXPORT void JNICALL Java_dev_eviemod_metal_mtl_Mtl_setTexture(JNIEnv*, jclass, jboolean fragment, jint index, jlong texture, jint samplerIndex, jlong sampler) {
    @autoreleasepool {
        id<MTLTexture> tex = OBJ(id<MTLTexture>, texture);
        id<MTLSamplerState> smp = OBJ(id<MTLSamplerState>, sampler);
        if (fragment) {
            [gRender setFragmentTexture:tex atIndex:index];
            if (smp && samplerIndex >= 0) [gRender setFragmentSamplerState:smp atIndex:samplerIndex];
        } else {
            [gRender setVertexTexture:tex atIndex:index];
            if (smp && samplerIndex >= 0) [gRender setVertexSamplerState:smp atIndex:samplerIndex];
        }
    }
}

JNIEXPORT void JNICALL Java_dev_eviemod_metal_mtl_Mtl_draw(JNIEnv*, jclass, jint primitive, jint first, jint count, jint instances) {
    @autoreleasepool {
        [gRender drawPrimitives:(MTLPrimitiveType)primitive vertexStart:first vertexCount:count instanceCount:instances];
    }
}

JNIEXPORT void JNICALL Java_dev_eviemod_metal_mtl_Mtl_drawIndexed(JNIEnv*, jclass, jint primitive, jint count, jboolean uint32, jlong indexBuffer, jlong indexOffset, jint instances, jint baseVertex) {
    @autoreleasepool {
        [gRender drawIndexedPrimitives:(MTLPrimitiveType)primitive indexCount:count indexType:uint32 ? MTLIndexTypeUInt32 : MTLIndexTypeUInt16
                           indexBuffer:OBJ(id<MTLBuffer>, indexBuffer) indexBufferOffset:indexOffset instanceCount:instances baseVertex:baseVertex baseInstance:0];
    }
}

/** Scissored clear (Metal load actions only clear whole attachments). Either texture may be 0. */
JNIEXPORT void JNICALL Java_dev_eviemod_metal_mtl_Mtl_clearRegion(JNIEnv* env, jclass, jlong color, jint colorFormat, jlong depth,
        jfloat r, jfloat g, jfloat b, jfloat a, jfloat depthValue, jint x, jint y, jint w, jint h) {
    @autoreleasepool {
        // ponytail: one cached pipeline per (color, depth) format pair; only a couple of combinations exist.
        if (!gClearPipelines) gClearPipelines = [NSMutableDictionary new];
        NSNumber* key = @((color ? colorFormat : 15) * 16 + (depth ? 1 : 0));
        id<MTLRenderPipelineState> pso = gClearPipelines[key];
        if (!pso) {
            MTLRenderPipelineDescriptor* d = [MTLRenderPipelineDescriptor new];
            d.vertexFunction = [gBuiltins newFunctionWithName:@"clear_vs"];
            d.fragmentFunction = [gBuiltins newFunctionWithName:@"clear_fs"];
            if (color) d.colorAttachments[0].pixelFormat = pixelFormat(colorFormat);
            if (depth) d.depthAttachmentPixelFormat = MTLPixelFormatDepth32Float;
            NSError* error = nil;
            pso = gClearPipelines[key] = [gDevice newRenderPipelineStateWithDescriptor:d error:&error];
            if (!pso) return throwJava(env, error.localizedDescription);
        }
        Java_dev_eviemod_metal_mtl_Mtl_beginPass(env, nullptr, color, JNI_FALSE, 0, 0, 0, 0, depth, JNI_FALSE, 0);
        struct { float color[4]; float depth; float pad[3]; } params = {{r, g, b, a}, depthValue, {}};
        [gRender setRenderPipelineState:pso];
        // A merged encoder retains the previous draw's rasterization state.
        [gRender setCullMode:MTLCullModeNone];
        [gRender setTriangleFillMode:MTLTriangleFillModeFill];
        [gRender setDepthBias:0 slopeScale:0 clamp:0];
        if (depth) [gRender setDepthStencilState:gClearDepthState];
        [gRender setScissorRect:(MTLScissorRect){(NSUInteger)x, (NSUInteger)y, (NSUInteger)w, (NSUInteger)h}];
        [gRender setVertexBytes:&params length:sizeof(params) atIndex:0];
        [gRender setFragmentBytes:&params length:sizeof(params) atIndex:0];
        [gRender drawPrimitives:MTLPrimitiveTypeTriangle vertexStart:0 vertexCount:3];
        Java_dev_eviemod_metal_mtl_Mtl_endPass(env, nullptr);
    }
}

JNIEXPORT void JNICALL Java_dev_eviemod_metal_mtl_Mtl_setBytes(JNIEnv*, jclass, jboolean fragment, jint index, jlong address, jint length) {
    @autoreleasepool {
        if (fragment) [gRender setFragmentBytes:(const void*)address length:length atIndex:index];
        else [gRender setVertexBytes:(const void*)address length:length atIndex:index];
    }
}

/** glMultiDrawElementsBaseVertex over raw arrays (counts: uint32, offsets: pointer-sized byte offsets, baseVertices: int32). */
JNIEXPORT void JNICALL Java_dev_eviemod_metal_mtl_Mtl_multiDrawIndexed(JNIEnv*, jclass, jint primitive, jboolean uint32, jlong indexBuffer,
        jlong counts, jlong offsets, jlong baseVertices, jint drawCount) {
    @autoreleasepool {
        id<MTLBuffer> ib = OBJ(id<MTLBuffer>, indexBuffer);
        const uint32_t* c = (const uint32_t*)counts;
        const uintptr_t* o = (const uintptr_t*)offsets;
        const int32_t* b = (const int32_t*)baseVertices;
        MTLIndexType type = uint32 ? MTLIndexTypeUInt32 : MTLIndexTypeUInt16;
        for (jint i = 0; i < drawCount; i++) {
            if (c[i] == 0) continue;
            [gRender drawIndexedPrimitives:(MTLPrimitiveType)primitive indexCount:c[i] indexType:type indexBuffer:ib indexBufferOffset:o[i]
                             instanceCount:1 baseVertex:b[i] baseInstance:0];
        }
    }
}

// ---- Frame & synchronization -------------------------------------------------

JNIEXPORT void JNICALL Java_dev_eviemod_metal_mtl_Mtl_present(JNIEnv* env, jclass, jlong texture) {
    @autoreleasepool {
        endRender();
        endBlit();
        id<MTLTexture> tex = OBJ(id<MTLTexture>, texture);
        CGSize size = CGSizeMake(tex.width, tex.height);
        if (!CGSizeEqualToSize(gLayer.drawableSize, size)) gLayer.drawableSize = size;
        id<CAMetalDrawable> drawable = takeReadyDrawable(tex);
        if (!drawable && gVsync) drawable = [gLayer nextDrawable];
        // With vsync off, a windowed layer still hands out drawables at the display rate; drop the frame instead of
        // blocking, like GL does with swap interval 0.
        if (!gVsync) prefetchDrawable();
        if (drawable) {
            MTLRenderPassDescriptor* d = [MTLRenderPassDescriptor renderPassDescriptor];
            d.colorAttachments[0].texture = drawable.texture;
            d.colorAttachments[0].loadAction = MTLLoadActionDontCare;
            d.colorAttachments[0].storeAction = MTLStoreActionStore;
            profilePass(d, @"Present");
            id<MTLRenderCommandEncoder> enc = [cmd() renderCommandEncoderWithDescriptor:d];
            [enc setRenderPipelineState:gPresentPipeline];
            [enc setFragmentTexture:tex atIndex:0];
            [enc setFragmentSamplerState:gPresentSampler atIndex:0];
            [enc drawPrimitives:MTLPrimitiveTypeTriangle vertexStart:0 vertexCount:3];
            [enc endEncoding];
            [cmd() presentDrawable:drawable];
        }
        commit();

        // Keep the CPU at most kFramesInFlight frames ahead of the GPU.
        gFrameFence[gFrame] = gEventValue;
        gFrame = (gFrame + 1) % kFramesInFlight;
        if (gEvent.signaledValue < gFrameFence[gFrame] &&
                ![gEvent waitUntilSignaledValue:gFrameFence[gFrame] timeoutMS:5000]) {
            throwJava(env, @"Metal GPU did not complete queued frames within 5 seconds; stopping to bound retained resources");
        }
    }
}

/**
 * A fence covering everything recorded so far. It doesn't submit: it's the value the next commit will signal, so
 * Minecraft's many fences per frame cost one command buffer. Waiting on a not-yet-submitted fence submits first.
 */
JNIEXPORT jlong JNICALL Java_dev_eviemod_metal_mtl_Mtl_fence(JNIEnv*, jclass) {
    @autoreleasepool {
        return (jlong)(gEventValue + 1);
    }
}

JNIEXPORT void JNICALL Java_dev_eviemod_metal_mtl_Mtl_setGpuProfiling(JNIEnv*, jclass, jboolean enabled) {
    @autoreleasepool {
        gProfile = enabled;
        { std::lock_guard<std::mutex> lock(gProfileLock); gProfileSeconds = enabled ? [NSMutableDictionary new] : nil; }
        gTimestampSet = nil;
        if (!enabled) return;
        for (id<MTLCounterSet> set in gDevice.counterSets) {
            if ([set.name isEqualToString:MTLCommonCounterSetTimestamp]) gTimestampSet = set;
        }
        if (![gDevice supportsCounterSampling:MTLCounterSamplingPointAtStageBoundary]) gTimestampSet = nil;
    }
}

JNIEXPORT void JNICALL Java_dev_eviemod_metal_mtl_Mtl_setPassLabel(JNIEnv* env, jclass, jstring label) {
    @autoreleasepool {
        const char* s = env->GetStringUTFChars(label, nullptr);
        NSString* value = @(s);
        gNextLabel = value.length > 256 ? [value substringToIndex:256] : value;
        env->ReleaseStringUTFChars(label, s);
    }
}

/** "label=seconds" lines accumulated since the last call. */
JNIEXPORT jstring JNICALL Java_dev_eviemod_metal_mtl_Mtl_takeGpuProfile(JNIEnv* env, jclass) {
    @autoreleasepool {
        std::lock_guard<std::mutex> lock(gProfileLock);
        NSMutableString* out = [NSMutableString new];
        for (NSString* key in gProfileSeconds) [out appendFormat:@"%@=%f\n", key, gProfileSeconds[key].doubleValue];
        [gProfileSeconds removeAllObjects];
        return env->NewStringUTF(out.UTF8String);
    }
}

/**
 * GPU nanoseconds spent on the command buffers with fence values in [first, last], or -1 until they've all completed.
 * Entries older than the history window are skipped, which only happens for queries read long after the fact.
 */
JNIEXPORT jlong JNICALL Java_dev_eviemod_metal_mtl_Mtl_gpuNanosBetween(JNIEnv*, jclass, jlong first, jlong last) {
    @autoreleasepool {
        if (gEvent.signaledValue < (uint64_t)last) return -1;
        std::lock_guard<std::mutex> lock(gHistoryLock);
        double seconds = 0;
        for (uint64_t v = first; v <= (uint64_t)last; v++) {
            if (gHistory[v % kHistorySize].fence == v) seconds += gHistory[v % kHistorySize].seconds;
        }
        return (jlong)(seconds * 1e9);
    }
}

/** GPU execution time (seconds) accumulated since the last call. */
JNIEXPORT jdouble JNICALL Java_dev_eviemod_metal_mtl_Mtl_takeGpuSeconds(JNIEnv*, jclass) {
    @autoreleasepool {
        return gGpuSeconds.exchange(0);
    }
}

/** Highest fence value the GPU has finished; never submits. */
JNIEXPORT jlong JNICALL Java_dev_eviemod_metal_mtl_Mtl_completedFence(JNIEnv*, jclass) {
    @autoreleasepool {
        return (jlong)gEvent.signaledValue;
    }
}

JNIEXPORT jboolean JNICALL Java_dev_eviemod_metal_mtl_Mtl_fenceWait(JNIEnv*, jclass, jlong value, jlong timeoutMs) {
    @autoreleasepool {
        if (gEvent.signaledValue >= (uint64_t)value) return JNI_TRUE;
        if (timeoutMs <= 0) return JNI_FALSE;
        if ((uint64_t)value > gEventValue && !gInPass) @autoreleasepool { commit(); }
        return [gEvent waitUntilSignaledValue:(uint64_t)value timeoutMS:(uint64_t)MIN(timeoutMs, (jlong)UINT32_MAX)];
    }
}

}  // extern "C"
