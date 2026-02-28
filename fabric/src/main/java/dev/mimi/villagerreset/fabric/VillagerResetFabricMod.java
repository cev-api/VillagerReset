package dev.cevapi.villagerreset.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Items;

public final class VillagerResetFabricMod implements ModInitializer {
    public static FabricResetConfig CONFIG;

    @Override
    public void onInitialize() {
        CONFIG = FabricConfigManager.load();

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide()) {
                return InteractionResult.PASS;
            }
            if (entity instanceof Villager villager) {
                FabricVillagerService.ensureSpecialOffers(villager, CONFIG);
            }
            if (entity instanceof ZombieVillager zombieVillager && player.getItemInHand(hand).is(Items.GOLDEN_APPLE)) {
                FabricVillagerService.markCureAttempt(zombieVillager.getUUID(), player.getUUID());
            }
            return InteractionResult.PASS;
        });
    }
}
