package dev.cevapi.villagerreset.fabric;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class FabricVillagerService {
    public static final String CYCLE_NAME = "VillagerReset: Reset Trades";
    public static final String PROF_PREFIX = "VillagerReset: Profession ";

    private static final Set<UUID> LOCKED = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<UUID, UUID> PENDING_CURES = new ConcurrentHashMap<>();

    private FabricVillagerService() {
    }

    public static boolean isSpecialOffer(MerchantOffer offer) {
        String name = offer.getResult().getHoverName().getString();
        return CYCLE_NAME.equals(name) || name.startsWith(PROF_PREFIX);
    }

    public static void ensureSpecialOffers(Villager villager, FabricResetConfig config) {
        if (LOCKED.contains(villager.getUUID())) {
            removeSpecialOffers(villager);
            return;
        }

        MerchantOffers offers = villager.getOffers();
        List<MerchantOffer> normal = new ArrayList<>();
        for (MerchantOffer offer : offers) {
            if (!isSpecialOffer(offer)) {
                normal.add(offer);
            }
        }

        MerchantOffers rebuilt = new MerchantOffers();
        rebuilt.addAll(normal);
        if (!villager.getVillagerData().profession().is(VillagerProfession.NONE)) {
            rebuilt.add(createCycleOffer(config));
        }
        rebuilt.add(createProfessionOffer(villager, config));
        offers.clear();
        offers.addAll(rebuilt);
    }

    public static void removeSpecialOffers(Villager villager) {
        MerchantOffers offers = villager.getOffers();
        List<MerchantOffer> normal = new ArrayList<>();
        for (MerchantOffer offer : offers) {
            if (!isSpecialOffer(offer)) {
                normal.add(offer);
            }
        }
        offers.clear();
        offers.addAll(normal);
    }

    public static void onTradeCompleted(Villager villager, MerchantOffer offer, FabricResetConfig config) {
        String name = offer.getResult().getHoverName().getString();
        if (CYCLE_NAME.equals(name)) {
            rerollVillager(villager);
            ensureSpecialOffers(villager, config);
            refreshForTrader(villager);
            return;
        }
        if (name.startsWith(PROF_PREFIX)) {
            if (ThreadLocalRandom.current().nextDouble() <= config.professionSwapSuccessChance) {
                String token = name.substring(PROF_PREFIX.length()).trim();
                Identifier id = Identifier.tryParse(token.toLowerCase(Locale.ROOT));
                if (id != null) {
                    VillagerProfession profession = BuiltInRegistries.VILLAGER_PROFESSION.getValue(id);
                    if (profession != null) {
                        villager.setVillagerData(villager.getVillagerData()
                                .withProfession(BuiltInRegistries.VILLAGER_PROFESSION.wrapAsHolder(profession))
                                .withLevel(1));
                        villager.setVillagerXp(0);
                    }
                }
            }
            ensureSpecialOffers(villager, config);
            refreshForTrader(villager);
            return;
        }

        LOCKED.add(villager.getUUID());
        removeSpecialOffers(villager);
        refreshForTrader(villager);
    }

    public static void applyGlobalCureDiscount(ServerPlayer player, ServerLevel level, double x, double y, double z, FabricResetConfig config) {
        if (!config.cureDiscountEnabled) {
            return;
        }
        double radius = config.cureDiscountRadiusBlocks;
        var box = new AABB(x, y, z, x, y, z).inflate(radius, radius, radius);
        var villagers = level.getEntitiesOfClass(Villager.class, box);
        for (Villager nearby : villagers) {
            nearby.getGossips().add(player.getUUID(), net.minecraft.world.entity.ai.gossip.GossipType.MAJOR_POSITIVE, config.cureDiscountBonusLevel);
        }
    }

    public static void markCureAttempt(UUID zombieVillagerId, UUID playerId) {
        PENDING_CURES.put(zombieVillagerId, playerId);
    }

    public static ServerPlayer consumeCurer(UUID zombieVillagerId, ServerLevel level) {
        UUID playerId = PENDING_CURES.remove(zombieVillagerId);
        if (playerId == null) {
            return null;
        }
        if (level.getPlayerByUUID(playerId) instanceof ServerPlayer serverPlayer) {
            return serverPlayer;
        }
        return null;
    }

    private static MerchantOffer createCycleOffer(FabricResetConfig config) {
        ItemStack result = new ItemStack(Items.BARRIER);
        result.set(DataComponents.CUSTOM_NAME, Component.literal(CYCLE_NAME));
        return new MerchantOffer(
                new ItemCost(Items.EMERALD, config.cycleCostEmeralds),
                Optional.empty(),
                result,
                0,
                config.cycleMaxUses,
                0,
                0.05F
        );
    }

    private static MerchantOffer createProfessionOffer(Villager villager, FabricResetConfig config) {
        VillagerProfession profession = pickRandomProfession(villager.getVillagerData().profession().value());
        String token = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession).toString();

        ItemStack result = new ItemStack(Items.PLAYER_HEAD);
        result.set(DataComponents.CUSTOM_NAME, Component.literal(PROF_PREFIX + token));
        return new MerchantOffer(
                new ItemCost(Items.EMERALD, config.professionSwapCostEmeralds),
                Optional.empty(),
                result,
                0,
                config.cycleMaxUses,
                0,
                0.05F
        );
    }

    private static VillagerProfession pickRandomProfession(VillagerProfession current) {
        List<VillagerProfession> available = new ArrayList<>();
        for (VillagerProfession profession : BuiltInRegistries.VILLAGER_PROFESSION) {
            var holder = BuiltInRegistries.VILLAGER_PROFESSION.wrapAsHolder(profession);
            if (holder.is(VillagerProfession.NONE) || holder.is(VillagerProfession.NITWIT) || profession == current) {
                continue;
            }
            available.add(profession);
        }
        if (available.isEmpty()) {
            return current;
        }
        return available.get(ThreadLocalRandom.current().nextInt(available.size()));
    }

    private static void rerollVillager(Villager villager) {
        VillagerProfession current = villager.getVillagerData().profession().value();
        villager.setVillagerData(villager.getVillagerData()
                .withProfession(BuiltInRegistries.VILLAGER_PROFESSION.wrapAsHolder(
                        BuiltInRegistries.VILLAGER_PROFESSION.getValueOrThrow(VillagerProfession.NONE)))
                .withLevel(1));
        villager.setVillagerXp(0);
        villager.setVillagerData(villager.getVillagerData()
                .withProfession(BuiltInRegistries.VILLAGER_PROFESSION.wrapAsHolder(current))
                .withLevel(1));
        villager.setVillagerXp(0);
    }

    private static void refreshForTrader(Villager villager) {
        if (villager.getTradingPlayer() instanceof ServerPlayer player && player.containerMenu != null) {
            player.sendMerchantOffers(
                    player.containerMenu.containerId,
                    villager.getOffers(),
                    villager.getVillagerData().level(),
                    villager.getVillagerXp(),
                    villager.showProgressBar(),
                    villager.canRestock()
            );
        }
    }
}
