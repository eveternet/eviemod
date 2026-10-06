package dev.eviemod.compat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Compile-time adapter for the Minecraft 26.1.2 GUI/HUD ownership boundary. */
public final class ClientUi {
    private ClientUi() {}
    public static Screen screen(Minecraft client) { return client.screen; }
    public static void setScreen(Minecraft client, Screen screen) { client.setScreen(screen); }
    public static Overlay overlay(Minecraft client) { return client.getOverlay(); }
    public static ToastManager toasts(Minecraft client) { return client.getToastManager(); }
    public static RenderTarget mainRenderTarget(Minecraft client) { return client.getMainRenderTarget(); }
    public static ChatComponent getChat(Minecraft client) { return client.gui.getChat(); }
    public static void setTimes(Minecraft client, int fadeIn, int stay, int fadeOut) { client.gui.setTimes(fadeIn, stay, fadeOut); }
    public static void setSubtitle(Minecraft client, Component text) { client.gui.setSubtitle(text); }
    public static void setTitle(Minecraft client, Component text) { client.gui.setTitle(text); }
}
