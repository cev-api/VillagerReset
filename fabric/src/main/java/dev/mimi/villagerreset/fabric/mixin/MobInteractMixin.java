package dev.cevapi.villagerreset.fabric.mixin;

import dev.cevapi.villagerreset.fabric.FabricVillagerService;
import dev.cevapi.villagerreset.fabric.VillagerResetFabricMod;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Villager.class)
public abstract class MobInteractMixin {
    @Inject(method = "mobInteract(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;", at = @At("HEAD"))
    private void villagerreset$ensureOffersBeforeMobInteract(Player player, InteractionHand hand,
                                                              CallbackInfoReturnable<InteractionResult> cir) {
        // Inject special offers BEFORE vanilla Villager.mobInteract runs its checks.
        // We do NOT cancel vanilla — we let it proceed naturally once offers exist.
        // Unemployed (NONE) villagers are explicitly skipped: vanilla has no trading
        // GUI for them and opening one causes the screen to flash open-then-close.
        // Only employed villagers get the cycle + profession-swap offers.
        if (!player.level().isClientSide()) {
            FabricVillagerService.ensureSpecialOffers((Villager) (Object) this, VillagerResetFabricMod.CONFIG);
        }
    }
}
