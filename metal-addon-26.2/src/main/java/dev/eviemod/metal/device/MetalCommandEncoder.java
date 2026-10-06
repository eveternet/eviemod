// SPDX-License-Identifier: GPL-3.0-only
// Derived from Im-Fran/MetalCraft, commit a2cc82780d01a51d00a297f75cf07c221d7c8700.
package dev.eviemod.metal.device;

import dev.eviemod.metal.mtl.Mtl;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoderBackend;
import com.mojang.blaze3d.systems.GpuQueryPool;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import org.joml.Vector4fc;
import com.mojang.blaze3d.systems.RenderPassBackend;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Supplier;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

/**
 * Records Blaze3D commands into the current MTLCommandBuffer. Unlike GL, nothing executes until the command buffer is
 * committed (on present or fence), so every CPU-side update to a resource that queued GPU work may read goes through a
 * staging buffer + blit to keep GL's in-order semantics.
 */
public class MetalCommandEncoder implements CommandEncoderBackend {
    final MetalDevice device;
    private boolean inRenderPass;
    private long noDepthState;

    MetalCommandEncoder(MetalDevice device) {
        this.device = device;
    }

    public boolean isInRenderPass() { return inRenderPass; }

    void close() { transientMemory.close(); Mtl.release(noDepthState); noDepthState = 0; }

    long noDepthState() {
        if (noDepthState == 0) noDepthState = Mtl.newDepthStencilState(Mtl.COMPARE_ALWAYS, false);
        return noDepthState;
    }

    private void assertNoRenderPass() {
        if (inRenderPass) throw new IllegalStateException("Close the existing render pass before performing additional commands");
    }

    private final MetalTransientMemory transientMemory = new MetalTransientMemory();
    @Override public com.mojang.blaze3d.systems.TransientMemory transientMemory() { return transientMemory; }
    @Override public void submit() { assertNoRenderPass(); Mtl.submit(); transientMemory.rotate(); UploadRing.trimCompleted(); }
    @Override public void submitRenderPass() { finishRenderPass(); }

    @Override public RenderPassBackend createRenderPass(RenderPassDescriptor descriptor) {
        assertNoRenderPass();
        if (descriptor.colorAttachments().size() != 1 || descriptor.colorAttachments().getFirst() == null)
            throw new UnsupportedOperationException("Metal 26.2 currently requires one color attachment; requested " + descriptor.colorAttachments().size());
        var attachment = descriptor.colorAttachments().getFirst();
        var color = attachment.textureView();
        var depth = descriptor.depthAttachment() == null ? null : descriptor.depthAttachment().textureView();
        var area = descriptor.renderArea;
        if (color.isClosed() || (depth != null && depth.isClosed())) throw new IllegalStateException("Closed render attachment");
        var clear = attachment.clearValue();
        var depthClear = descriptor.depthAttachment() == null ? OptionalDouble.empty() : descriptor.depthAttachment().clearValue();
        boolean full = area == null || (area.x() == 0 && area.y() == 0 && area.width() == color.getWidth(0) && area.height() == color.getHeight(0));
        if (!full && (clear.isPresent() || depthClear.isPresent())) {
            // The existing regional clear writes both aspects; reject ambiguous single-aspect combinations.
            if (clear.isEmpty() || (depth != null && depthClear.isEmpty()))
                throw new UnsupportedOperationException("Partial-area clear requires explicit values for every attached aspect");
            Vector4fc c = clear.get();
            Mtl.clearRegion(((MetalTextureView) color).handle, MetalFormats.texture(color.texture().getFormat()), depth == null ? 0 : ((MetalTextureView) depth).handle,
                    c.x(), c.y(), c.z(), c.w(), (float) depthClear.orElse(1), area.x(), area.y(), area.width(), area.height());
        }
        Vector4fc c = clear.orElse(new org.joml.Vector4f());
        if (Mtl.GPU_PROFILING) Mtl.setPassLabel(descriptor.label().get());
        Mtl.beginPass(((MetalTextureView) color).handle, full && clear.isPresent(), c.x(), c.y(), c.z(), c.w(),
                depth == null ? 0 : ((MetalTextureView) depth).handle, full && depthClear.isPresent(), depthClear.orElse(1));
        inRenderPass = true;
        return new MetalRenderPass(this, color, depth, area);
    }

    void finishRenderPass() {
        try { Mtl.endPass(); }
        finally { inRenderPass = false; }
    }

    @Override
    public void clearColorTexture(GpuTexture texture, Vector4fc color) {
        if (Mtl.GPU_PROFILING) Mtl.setPassLabel("Clear " + describe(texture));
        assertNoRenderPass();
        Mtl.beginPass(handle(texture), true, color.x(), color.y(), color.z(), color.w(), 0, false, 0);
        Mtl.endPass();
    }

