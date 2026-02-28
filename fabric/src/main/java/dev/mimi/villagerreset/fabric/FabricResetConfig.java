package dev.cevapi.villagerreset.fabric;

import dev.cevapi.villagerreset.common.ResetDefaults;

public final class FabricResetConfig {
    public int cycleCostEmeralds = ResetDefaults.CYCLE_COST_EMERALDS;
    public int professionSwapCostEmeralds = ResetDefaults.PROFESSION_SWAP_COST_EMERALDS;
    public int cycleMaxUses = ResetDefaults.CYCLE_MAX_USES;
    public double professionSwapSuccessChance = ResetDefaults.PROFESSION_SWAP_CHANCE;
    public boolean cureDiscountEnabled = ResetDefaults.GLOBAL_CURE_DISCOUNT_ENABLED;
    public int cureDiscountRadiusBlocks = ResetDefaults.GLOBAL_CURE_DISCOUNT_RADIUS;
    public int cureDiscountBonusLevel = 20;
}