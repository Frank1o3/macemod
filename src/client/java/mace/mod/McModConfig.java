package mace.mod;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "mace-mod")
public class McModConfig implements ConfigData {

    @ConfigEntry.Gui.Tooltip
    public boolean AutoAim = true;

    @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
    @ConfigEntry.Gui.Tooltip
    public float Smoothing = 0.2f; // 0.2 is a good "human" default

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
    public float FOV = 30.f;

    @ConfigEntry.BoundedDiscrete(min = 0, max = 10)
    @ConfigEntry.Gui.Tooltip
    public float FallDistance = 1.0f;

    @ConfigEntry.BoundedDiscrete(min = 0, max = 1)
    @ConfigEntry.Gui.Tooltip
    public float Cooldown = 1.0f;
}