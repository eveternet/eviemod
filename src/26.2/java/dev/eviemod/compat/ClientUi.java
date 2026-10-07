package dev.eviemod.compat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Compile-time adapter for the Minecraft 26.2 GUI/HUD ownership boundary. */
public final class ClientUi {
    private ClientUi() {}
    public static Screen screen(Minecraft client) { return client.gui.screen(); }
    public static void setScreen(Minecraft client, Screen screen) { client.gui.setScreen(screen); }
    public static Overlay overlay(Minecraft client) { return client.gui.overlay(); }
    public static ToastManager toasts(Minecraft client) { return client.gui.toastManager(); }
    public static RenderTarget mainRenderTarget(Minecraft client) { return client.gameRenderer.mainRenderTarget(); }
    public static ChatComponent getChat(Minecraft client) { return client.gui.hud.getChat(); }
    public static void setTimes(Minecraft client, int fadeIn, int stay, int fadeOut) { client.gui.hud.setTimes(fadeIn, stay, fadeOut); }
    public static void setSubtitle(Minecraft client, Component text) { client.gui.hud.setSubtitle(text); }
    public static void setTitle(Minecraft client, Component text) { client.gui.hud.setTitle(text); }
}
