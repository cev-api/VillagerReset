package dev.cevapi.villagerreset.fabric.mixin;

import dev.cevapi.villagerreset.fabric.FabricVillagerService;
import dev.cevapi.villagerreset.fabric.VillagerResetFabricMod;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractVillager.class)
public abstract class VillagerMixin {
    @Inject(method = "notifyTrade", at = @At("TAIL"))
    private void villagerreset$onTrade(MerchantOffer offer, CallbackInfo ci) {
        if ((Object) this instanceof Villager villager) {
            FabricVillagerService.onTradeCompleted(villager, offer, VillagerResetFabricMod.CONFIG);
        }
    }
}
