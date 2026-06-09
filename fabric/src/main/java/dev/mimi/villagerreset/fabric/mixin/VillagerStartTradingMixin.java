package dev.cevapi.villagerreset.fabric.mixin;

import dev.cevapi.villagerreset.fabric.FabricVillagerService;
import dev.cevapi.villagerreset.fabric.VillagerResetFabricMod;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class VillagerStartTradingMixin {
    @Inject(method = "startTrading", at = @At("HEAD"))
    private void villagerreset$ensureOffersBeforeTrading(Player player, CallbackInfo ci) {
        if (!player.level().isClientSide()) {
            FabricVillagerService.ensureSpecialOffers((Villager) (Object) this,
                    VillagerResetFabricMod.CONFIG);
        }
    }
}
