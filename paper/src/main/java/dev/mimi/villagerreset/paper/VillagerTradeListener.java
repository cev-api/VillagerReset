package dev.cevapi.villagerreset.paper;

import dev.cevapi.villagerreset.common.ResetKeys;
import com.destroystokyo.paper.entity.villager.Reputation;
import com.destroystokyo.paper.entity.villager.ReputationType;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.ZombieVillager;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.entity.VillagerReplenishTradeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class VillagerTradeListener implements Listener {
    private final VillagerResetPaperPlugin plugin;
    private final NamespacedKey lockedKey;
    private final NamespacedKey recycleOfferKey;
    private final NamespacedKey professionOfferKey;
    private final NamespacedKey targetProfessionKey;
    private final NamespacedKey recycleUsesKey;
    private final NamespacedKey professionUsesKey;
    private final NamespacedKey offeredProfessionKey;
    private final NamespacedKey forcedProfessionKey;
    private final Map<UUID, UUID> unemployedSessions;

    public VillagerTradeListener(VillagerResetPaperPlugin plugin) {
        this.plugin = plugin;
        this.lockedKey = new NamespacedKey(plugin, ResetKeys.LOCKED);
        this.recycleOfferKey = new NamespacedKey(plugin, ResetKeys.RECYCLE_OFFER);
        this.professionOfferKey = new NamespacedKey(plugin, ResetKeys.PROFESSION_OFFER);
        this.targetProfessionKey = new NamespacedKey(plugin, "target_profession");
        this.recycleUsesKey = new NamespacedKey(plugin, "recycle_uses");
        this.professionUsesKey = new NamespacedKey(plugin, "profession_uses");
        this.offeredProfessionKey = new NamespacedKey(plugin, "offered_profession");
        this.forcedProfessionKey = new NamespacedKey(plugin, "forced_profession");
        this.unemployedSessions = new ConcurrentHashMap<>();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVillagerInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!(event.getRightClicked() instanceof Villager villager)) {
            return;
        }
        if (!plugin.resetConfig().enabled) {
            debug("Interact ignored (plugin disabled) villager=" + villager.getUniqueId());
            removeRecyclerOffers(villager);
            return;
        }
        event.setCancelled(true);
        debug("Interact start player=" + player.getName()
                + " villager=" + villager.getUniqueId()
                + " profession=" + villager.getProfession()
                + " locked=" + isLocked(villager));
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!villager.isValid() || !player.isValid()) {
                debug("Open skipped invalid entities playerValid=" + player.isValid() + " villagerValid=" + villager.isValid());
                return;
            }
            ensureRecyclerOffers(villager);
            debug("Opening merchant player=" + player.getName() + " villager=" + villager.getUniqueId()
                    + " recipes=" + villager.getRecipes().size());
            if (plugin.resetConfig().unemployedInitialProfessionEnabled && villager.getProfession() == Villager.Profession.NONE) {
                openUnemployedMerchant(player, villager);
                return;
            }
            player.openMerchant(villager, true);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMerchantPurchase(InventoryClickEvent event) {
        if (!plugin.resetConfig().enabled) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory() instanceof MerchantInventory merchantInventory)) {
            return;
        }
        boolean customMerchantSession = false;
        Villager villager;
        if (merchantInventory.getHolder() instanceof Villager holderVillager) {
            villager = holderVillager;
        } else {
            customMerchantSession = true;
            UUID villagerId = unemployedSessions.get(player.getUniqueId());
            if (villagerId == null) {
                debug("Purchase ignored: custom merchant without session player=" + player.getName());
                return;
            }
            Villager resolved = findVillager(villagerId);
            if (resolved == null || !resolved.isValid()) {
                debug("Purchase ignored: session villager missing player=" + player.getName() + " villager=" + villagerId);
                return;
            }
            villager = resolved;
        }
        if (event.getRawSlot() != 2) {
            return;
        }
        MerchantRecipe selectedRecipe = merchantInventory.getSelectedRecipe();
        if (selectedRecipe == null) {
            debug("Purchase skipped no selected recipe for player=" + player.getName());
            return;
        }

        if (hasMarker(selectedRecipe.getResult(), recycleOfferKey) || hasMarker(selectedRecipe.getResult(), professionOfferKey)) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!villager.isValid() || !player.isValid()) {
                    return;
                }
                debug("Special purchase click player=" + player.getName()
                        + " villager=" + villager.getUniqueId()
                        + " result=" + selectedRecipe.getResult().getType());
                handleSpecialPurchase(player, villager, merchantInventory, selectedRecipe);
            });
            return;
        }

        if (customMerchantSession) {
            event.setCancelled(true);
            debug("Blocked non-special purchase in custom unemployed merchant player=" + player.getName()
                    + " villager=" + villager.getUniqueId());
            return;
        }

        if (!hasRequiredIngredients(merchantInventory, selectedRecipe)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!villager.isValid() || !player.isValid()) {
                return;
            }
            setLocked(villager, true);
            villager.getPersistentDataContainer().remove(recycleUsesKey);
            villager.getPersistentDataContainer().remove(professionUsesKey);
            villager.getPersistentDataContainer().remove(offeredProfessionKey);
            removeRecyclerOffers(villager);
            debug("Normal trade lock applied villager=" + villager.getUniqueId() + " player=" + player.getName());
            refreshMerchantWindow(player, villager);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVillagerCured(EntityTransformEvent event) {
        if (event.getTransformReason() != EntityTransformEvent.TransformReason.CURED) {
            return;
        }
        if (!(event.getEntity() instanceof ZombieVillager zombieVillager)) {
            return;
        }
        if (!(event.getTransformedEntity() instanceof Villager)) {
            return;
        }

        PaperResetConfig cfg = plugin.resetConfig();
        if (!cfg.enabled || !cfg.cureDiscountEnabled) {
            return;
        }

        OfflinePlayer conversionPlayer = zombieVillager.getConversionPlayer();
        if (conversionPlayer == null || !conversionPlayer.isOnline()) {
            return;
        }
        Player curingPlayer = conversionPlayer.getPlayer();
        if (curingPlayer == null) {
            return;
        }

        double radius = cfg.cureDiscountRadius;
        List<Entity> nearby = curingPlayer.getNearbyEntities(radius, radius, radius);
        for (Entity entity : nearby) {
            if (!(entity instanceof Villager nearbyVillager)) {
                continue;
            }
            Reputation rep = nearbyVillager.getReputation(curingPlayer.getUniqueId());
            rep.setReputation(ReputationType.MAJOR_POSITIVE, rep.getReputation(ReputationType.MAJOR_POSITIVE) + cfg.cureDiscountBonusLevel);
            nearbyVillager.setReputation(curingPlayer.getUniqueId(), rep);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVillagerRestock(VillagerReplenishTradeEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }
        if (isLocked(villager)) {
            return;
        }
        setSpecialUses(villager, recycleUsesKey, 0);
        setSpecialUses(villager, professionUsesKey, 0);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVillagerCareerChange(VillagerCareerChangeEvent event) {
        Villager villager = event.getEntity();
        String forcedRaw = villager.getPersistentDataContainer().get(forcedProfessionKey, PersistentDataType.STRING);
        if (forcedRaw == null) {
            return;
        }
        if (event.getProfession() == Villager.Profession.NONE
                && event.getReason() == VillagerCareerChangeEvent.ChangeReason.LOSING_JOB) {
            event.setCancelled(true);
            debug("Blocked LOSING_JOB for forced profession villager=" + villager.getUniqueId()
                    + " forced=" + forcedRaw);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMerchantClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            unemployedSessions.remove(player.getUniqueId());
        }
    }

    private void ensureRecyclerOffers(Villager villager) {
        if (isLocked(villager)) {
            removeRecyclerOffers(villager);
            debug("ensureRecyclerOffers removed specials because villager is locked id=" + villager.getUniqueId());
            return;
        }

        List<MerchantRecipe> base = new ArrayList<>();
        for (MerchantRecipe recipe : villager.getRecipes()) {
            if (!isRecyclerRecipe(recipe)) {
                base.add(recipe);
            }
        }

        int recycleUses = getSpecialUses(villager, recycleUsesKey);
        int professionUses = getSpecialUses(villager, professionUsesKey);
        Villager.Profession offered = getOrCreateOfferedProfession(villager);

        if (villager.getProfession() != Villager.Profession.NONE) {
            base.add(createRecycleRecipe(recycleUses));
        }
        base.add(createProfessionSwapRecipe(villager, offered, professionUses));
        villager.setRecipes(base);
        debug("ensureRecyclerOffers applied villager=" + villager.getUniqueId()
                + " recycleUses=" + recycleUses
                + " professionUses=" + professionUses
                + " offered=" + offered
                + " totalRecipes=" + base.size()
                + " resetVisible=" + (villager.getProfession() != Villager.Profession.NONE));
    }

    private void removeRecyclerOffers(Villager villager) {
        List<MerchantRecipe> base = new ArrayList<>();
        for (MerchantRecipe recipe : villager.getRecipes()) {
            if (!isRecyclerRecipe(recipe)) {
                base.add(recipe);
            }
        }
        villager.setRecipes(base);
    }

    private MerchantRecipe createRecycleRecipe(int uses) {
        PaperResetConfig cfg = plugin.resetConfig();

        ItemStack result = new ItemStack(Material.BARRIER);
        ItemMeta meta = result.getItemMeta();
        meta.displayName(Component.text("Reset Trades"));
        meta.getPersistentDataContainer().set(recycleOfferKey, PersistentDataType.BYTE, (byte) 1);
        result.setItemMeta(meta);

        MerchantRecipe recipe = new MerchantRecipe(result, cfg.cycleMaxUses);
        recipe.addIngredient(new ItemStack(Material.EMERALD, cfg.cycleCostEmeralds));
        recipe.setUses(Math.min(uses, cfg.cycleMaxUses));
        return recipe;
    }

    private MerchantRecipe createProfessionSwapRecipe(Villager villager, Villager.Profession profession, int uses) {
        PaperResetConfig cfg = plugin.resetConfig();

        ItemStack result = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = result.getItemMeta();
        meta.displayName(Component.text(professionLabel(profession)));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(professionOfferKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(targetProfessionKey, PersistentDataType.STRING, profession.name());
        result.setItemMeta(meta);

        MerchantRecipe recipe = new MerchantRecipe(result, cfg.cycleMaxUses);
        recipe.addIngredient(new ItemStack(Material.EMERALD, professionSwapCostFor(villager)));
        recipe.setUses(Math.min(uses, cfg.cycleMaxUses));
        return recipe;
    }

    private boolean applyProfessionSwap(Villager villager, Villager.Profession target) {
        PaperResetConfig cfg = plugin.resetConfig();
        if (ThreadLocalRandom.current().nextDouble() > cfg.professionSwapChance) {
            debug("Profession swap chance failed villager=" + villager.getUniqueId()
                    + " target=" + target
                    + " chance=" + cfg.professionSwapChance);
            return false;
        }

        villager.setProfession(target);
        villager.setVillagerLevel(1);
        villager.setVillagerExperience(0);
        villager.getPersistentDataContainer().set(forcedProfessionKey, PersistentDataType.STRING, target.name());
        debug("Profession applied villager=" + villager.getUniqueId() + " profession=" + villager.getProfession());
        return true;
    }

    private void rerollVillager(Villager villager) {
        // Trade-only reroll: avoid profession flipping, which can destabilize villagers on some servers.
        villager.setVillagerLevel(1);
        villager.setVillagerExperience(0);
        villager.setRecipes(new ArrayList<>());
        villager.addTrades(2);
    }

    private void refreshMerchantWindow(Player player, Villager villager) {
        if (!player.isValid() || !villager.isValid()) {
            return;
        }
        player.openMerchant(villager, true);
    }

    private boolean isRecyclerRecipe(MerchantRecipe recipe) {
        return hasMarker(recipe.getResult(), recycleOfferKey) || hasMarker(recipe.getResult(), professionOfferKey);
    }

    private boolean hasMarker(ItemStack item, NamespacedKey key) {
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    private boolean isLocked(Villager villager) {
        Byte value = villager.getPersistentDataContainer().get(lockedKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    private void setLocked(Villager villager, boolean locked) {
        if (locked) {
            villager.getPersistentDataContainer().set(lockedKey, PersistentDataType.BYTE, (byte) 1);
        } else {
            villager.getPersistentDataContainer().remove(lockedKey);
        }
    }

    private Villager.Profession pickRandomProfession(Villager.Profession current) {
        List<Villager.Profession> professions = new ArrayList<>(Arrays.stream(Villager.Profession.values())
                .filter(p -> p != Villager.Profession.NONE && p != Villager.Profession.NITWIT)
                .toList());
        professions.remove(current);
        if (professions.isEmpty()) {
            return current;
        }
        return professions.get(ThreadLocalRandom.current().nextInt(professions.size()));
    }

    private String professionLabel(Villager.Profession profession) {
        String lower = profession.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        String[] parts = lower.split(" ");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    private void handleSpecialPurchase(
            Player player,
            Villager villager,
            MerchantInventory merchantInventory,
            MerchantRecipe selectedRecipe
    ) {
        if (!hasRequiredIngredients(merchantInventory, selectedRecipe)) {
            return;
        }

        boolean isRecycle = hasMarker(selectedRecipe.getResult(), recycleOfferKey);
        boolean isProfession = hasMarker(selectedRecipe.getResult(), professionOfferKey);
        if (!isRecycle && !isProfession) {
            return;
        }

        PaperResetConfig cfg = plugin.resetConfig();
        NamespacedKey usesKey = isRecycle ? recycleUsesKey : professionUsesKey;
        int uses = getSpecialUses(villager, usesKey);
        if (uses >= cfg.cycleMaxUses) {
            debug("Special purchase blocked sold-out villager=" + villager.getUniqueId()
                    + " type=" + (isRecycle ? "recycle" : "profession")
                    + " uses=" + uses
                    + " max=" + cfg.cycleMaxUses);
            ensureRecyclerOffers(villager);
            refreshMerchantWindow(player, villager);
            return;
        }

        if (!consumeForRecipe(merchantInventory, selectedRecipe)) {
            debug("Special purchase blocked missing payment villager=" + villager.getUniqueId()
                    + " player=" + player.getName());
            return;
        }

        setSpecialUses(villager, usesKey, uses + 1);
        debug("Special purchase success villager=" + villager.getUniqueId()
                + " type=" + (isRecycle ? "recycle" : "profession")
                + " usesNow=" + (uses + 1));

        if (isRecycle) {
            rerollVillager(villager);
            setOfferedProfession(villager, pickRandomProfession(villager.getProfession()));
        } else if (isProfession) {
            Villager.Profession target = getOrCreateOfferedProfession(villager);
            boolean applied = applyProfessionSwap(villager, target);
            if (!applied) {
                debug("Profession swap not applied villager=" + villager.getUniqueId() + " target=" + target);
            }
            setOfferedProfession(villager, pickRandomProfession(villager.getProfession()));
        }

        ensureRecyclerOffers(villager);
        if (plugin.resetConfig().unemployedInitialProfessionEnabled && villager.getProfession() == Villager.Profession.NONE) {
            openUnemployedMerchant(player, villager);
        } else {
            refreshMerchantWindow(player, villager);
        }
    }

    private boolean hasRequiredIngredients(MerchantInventory merchantInventory, MerchantRecipe recipe) {
        List<ItemStack> ingredients = recipe.getIngredients();
        if (ingredients.isEmpty()) {
            return true;
        }
        int emeraldRequired = 0;
        for (ItemStack ingredient : ingredients) {
            if (ingredient.getType() == Material.EMERALD) {
                emeraldRequired += ingredient.getAmount();
            }
        }
        if (emeraldRequired == 0) {
            return true;
        }
        int emeraldCount = countEmeralds(merchantInventory.getItem(0)) + countEmeralds(merchantInventory.getItem(1));
        return emeraldCount >= emeraldRequired;
    }

    private boolean consumeForRecipe(MerchantInventory merchantInventory, MerchantRecipe recipe) {
        int emeraldRequired = 0;
        for (ItemStack ingredient : recipe.getIngredients()) {
            if (ingredient.getType() == Material.EMERALD) {
                emeraldRequired += ingredient.getAmount();
            }
        }
        if (emeraldRequired <= 0) {
            return true;
        }

        int left = removeEmeraldsFromSlot(merchantInventory, 0, emeraldRequired);
        left = removeEmeraldsFromSlot(merchantInventory, 1, left);
        return left == 0;
    }

    private int countEmeralds(ItemStack stack) {
        if (stack == null || stack.getType() != Material.EMERALD) {
            return 0;
        }
        return stack.getAmount();
    }

    private int removeEmeraldsFromSlot(MerchantInventory inventory, int slot, int amount) {
        if (amount <= 0) {
            return 0;
        }
        ItemStack stack = inventory.getItem(slot);
        if (stack == null || stack.getType() != Material.EMERALD) {
            return amount;
        }
        int take = Math.min(stack.getAmount(), amount);
        int remain = stack.getAmount() - take;
        if (remain <= 0) {
            inventory.setItem(slot, null);
        } else {
            stack.setAmount(remain);
            inventory.setItem(slot, stack);
        }
        return amount - take;
    }

    private int getSpecialUses(Villager villager, NamespacedKey key) {
        Integer uses = villager.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
        return uses == null ? 0 : Math.max(0, uses);
    }

    private void setSpecialUses(Villager villager, NamespacedKey key, int uses) {
        villager.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, Math.max(0, uses));
    }

    private Villager.Profession getOrCreateOfferedProfession(Villager villager) {
        Villager.Profession stored = getStoredOfferedProfession(villager);
        if (stored != null && stored != Villager.Profession.NONE && stored != Villager.Profession.NITWIT) {
            return stored;
        }
        Villager.Profession generated = pickRandomProfession(villager.getProfession());
        setOfferedProfession(villager, generated);
        return generated;
    }

    private Villager.Profession getStoredOfferedProfession(Villager villager) {
        String raw = villager.getPersistentDataContainer().get(offeredProfessionKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return Villager.Profession.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void setOfferedProfession(Villager villager, Villager.Profession profession) {
        villager.getPersistentDataContainer().set(offeredProfessionKey, PersistentDataType.STRING, profession.name());
    }

    private int professionSwapCostFor(Villager villager) {
        PaperResetConfig cfg = plugin.resetConfig();
        if (cfg.unemployedInitialProfessionEnabled && villager.getProfession() == Villager.Profession.NONE) {
            return cfg.unemployedInitialProfessionCostEmeralds;
        }
        return cfg.professionSwapCostEmeralds;
    }

    private void openUnemployedMerchant(Player player, Villager villager) {
        Merchant merchant = Bukkit.createMerchant(Component.text("VillagerReset"));
        List<MerchantRecipe> specials = new ArrayList<>();
        for (MerchantRecipe recipe : villager.getRecipes()) {
            if (isRecyclerRecipe(recipe)) {
                specials.add(recipe);
            }
        }
        merchant.setRecipes(specials);
        unemployedSessions.put(player.getUniqueId(), villager.getUniqueId());
        boolean opened = player.openMerchant(merchant, true) != null;
        debug("Open unemployed custom merchant player=" + player.getName()
                + " villager=" + villager.getUniqueId()
                + " offers=" + specials.size()
                + " opened=" + opened);
    }

    private Villager findVillager(UUID uuid) {
        for (World world : Bukkit.getWorlds()) {
            Entity entity = world.getEntity(uuid);
            if (entity instanceof Villager villager) {
                return villager;
            }
        }
        return null;
    }

    private void debug(String message) {
        if (plugin.resetConfig().debug) {
            plugin.getLogger().info("[VR-DEBUG] " + message);
        }
    }
}
