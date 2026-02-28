package dev.cevapi.villagerreset.paper;

import dev.cevapi.villagerreset.common.ResetDefaults;
import org.bukkit.configuration.file.FileConfiguration;

public final class PaperResetConfig {
    public final boolean enabled;
    public final boolean debug;
    public final int cycleCostEmeralds;
    public final int professionSwapCostEmeralds;
    public final boolean unemployedInitialProfessionEnabled;
    public final int unemployedInitialProfessionCostEmeralds;
    public final int cycleMaxUses;
    public final double professionSwapChance;
    public final boolean cureDiscountEnabled;
    public final int cureDiscountRadius;
    public final int cureDiscountBonusLevel;

    private PaperResetConfig(
            boolean enabled,
            boolean debug,
            int cycleCostEmeralds,
            int professionSwapCostEmeralds,
            boolean unemployedInitialProfessionEnabled,
            int unemployedInitialProfessionCostEmeralds,
            int cycleMaxUses,
            double professionSwapChance,
            boolean cureDiscountEnabled,
            int cureDiscountRadius,
            int cureDiscountBonusLevel
    ) {
        this.enabled = enabled;
        this.debug = debug;
        this.cycleCostEmeralds = cycleCostEmeralds;
        this.professionSwapCostEmeralds = professionSwapCostEmeralds;
        this.unemployedInitialProfessionEnabled = unemployedInitialProfessionEnabled;
        this.unemployedInitialProfessionCostEmeralds = unemployedInitialProfessionCostEmeralds;
        this.cycleMaxUses = cycleMaxUses;
        this.professionSwapChance = professionSwapChance;
        this.cureDiscountEnabled = cureDiscountEnabled;
        this.cureDiscountRadius = cureDiscountRadius;
        this.cureDiscountBonusLevel = cureDiscountBonusLevel;
    }

    public static PaperResetConfig from(FileConfiguration cfg) {
        return new PaperResetConfig(
                cfg.getBoolean("enabled", true),
                cfg.getBoolean("debug", false),
                Math.max(1, cfg.getInt("cycle-cost-emeralds", ResetDefaults.CYCLE_COST_EMERALDS)),
                Math.max(1, cfg.getInt("profession-swap-cost-emeralds", ResetDefaults.PROFESSION_SWAP_COST_EMERALDS)),
                cfg.getBoolean("unemployed-initial-profession.enabled", true),
                Math.max(1, cfg.getInt("unemployed-initial-profession.cost-emeralds", 10)),
                Math.max(1, cfg.getInt("cycle-max-uses", ResetDefaults.CYCLE_MAX_USES)),
                Math.max(0D, Math.min(1D, cfg.getDouble("profession-swap-success-chance", ResetDefaults.PROFESSION_SWAP_CHANCE))),
                cfg.getBoolean("cure-discount.enabled", ResetDefaults.GLOBAL_CURE_DISCOUNT_ENABLED),
                Math.max(1, cfg.getInt("cure-discount.radius-blocks", ResetDefaults.GLOBAL_CURE_DISCOUNT_RADIUS)),
                Math.max(1, cfg.getInt("cure-discount.bonus-level", 20))
        );
    }
}
