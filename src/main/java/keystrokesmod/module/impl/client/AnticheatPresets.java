package keystrokesmod.module.impl.client;

import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.utility.profile.AnticheatConfigHelper;

public class AnticheatPresets extends Module {
    public AnticheatPresets() {
        super("Anticheat Presets", category.configs);
        this.registerSetting(new DescriptionSetting("One-click configs for common anticheats."));
        this.registerSetting(new ButtonSetting("Apply Watchdog", () ->
                AnticheatConfigHelper.apply(AnticheatConfigHelper.Preset.WATCHDOG)));
        this.registerSetting(new ButtonSetting("Apply Polar", () ->
                AnticheatConfigHelper.apply(AnticheatConfigHelper.Preset.POLAR)));
        this.ignoreOnSave = true;
        this.canBeEnabled = false;
    }
}
