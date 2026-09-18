package dev.eviemod.features.skyblock.mixin;

import dev.eviemod.features.skyblock.SkyblockContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldBorder.class)
public class WorldBorderMixin {
   @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
   private void skyblockvisuals$removeClientBorderCollision(CallbackInfoReturnable<VoxelShape> cir) {
      if (SkyblockContext.shouldIgnoreWorldBorderRestrictions()) {
         cir.setReturnValue(Shapes.empty());
      }
   }

   @Inject(method = "isInsideCloseToBorder", at = @At("HEAD"), cancellable = true)
   private void skyblockvisuals$notInsideCloseClientBorder(Entity entity, AABB box, CallbackInfoReturnable<Boolean> cir) {
      if (SkyblockContext.shouldIgnoreWorldBorderRestrictions()) {
         cir.setReturnValue(false);
      }
   }

   @Inject(method = "isWithinBounds(Lnet/minecraft/core/BlockPos;)Z", at = @At("HEAD"), cancellable = true)
   private void skyblockvisuals$blockPosWithinClientBorder(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
      if (SkyblockContext.shouldIgnoreWorldBorderRestrictions()) {
         cir.setReturnValue(true);
      }
   }

   @Inject(method = "isWithinBounds(Lnet/minecraft/world/phys/Vec3;)Z", at = @At("HEAD"), cancellable = true)
   private void skyblockvisuals$vecWithinClientBorder(Vec3 pos, CallbackInfoReturnable<Boolean> cir) {
      if (SkyblockContext.shouldIgnoreWorldBorderRestrictions()) {
         cir.setReturnValue(true);
      }
   }

   @Inject(method = "isWithinBounds(Lnet/minecraft/world/level/ChunkPos;)Z", at = @At("HEAD"), cancellable = true)
   private void skyblockvisuals$chunkWithinClientBorder(ChunkPos pos, CallbackInfoReturnable<Boolean> cir) {
      if (SkyblockContext.shouldIgnoreWorldBorderRestrictions()) {
         cir.setReturnValue(true);
      }
   }

   @Inject(method = "isWithinBounds(Lnet/minecraft/world/phys/AABB;)Z", at = @At("HEAD"), cancellable = true)
   private void skyblockvisuals$boxWithinClientBorder(AABB box, CallbackInfoReturnable<Boolean> cir) {
      if (SkyblockContext.shouldIgnoreWorldBorderRestrictions()) {
         cir.setReturnValue(true);
      }
   }

   @Inject(method = "isWithinBounds(DD)Z", at = @At("HEAD"), cancellable = true)
   private void skyblockvisuals$coordinatesWithinClientBorder(double x, double z, CallbackInfoReturnable<Boolean> cir) {
      if (SkyblockContext.shouldIgnoreWorldBorderRestrictions()) {
         cir.setReturnValue(true);
      }
   }

   @Inject(method = "isWithinBounds(DDD)Z", at = @At("HEAD"), cancellable = true)
   private void skyblockvisuals$coordinatesWithMarginWithinClientBorder(double x, double z, double margin, CallbackInfoReturnable<Boolean> cir) {
      if (SkyblockContext.shouldIgnoreWorldBorderRestrictions()) {
         cir.setReturnValue(true);
      }
   }
}
