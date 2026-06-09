package dev.cevapi.villagerreset.fabric;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
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
    public static final String PREFIX = "VillagerReset: ";
    public static final String CYCLE_NAME = "VillagerReset: Reset Trades";
    public static final String PROF_NAME = "VillagerReset: Change Profession";
    private static final String PROF_TAG_KEY = "villagerreset_profession";

    private static final Set<UUID> LOCKED = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<UUID, UUID> PENDING_CURES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Integer> CYCLE_USES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Integer> PROF_USES = new ConcurrentHashMap<>();

    private FabricVillagerService() {
    }

    public static boolean isSpecialOffer(MerchantOffer offer) {
        String name = offer.getResult().getHoverName().getString();
        return name.startsWith(PREFIX);
    }

    public static void ensureSpecialOffers(Villager villager, FabricResetConfig config) {
        UUID id = villager.getUUID();

        // Unemployed (NONE) villagers are not supported — Fabric cannot open a stable
        // trading GUI for them (the screen flashes open-then-close).  Only employed
        // villagers receive cycle + profession-swap offers.
        if (villager.getVillagerData().profession().is(VillagerProfession.NONE)) {
            removeSpecialOffers(villager);
            return;
        }

        if (LOCKED.contains(id)) {
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

        int cycleUses = CYCLE_USES.getOrDefault(id, 0);
        int profUses = PROF_USES.getOrDefault(id, 0);

        MerchantOffers rebuilt = new MerchantOffers();
        rebuilt.addAll(normal);
        rebuilt.add(createCycleOffer(config, cycleUses));
        rebuilt.add(createProfessionOffer(villager, config, profUses));
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
        UUID id = villager.getUUID();
        String name = offer.getResult().getHoverName().getString();

        if (CYCLE_NAME.equals(name)) {
            int uses = CYCLE_USES.getOrDefault(id, 0);
            if (uses >= config.cycleMaxUses) {
                return;
            }
            CYCLE_USES.put(id, uses + 1);
            clearSpecialItemFromPlayer(villager);
            rerollVillager(villager);
            ensureSpecialOffers(villager, config);
            refreshForTrader(villager);
            return;
        }
        if (PROF_NAME.equals(name)) {
            int uses = PROF_USES.getOrDefault(id, 0);
            if (uses >= config.cycleMaxUses) {
                return;
            }
            PROF_USES.put(id, uses + 1);
            clearSpecialItemFromPlayer(villager);
            if (ThreadLocalRandom.current().nextDouble() <= config.professionSwapSuccessChance) {
                String token = readProfessionToken(offer);
                if (token != null) {
                    Identifier professionId = Identifier.tryParse(token.toLowerCase(Locale.ROOT));
                    if (professionId != null) {
                        VillagerProfession profession = BuiltInRegistries.VILLAGER_PROFESSION.getValue(professionId);
                        if (profession != null) {
                            villager.setVillagerData(villager.getVillagerData()
                                    .withProfession(BuiltInRegistries.VILLAGER_PROFESSION.wrapAsHolder(profession))
                                    .withLevel(1));
                            villager.setVillagerXp(0);
                        }
                    }
                }
            }
            ensureSpecialOffers(villager, config);
            refreshForTrader(villager);
            return;
        }

        LOCKED.add(id);
        CYCLE_USES.remove(id);
        PROF_USES.remove(id);
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

    private static MerchantOffer createCycleOffer(FabricResetConfig config, int uses) {
        ItemStack result = new ItemStack(Items.BARRIER);
        result.set(DataComponents.CUSTOM_NAME, Component.literal(CYCLE_NAME));
        return new MerchantOffer(
                new ItemCost(Items.EMERALD, config.cycleCostEmeralds),
                Optional.empty(),
                result,
                Math.min(uses, config.cycleMaxUses),
                config.cycleMaxUses,
                0,
                0.05F
        );
    }

    private static MerchantOffer createProfessionOffer(Villager villager, FabricResetConfig config, int uses) {
        VillagerProfession profession = pickRandomProfession(villager.getVillagerData().profession().value());
        String token = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession).toString();

        ItemStack result = new ItemStack(Items.PLAYER_HEAD);
        // Friendly display name — does NOT leak the profession token
        result.set(DataComponents.CUSTOM_NAME, Component.literal(PROF_NAME));
        // Store the profession token in hidden CUSTOM_DATA for look-up on trade
        CompoundTag tag = new CompoundTag();
        tag.putString(PROF_TAG_KEY, token);
        result.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return new MerchantOffer(
                new ItemCost(Items.EMERALD, config.professionSwapCostEmeralds),
                Optional.empty(),
                result,
                Math.min(uses, config.cycleMaxUses),
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

    private static void clearSpecialItemFromPlayer(Villager villager) {
        if (!(villager.getTradingPlayer() instanceof ServerPlayer player)) {
            return;
        }
        // 1) Cursor (carried item) — most likely location right after trade
        if (player.containerMenu != null) {
            ItemStack carried = player.containerMenu.getCarried();
            if (!carried.isEmpty()) {
                String carriedName = carried.getHoverName().getString();
                if (carriedName.startsWith(PREFIX)) {
                    player.containerMenu.setCarried(ItemStack.EMPTY);
                    return;
                }
            }
        }
        // 2) Scan entire player inventory via Container interface (works across all mappings)
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                String stackName = stack.getHoverName().getString();
                if (stackName.startsWith(PREFIX)) {
                    player.getInventory().setItem(i, ItemStack.EMPTY);
                    return;
                }
            }
        }
    }

    private static String readProfessionToken(MerchantOffer offer) {
        CustomData customData = offer.getResult().get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return null;
        }
        return customData.copyTag().getString(PROF_TAG_KEY).orElse(null);
    }

    public static void resetUses(Villager villager) {
        UUID id = villager.getUUID();
        CYCLE_USES.remove(id);
        PROF_USES.remove(id);
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
