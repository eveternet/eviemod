// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.mixin;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.eviemod.metal.MetalBootstrap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 26.2 boundary for legacy state surrounding queued Blaze3D GUI submissions.
 * Metal pipelines/passes already own this state; replaying global GL toggles would apply
 * the wrong state to deferred draws. Cancel before touching even the GL state cache.
 * Resource/draw operations cannot be emulated by a state shim and fail in Java instead.
 * See docs/metal-gui-compatibility.md for the audited path and direct-LWJGL limits.
 */
@Mixin(GlStateManager.class)
abstract class LegacyGlStateMixin {
    @Inject(method = {
            "_disableScissorTest()V",
            "_enableScissorTest()V",
            "_scissorBox(IIII)V",
            "_disableDepthTest()V",
            "_enableDepthTest()V",
            "_depthFunc(I)V",
            "_depthMask(Z)V",
            "_disableBlend(I)V",
            "_enableBlend(I)V",
            "_blendFuncSeparate(IIII)V",
            "_blendEquationSeparate(II)V",
            "glBlendFuncSeparate(IIII)V",
            "glBlendEquationSeparate(II)V",
            "_enableCull()V",
            "_disableCull()V",
            "_polygonMode(II)V",
            "_enablePolygonOffset()V",
            "_disablePolygonOffset()V",
            "_polygonOffset(FF)V",
            "_activeTexture(I)V",
            "_viewport(IIII)V",
            "_colorMask(I)V",
            "_colorMask(II)V",
            "clearGlErrors()V"
    }, at = @At("HEAD"), cancellable = true)
    private static void eviemod$pipelineOwnsState(CallbackInfo ci) {
        if (!MetalBootstrap.isActive()) return;
        RenderSystem.assertOnRenderThread();
        ci.cancel();
    }

    @Inject(method = "_getError()I", at = @At("HEAD"), cancellable = true)
    private static void eviemod$noGlError(CallbackInfoReturnable<Integer> ci) {
        if (!MetalBootstrap.isActive()) return;
        RenderSystem.assertOnRenderThread();
        // No GL commands were issued. Metal errors retain their existing frame-boundary checks.
        ci.setReturnValue(0);
    }

    @Inject(method = {"_bindTexture(I)V", "_glUseProgram(I)V"}, at = @At("HEAD"), cancellable = true)
    private static void eviemod$unbindOnly(int handle, CallbackInfo ci) {
        if (!MetalBootstrap.isActive()) return;
        RenderSystem.assertOnRenderThread();
        if (handle != 0) throw eviemod$unsupported(ci);
        // A legacy unbind has no resource to resolve; Blaze3D binds resources by name per pass.
        ci.cancel();
    }

    @Inject(method = {
            "glAttachShader(II)V",
            "glDeleteShader(I)V",
            "glShaderSource(ILjava/lang/String;)V",
            "glCompileShader(I)V",
            "glDeleteProgram(I)V",
            "glLinkProgram(I)V",
            "_glUniform1i(II)V",
            "_glBindAttribLocation(IILjava/lang/CharSequence;)V",
            "_glBindBuffer(II)V",
            "_glBindVertexArray(I)V",
            "_glBufferData(ILjava/nio/ByteBuffer;I)V",
            "_glBufferSubData(IJLjava/nio/ByteBuffer;)V",
            "_glBufferData(IJI)V",
            "_glUnmapBuffer(I)V",
            "_glDeleteBuffers(I)V",
            "_glBindFramebuffer(II)V",
            "_glBlitFrameBuffer(IIIIIIIIII)V",
            "_glDeleteFramebuffers(I)V",
            "_glFramebufferTexture2D(IIIII)V",
            "_enableColorLogicOp()V",
            "_disableColorLogicOp()V",
            "_logicOp(I)V",
            "_texParameter(III)V",
            "_deleteTexture(I)V",
            "_texImage2D(IIIIIIIILjava/nio/ByteBuffer;)V",
            "_texSubImage2D(IIIIIIIIJ)V",
            "_texSubImage2D(IIIIIIIILjava/nio/ByteBuffer;)V",
            "_clear(I)V",
            "_clearBuffer(ILorg/joml/Vector4fc;)V",
            "_clearBuffer(D)V",
            "_vertexAttribPointer(IIIZIJ)V",
            "_vertexAttribIPointer(IIIIJ)V",
            "_enableVertexAttribArray(I)V",
            "_drawElements(IIIJ)V",
            "_drawArrays(III)V",
            "_pixelStore(II)V",
            "_readPixels(IIIIIIJ)V",
            "_glDeleteSync(J)V"
    }, at = @At("HEAD"), cancellable = true)
    private static void eviemod$unsupportedCommand(CallbackInfo ci) { if (MetalBootstrap.isActive()) throw eviemod$unsupported(ci); }
    @Inject(method = {
            "glGetProgrami(II)I",
            "glCreateShader(I)I",
            "glGetShaderi(II)I",
            "glCreateProgram()I",
            "_glGetUniformLocation(ILjava/lang/CharSequence;)I",
            "_glGenBuffers()I",
            "_glGenVertexArrays()I",
            "_glMapBufferRange(IJJI)Ljava/nio/ByteBuffer;",
            "getFrameBuffer(I)I",
            "glGenFramebuffers()I",
            "glGetShaderInfoLog(II)Ljava/lang/String;",
            "glGetProgramInfoLog(II)Ljava/lang/String;",
            "_getTexLevelParameter(III)I",
            "_genTexture()I",
            "_getString(I)Ljava/lang/String;",
            "_getInteger(I)I",
            "_glFenceSync(II)J",
            "_glClientWaitSync(JIJ)I"
    }, at = @At("HEAD"), cancellable = true)
    private static void eviemod$unsupportedQuery(CallbackInfoReturnable<?> ci) { if (MetalBootstrap.isActive()) throw eviemod$unsupported(ci); }

    private static UnsupportedOperationException eviemod$unsupported(CallbackInfo ci) {
        return new UnsupportedOperationException("Legacy OpenGL GlStateManager." + ci.getId()
                + " is unavailable while Eviemetal is active; use Blaze3D pipelines, passes and GPU resources");
    }
}
