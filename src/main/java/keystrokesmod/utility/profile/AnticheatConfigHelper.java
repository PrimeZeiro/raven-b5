package keystrokesmod.utility.profile;

import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.setting.Setting;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.Utils;

/**
 * Applies premade module presets tuned for common anticheats.
 */
public final class AnticheatConfigHelper {
    public enum Preset {
        POLAR,
        WATCHDOG
    }

    private AnticheatConfigHelper() {
    }

    public static void apply(Preset preset) {
        if (!Utils.nullCheck()) {
            return;
        }

        disableBlatantModules();

        switch (preset) {
            case WATCHDOG:
                applyWatchdog();
                break;
            case POLAR:
                applyPolar();
                break;
        }

        Utils.sendMessage("&7Applied &d" + preset.name().toLowerCase() + " &7preset.");
    }

    private static void disableBlatantModules() {
        setEnabled("Kill Aura", false);
        setEnabled("TPAura", false);
        setEnabled("Fly", false);
        setEnabled("Timer", false);
        setEnabled("Reach", false);
        setEnabled("BlockESP", false);
        setEnabled("PlayerESP", false);
        setEnabled("Bed Aura", false);
        setEnabled("Chams", false);
        setEnabled("Lag Range", false);
        setEnabled("Long Jump", false);
        setEnabled("VClip", false);
        setEnabled("Teleport", false);
        setEnabled("FakeLag", false);
        setEnabled("Blink", false);
    }

    private static void applyWatchdog() {
        setEnabled("Silent Aura", true);
        setSlider("Silent Aura", "Target CPS", 9.0);
        setSlider("Silent Aura", "Speed", 11.0);
        setSlider("Silent Aura", "Range (attack)", 3.0);
        setSlider("Silent Aura", "Range (aim)", 4.2);
        setButton("Silent Aura", "Require mouse down", true);
        setButton("Silent Aura", "Ignore behind walls", true);

        setEnabled("AutoClicker", true);
        setEnabled("Aim Assist", false);
        setEnabled("Velocity", true);
        setSlider("Velocity", "Horizontal", 100.0);
        setSlider("Velocity", "Vertical", 100.0);
        setEnabled("WTap", true);
        setEnabled("Keep Sprint", true);
        setEnabled("Sprint", true);
        setEnabled("NoSlow", true);
        setEnabled("SafeWalk", true);
        setEnabled("Bridge Assist", true);
        setEnabled("Clutch", true);
        setEnabled("HUD", true);
        setEnabled("Target HUD", true);
        setSlider("Target HUD", "Mode", 0);
        setEnabled("HitSelect", true);
        setEnabled("KnockbackDelay", false);
        setEnabled("Reduce", false);
        setEnabled("Block In", false);
    }

    private static void applyPolar() {
        setEnabled("Silent Aura", true);
        setSlider("Silent Aura", "Target CPS", 7.0);
        setSlider("Silent Aura", "Speed", 8.0);
        setSlider("Silent Aura", "Range (attack)", 3.0);
        setSlider("Silent Aura", "Range (aim)", 3.8);
        setSlider("Silent Aura", "Randomization", 35.0);
        setButton("Silent Aura", "Require mouse down", true);
        setButton("Silent Aura", "Ignore behind walls", true);
        setButton("Silent Aura", "Keep move direction", true);

        setEnabled("AutoClicker", true);
        setEnabled("Aim Assist", false);
        setEnabled("Velocity", true);
        setSlider("Velocity", "Horizontal", 72.0);
        setSlider("Velocity", "Vertical", 78.0);
        setEnabled("WTap", true);
        setEnabled("Keep Sprint", true);
        setEnabled("Sprint", true);
        setEnabled("NoSlow", true);
        setEnabled("SafeWalk", true);
        setEnabled("Bridge Assist", true);
        setEnabled("Clutch", true);
        setEnabled("HUD", true);
        setEnabled("Target HUD", true);
        setSlider("Target HUD", "Mode", 0);
        setEnabled("HitSelect", true);
        setEnabled("KnockbackDelay", true);
        setSlider("Knockback Delay", "Maximum delay", 180.0);
        setEnabled("Reduce", true);
        setEnabled("Jump Reset", true);
        setEnabled("Block In", false);
    }

    private static void setEnabled(String moduleName, boolean enabled) {
        Module module = ModuleManager.getModule(moduleName);
        if (module == null) {
            return;
        }
        if (enabled) {
            module.enable();
        } else {
            module.disable();
        }
    }

    private static void setSlider(String moduleName, String settingName, double value) {
        Module module = ModuleManager.getModule(moduleName);
        if (module == null) {
            return;
        }
        for (Setting setting : module.getSettings()) {
            if (setting instanceof SliderSetting && settingName.equals(((SliderSetting) setting).getName())) {
                ((SliderSetting) setting).setValueWithEvent(value);
                return;
            }
        }
    }

    private static void setButton(String moduleName, String settingName, boolean enabled) {
        Module module = ModuleManager.getModule(moduleName);
        if (module == null) {
            return;
        }
        for (Setting setting : module.getSettings()) {
            if (setting instanceof ButtonSetting && settingName.equals(setting.getName())) {
                ButtonSetting button = (ButtonSetting) setting;
                if (button.isToggled() != enabled) {
                    button.toggle();
                }
                return;
            }
        }
    }
}
