package dev.cevapi.villagerreset.fabric.mixin;

import dev.cevapi.villagerreset.fabric.FabricVillagerService;
import dev.cevapi.villagerreset.fabric.VillagerResetFabricMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ZombieVillager.class)
public abstract class ZombieVillagerMixin {
    @Inject(method = "finishConversion", at = @At("TAIL"))
    private void villagerreset$afterConversion(ServerLevel level, CallbackInfo ci) {
        ZombieVillager self = (ZombieVillager) (Object) this;
        ServerPlayer player = FabricVillagerService.consumeCurer(self.getUUID(), level);
        if (player == null) {
            return;
        }
        FabricVillagerService.applyGlobalCureDiscount(player, level, self.getX(), self.getY(), self.getZ(), VillagerResetFabricMod.CONFIG);
    }
}
