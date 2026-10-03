// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.smoke;

import com.mojang.blaze3d.opengl.GlStateManager;
import dev.eviemod.metal.MetalBootstrap;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.TreeMap;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;

/** Runs through the real transformed Minecraft classes, including on CI's OpenGL fallback. */
final class LegacyGuiStateProbe {
    private LegacyGuiStateProbe() {}

    static void verify() {
        if (MetalBootstrap.isActive()) {
            check(GLFW.glfwGetCurrentContext() == 0, "Metal unexpectedly has an OpenGL context");
            Map<String, Object> before = snapshot();
            for (int i = 0; i < 1000; i++) stateCycle();
            check(GlStateManager._getError() == 0, "Unexpected legacy GL error");
            verifyUnsupportedOperations();
            check(before.equals(snapshot()), "Metal mutated legacy GL caches or resource counters");
            System.out.println("EVIEMETAL_LEGACY_GUI_STATE_OK: 1000 cycles, no GL context, caches unchanged");
        } else {
            check(GLFW.glfwGetCurrentContext() != 0, "Fallback is missing its OpenGL context");
            stateCycle();
            GlStateManager._enableBlend();
            check(GL11.glIsEnabled(GL11.GL_BLEND), "Blend enable did not reach OpenGL");
            GlStateManager._disableBlend();
            check(!GL11.glIsEnabled(GL11.GL_BLEND), "Blend disable did not reach OpenGL");
            GlStateManager._disableCull();
            check(!GL11.glIsEnabled(GL11.GL_CULL_FACE), "Cull disable did not reach OpenGL");
            GlStateManager._enableCull();
            check(GL11.glIsEnabled(GL11.GL_CULL_FACE), "Cull enable did not reach OpenGL");
            check(GL11.glGetInteger(GL11.GL_DEPTH_FUNC) == GL11.GL_LEQUAL, "Depth function did not reach OpenGL");
            check(GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK), "Depth mask did not reach OpenGL");
            check(GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB) == GL11.GL_ONE, "Blend function did not reach OpenGL");
            int texture = GlStateManager._genTexture();
            check(texture != 0, "Texture allocation was intercepted on OpenGL");
            GlStateManager._bindTexture(texture);
            check(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) == texture, "Texture bind did not reach OpenGL");
            GlStateManager._deleteTexture(texture);
            check(GlStateManager._getError() == 0, "OpenGL state fixture produced an error");
            System.out.println("EVIEMETAL_LEGACY_GUI_OPENGL_OK");
        }
    }

    private static void stateCycle() {
        GlStateManager._enableBlend();
        GlStateManager._disableCull();
        GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager._enableDepthTest();
        GlStateManager._depthFunc(GL11.GL_ALWAYS);
        GlStateManager._depthMask(false);
        GlStateManager._colorMask(0);
        GlStateManager._enableScissorTest();
        GlStateManager._scissorBox(1, 2, 3, 4);
        GlStateManager._viewport(0, 0, 16, 16);
        GlStateManager._polygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
        GlStateManager._enablePolygonOffset();
        GlStateManager._polygonOffset(1, 1);
        GlStateManager._activeTexture(GL13.GL_TEXTURE1);
        GlStateManager._bindTexture(0);
        GlStateManager._glUseProgram(0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._disablePolygonOffset();
        GlStateManager._polygonOffset(0, 0);
        GlStateManager._polygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
        GlStateManager._disableScissorTest();
        GlStateManager._colorMask(15);
        GlStateManager._depthMask(true);
        GlStateManager._depthFunc(GL11.GL_LEQUAL);
        GlStateManager._disableDepthTest();
        // Match the exact Talium root's finally block.
        GlStateManager._disableBlend();
        GlStateManager._enableCull();
        GlStateManager.clearGlErrors();
    }

    private static void verifyUnsupportedOperations() {
        var supported = java.util.Set.of("_enableBlend", "_disableBlend", "_blendFuncSeparate", "glBlendFuncSeparate",
                "_enableDepthTest", "_disableDepthTest", "_depthFunc", "_depthMask", "_enableCull", "_disableCull",
                "_colorMask", "_enableScissorTest", "_disableScissorTest", "_scissorBox", "_viewport", "_polygonMode",
                "_enablePolygonOffset", "_disablePolygonOffset", "_polygonOffset", "_activeTexture", "clearGlErrors", "_getError");
        for (var method : GlStateManager.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers()) || !Modifier.isStatic(method.getModifiers()) || supported.contains(method.getName())) continue;
            Object[] arguments = new Object[method.getParameterCount()];
            var types = method.getParameterTypes();
            for (int i = 0; i < types.length; i++) {
                arguments[i] = types[i] == int.class ? Integer.valueOf(1)
                        : types[i] == long.class ? Long.valueOf(0) : types[i] == boolean.class ? Boolean.FALSE
                        : types[i] == float.class ? Float.valueOf(0) : null;
            }
            try {
                method.invoke(null, arguments);
                throw new AssertionError("Unsupported GL call was allowed: " + method);
            } catch (InvocationTargetException e) {
                check(e.getCause() instanceof UnsupportedOperationException, "Wrong failure for " + method + ": " + e.getCause());
                check(e.getCause().getMessage().contains(method.getName()), "Missing operation diagnostic for " + method);
            } catch (IllegalAccessException e) { throw new AssertionError(e); }
        }
    }

    private static Map<String, Object> snapshot() {
        var values = new TreeMap<String, Object>();
        try {
            for (var field : GlStateManager.class.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                flatten(field.getName(), field.get(null), values);
            }
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        return values;
    }

    private static void flatten(String name, Object value, Map<String, Object> values) throws IllegalAccessException {
        if (value != null && value.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(value); i++) flatten(name + "[" + i + "]", Array.get(value, i), values);
        } else if (value != null && value.getClass().getName().startsWith(GlStateManager.class.getName() + "$")) {
            for (var field : value.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                flatten(name + "." + field.getName(), field.get(value), values);
            }
        } else values.put(name, value);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
