package mace.mod;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "mace-mod")
public class McModConfig implements ConfigData {

    // === Camera Settings ===
    
    @ConfigEntry.Gui.Tooltip
    public boolean AutoAim = true;

    @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
    @ConfigEntry.Gui.Tooltip
    public float Smoothing = 0.2f; // 0.2 is a good "human" default (20%)

    // === Combat Settings ===
    
    @ConfigEntry.Gui.Tooltip
    public double SurvivalReach = 3.0;

    @ConfigEntry.Gui.Tooltip
    public double CreativeReach = 5.0;

    @ConfigEntry.BoundedDiscrete(min = -1, max = 8)
    @ConfigEntry.Gui.Tooltip
    public int axeSlot = 0;

    @ConfigEntry.BoundedDiscrete(min = -1, max = 8)
    @ConfigEntry.Gui.Tooltip
    public int maceSlot = 1;

    @ConfigEntry.BoundedDiscrete(min = 15, max = 90)
    @ConfigEntry.Gui.Tooltip
    public float FOV = 30.0f;

    @ConfigEntry.BoundedDiscrete(min = 0, max = 1)
    @ConfigEntry.Gui.Tooltip
    public float Cooldown = 1.0f;

    // === Smart Swap Settings ===
    
    @ConfigEntry.BoundedDiscrete(min = 0, max = 20)
    @ConfigEntry.Gui.Tooltip
    public float fallTriggerDistance = 5.0f; // The "5-Block Rule"

    // === Human Delay Settings (Multiplayer Safety) ===
    
    @ConfigEntry.BoundedDiscrete(min = 50, max = 500)
    @ConfigEntry.Gui.Tooltip
    public int minDelayMs = 100; // Minimum delay between actions in milliseconds

    @ConfigEntry.BoundedDiscrete(min = 100, max = 1000)
    @ConfigEntry.Gui.Tooltip
    public int maxDelayMs = 300; // Maximum delay between actions in milliseconds
}