    @Override
    public void clearColorAndDepthTextures(GpuTexture color, Vector4fc c, GpuTexture depth, double depthValue) {
        if (Mtl.GPU_PROFILING) Mtl.setPassLabel("Clear " + describe(color) + " + depth");
        assertNoRenderPass();
        Mtl.beginPass(handle(color), true, c.x(), c.y(), c.z(), c.w(), handle(depth), true, depthValue);
        Mtl.endPass();
    }

    @Override
    public void clearColorAndDepthTextures(GpuTexture color, Vector4fc c, GpuTexture depth, double depthValue, int x, int y, int w, int h) {
        if (Mtl.GPU_PROFILING) Mtl.setPassLabel("Clear region " + describe(color));
        assertNoRenderPass();
        Mtl.clearRegion(handle(color), MetalFormats.texture(color.getFormat()), handle(depth), c.x(), c.y(), c.z(), c.w(),
                (float) depthValue, x, y, w, h);
    }

    @Override
    public void clearDepthTexture(GpuTexture depth, double depthValue) {
        if (Mtl.GPU_PROFILING) Mtl.setPassLabel("Clear depth " + describe(depth));
        assertNoRenderPass();
        Mtl.beginPass(0, false, 0, 0, 0, 0, handle(depth), true, depthValue);
        Mtl.endPass();
    }

    @Override
    public void writeToBuffer(GpuBufferSlice slice, ByteBuffer data) {
        assertNoRenderPass();
        int length = data.remaining();
        if (length == 0) return;
        if (length > slice.length()) throw new IllegalArgumentException("Cannot write more data than the slice allows (attempting to write " + length + " bytes into a slice of length " + slice.length() + ")");
        MetalBuffer buffer = (MetalBuffer) slice.buffer();
        // Orphaning keeps writes out of blit encoders, which would split the surrounding render passes.
        if (buffer.tryOrphanWrite(slice.offset(), MemoryUtil.memAddress(data), length)) return;
        UploadRing.Slice staging = UploadRing.reserve(length);
        MemoryUtil.memCopy(MemoryUtil.memAddress(data), staging.address(), length);
        Mtl.copyBuffer(staging.buffer(), staging.offset(), buffer.handle, slice.offset(), length);
        buffer.markGpuWrite();
    }

    @Override
    public void copyToBuffer(GpuBufferSlice src, GpuBufferSlice dst) {
        assertNoRenderPass();
        if (src.length() != dst.length()) throw new IllegalArgumentException("Cannot copy from slice of size " + src.length() + " to slice of size " + dst.length() + ", they must be equal");
        Mtl.copyBuffer(((MetalBuffer) src.buffer()).handle, src.offset(), ((MetalBuffer) dst.buffer()).handle, dst.offset(), src.length());
        ((MetalBuffer) dst.buffer()).markGpuWrite();
    }

    @Override public void writeToTexture(GpuTexture texture, ByteBuffer data, int mip, int layer, int x, int y, int w, int h) {
        assertNoRenderPass(); checkTextureWrite(texture, mip, layer, x, y, w, h);
        if (w == 0 || h == 0 || mip >= ((MetalTexture) texture).metalMips) return;
        int bytes = texture.getFormat().blockSize();
        if ((long) w * h * bytes > data.remaining()) throw new IllegalArgumentException("Texture upload exceeds source buffer");
        upload(texture, MemoryUtil.memAddress(data), (long) w * bytes, bytes, mip, layer, x, y, w, h);
    }
    @Override public void copyBufferToTexture(GpuBufferSlice source, int sourceX, int sourceY, int rowLength, int imageHeight,
            GpuTexture texture, int x, int y, int w, int h, int mip, int layer) {
        assertNoRenderPass(); checkTextureWrite(texture, mip, layer, x, y, w, h);
        if (w == 0 || h == 0 || mip >= ((MetalTexture) texture).metalMips) return;
        int bytes = texture.getFormat().blockSize();
        long offset = ((long) sourceY * rowLength + sourceX) * bytes;
        long end = offset + ((long) (h - 1) * rowLength + w) * bytes;
        if (offset < 0 || end > source.length()) throw new IllegalArgumentException("Texture copy exceeds buffer slice");
        Mtl.copyBufferToTexture(((MetalBuffer) source.buffer()).handle, source.offset() + offset, rowLength * bytes,
                handle(texture), layer, mip, x, y, w, h);
    }

