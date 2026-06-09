package dev.cevapi.villagerreset.fabric.mixin;

import dev.cevapi.villagerreset.fabric.FabricVillagerService;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class VillagerRestockMixin {
    @Inject(method = "restock", at = @At("TAIL"))
    private void villagerreset$onRestock(CallbackInfo ci) {
        FabricVillagerService.resetUses((Villager) (Object) this);
    }
}
