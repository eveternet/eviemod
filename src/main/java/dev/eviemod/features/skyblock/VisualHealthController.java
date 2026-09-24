package dev.eviemod.features.skyblock;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class VisualHealthController {
   private static VisualHealthController.MaxHealthSnapshot serverMaxHealthSnapshot;
   private static float serverMaxHealth = 20.0F;
   private static float serverHealth = 20.0F;
   private static boolean visualMaxHealthApplied;

   private VisualHealthController() {
   }

   public static void tick(Minecraft client) {
      LocalPlayer player = client.player;
      if (player == null) {
         reset();
      } else {
         reconcile(player);
      }
   }

   public static float toVisualHealth(float health) {
      serverHealth = health;
      return SkyblockContext.shouldNormalizeHealth() && !(serverMaxHealth <= 20.0F) ? scaledHealth(health) : health;
   }

   public static boolean isNormalizingHealth() {
      return SkyblockContext.shouldNormalizeHealth() && serverMaxHealth > 20.0F;
   }

   public static float getHudHealth(float observedHealth) {
      return !isNormalizingHealth() ? observedHealth : scaledHealth(serverHealth);
   }

   public static double getHudMaxHealth(double observedMaxHealth) {
      return !isNormalizingHealth() ? observedMaxHealth : 20.0;
   }

   public static int clampHudHealthCache(int cachedHealth, float currentVisualHealth) {
      if (!isNormalizingHealth()) {
         return cachedHealth;
      }

      int current = (int)Math.ceil(currentVisualHealth);
      return cachedHealth <= current ? cachedHealth : current;
   }

   public static void onAttributesApplied(ClientboundUpdateAttributesPacket packet) {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player != null && packet.getEntityId() == player.getId() && updatesMaxHealth(packet)) {
         AttributeInstance instance = player.getAttribute(Attributes.MAX_HEALTH);
         if (instance != null) {
            serverMaxHealthSnapshot = VisualHealthController.MaxHealthSnapshot.capture(instance);
            serverMaxHealth = Math.max(20.0F, (float)instance.getValue());
            SkyblockContext.refreshNow(client);
            reconcile(player);
         }
      }
   }

   private static void reconcile(LocalPlayer player) {
      AttributeInstance instance = player.getAttribute(Attributes.MAX_HEALTH);
      if (instance != null) {
         boolean shouldApply = SkyblockContext.shouldNormalizeHealth() && serverMaxHealth > 20.0F;
         if (shouldApply) {
            if (!visualMaxHealthApplied && serverMaxHealthSnapshot == null) {
               serverMaxHealthSnapshot = VisualHealthController.MaxHealthSnapshot.capture(instance);
               serverMaxHealth = Math.max(20.0F, (float)instance.getValue());
            }

            if (!visualMaxHealthApplied || instance.getBaseValue() != 20.0 || !instance.getModifiers().isEmpty()) {
               applyVisualMaxHealth(instance);
            }

            float visualHealth = scaledHealth(serverHealth);
            if (player.getHealth() != visualHealth) {
               player.setHealth(visualHealth);
            }

            visualMaxHealthApplied = true;
         } else {
            if (visualMaxHealthApplied) {
               if (serverMaxHealthSnapshot != null) {
                  serverMaxHealthSnapshot.restore(instance);
               }

               if (player.getHealth() != serverHealth) {
                  player.setHealth(serverHealth);
               }

               visualMaxHealthApplied = false;
            }
         }
      }
   }

   private static void applyVisualMaxHealth(AttributeInstance instance) {
      instance.setBaseValue(20.0);
      instance.removeModifiers();
   }

   private static boolean updatesMaxHealth(ClientboundUpdateAttributesPacket packet) {
      return packet.getValues().stream().anyMatch(snapshot -> snapshot.attribute().is(Attributes.MAX_HEALTH));
   }

   private static float scaledHealth(float health) {
      return clamp(health / serverMaxHealth * 20.0F, 0.0F, 20.0F);
   }

   private static float clamp(float value, float min, float max) {
      return Math.max(min, Math.min(max, value));
   }

   private static void reset() {
      serverMaxHealthSnapshot = null;
      serverMaxHealth = 20.0F;
      serverHealth = 20.0F;
      visualMaxHealthApplied = false;
   }

   private record MaxHealthSnapshot(double baseValue, Set<AttributeModifier> modifiers) {
      static VisualHealthController.MaxHealthSnapshot capture(AttributeInstance instance) {
         return new VisualHealthController.MaxHealthSnapshot(instance.getBaseValue(), new HashSet<>(instance.getModifiers()));
      }

      void restore(AttributeInstance instance) {
         instance.setBaseValue(this.baseValue);
         instance.removeModifiers();

         for (AttributeModifier modifier : this.modifiers) {
            instance.addTransientModifier(modifier);
         }
      }
   }
}