    private static void checkTextureWrite(GpuTexture texture, int mip, int layer, int x, int y, int w, int h) {
        if (x < 0 || y < 0 || w < 0 || h < 0 || layer < 0) throw new IllegalArgumentException("Negative texture copy range");
        if (mip < 0 || mip >= texture.getMipLevels()) throw new IllegalArgumentException("Invalid mipLevel " + mip + ", must be >= 0 and < " + texture.getMipLevels());
        if (x + w > texture.getWidth(mip) || y + h > texture.getHeight(mip)) {
            throw new IllegalArgumentException("Dest texture (" + texture.getWidth(mip) + "x" + texture.getHeight(mip) + ") is not large enough to write a rectangle of " + w + "x" + h + " at " + x + "x" + y);
        }
        if (texture.isClosed()) throw new IllegalStateException("Destination texture is closed");
        if ((texture.usage() & GpuTexture.USAGE_COPY_DST) == 0) throw new IllegalStateException("Color texture must have USAGE_COPY_DST to be a destination for a write");
        if (layer >= texture.getDepthOrLayers()) throw new UnsupportedOperationException("Depth or layer is out of range, must be >= 0 and < " + texture.getDepthOrLayers());
    }

    /** Packs the rectangle into a staging buffer in the texture's pixel format (expanding/narrowing channels like GL does) and blits it. */
    private static void upload(GpuTexture texture, long src, long srcStride, int srcComps, int mip, int layer, int x, int y, int w, int h) {
        if (mip >= ((MetalTexture) texture).metalMips || w == 0 || h == 0) return;
        int dstComps = texture.getFormat().blockSize();
        int rowBytes = w * dstComps;
        UploadRing.Slice staging = UploadRing.reserve((long) rowBytes * h);
        long dst = staging.address();
        for (int row = 0; row < h; row++) {
            long s = src + row * srcStride, d = dst + (long) row * rowBytes;
            if (srcComps == dstComps) {
                MemoryUtil.memCopy(s, d, rowBytes);
            } else {
                for (int px = 0; px < w; px++) {
                    for (int c = 0; c < dstComps; c++) {
                        byte v = c < srcComps ? MemoryUtil.memGetByte(s + (long) px * srcComps + c) : (byte) (c == 3 ? 0xFF : 0);
                        MemoryUtil.memPutByte(d + (long) px * dstComps + c, v);
                    }
                }
            }
        }
        int slice = (texture.usage() & GpuTexture.USAGE_CUBEMAP_COMPATIBLE) != 0 ? layer % 6 : 0;
        Mtl.copyBufferToTexture(staging.buffer(), staging.offset(), rowBytes, handle(texture), slice, mip, x, y, w, h);
    }

    @Override
    public void copyTextureToBuffer(GpuTexture texture, GpuBuffer buffer, long offset, Runnable callback, int mip) {
        copyTextureToBuffer(texture, buffer, offset, callback, mip, 0, 0, texture.getWidth(mip), texture.getHeight(mip));
    }

    @Override
    public void copyTextureToBuffer(GpuTexture texture, GpuBuffer buffer, long offset, Runnable callback, int mip, int x, int y, int w, int h) {
        assertNoRenderPass();
        if (mip < 0 || mip >= texture.getMipLevels()) throw new IllegalArgumentException("Invalid mipLevel " + mip + ", must be >= 0 and < " + texture.getMipLevels());
        if ((long) w * h * texture.getFormat().blockSize() + offset > buffer.size()) {
            throw new IllegalArgumentException("Buffer of size " + buffer.size() + " is not large enough to hold " + w + "x" + h + " pixels (" + texture.getFormat().blockSize() + " bytes each) starting from offset " + offset);
        }
        if (texture.isClosed()) throw new IllegalStateException("Source texture is closed");
        if (buffer.isClosed()) throw new IllegalStateException("Destination buffer is closed");
        ((MetalBuffer) buffer).markGpuWrite();
        if (mip < ((MetalTexture) texture).metalMips) Mtl.copyTextureToBuffer(handle(texture), mip, x, y, w, h, ((MetalBuffer) buffer).handle, offset, w * texture.getFormat().blockSize());
        RenderSystem.queueFencedTask(callback);
    }

    @Override
    public void copyTextureToTexture(GpuTexture src, GpuTexture dst, int mip, int dstX, int dstY, int srcX, int srcY, int w, int h) {
        assertNoRenderPass();
        if (src.isClosed()) throw new IllegalStateException("Source texture is closed");
        if (dst.isClosed()) throw new IllegalStateException("Destination texture is closed");
        if (mip < Math.min(((MetalTexture) src).metalMips, ((MetalTexture) dst).metalMips)) Mtl.copyTextureToTexture(handle(src), handle(dst), mip, dstX, dstY, srcX, srcY, w, h);
    }

    @Override
    public GpuFence createFence() {
        assertNoRenderPass();
        return new MetalFence(Mtl.fence());
    }

    @Override public void writeTimestamp(GpuQueryPool pool, int index) { assertNoRenderPass(); ((MetalQueryPool) pool).write(index); }

    private static String describe(GpuTexture texture) {
        return texture.getLabel() + " " + texture.getWidth(0) + "x" + texture.getHeight(0);
    }

    private static long handle(GpuTexture texture) {
        return ((MetalTexture) texture).handle;
    }
}
