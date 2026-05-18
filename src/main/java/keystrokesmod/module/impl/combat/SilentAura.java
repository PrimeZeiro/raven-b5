package keystrokesmod.module.impl.combat;

import keystrokesmod.event.ClientRotationEvent;
import keystrokesmod.event.PrePlayerInteractEvent;
import keystrokesmod.helper.RotationHelper;
import keystrokesmod.mixin.impl.accessor.IAccessorMinecraft;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.utility.ReflectionUtils;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Silent aim assist: acquires a target, rotates smoothly, and only attacks when actually aimed on them.
 */
public class SilentAura extends Module {
    private static final String[] SORT_MODES = {"Health", "Angle", "Hurt time", "Distance"};

    private final SliderSetting targetCps;
    private final SliderSetting attackRange;
    private final SliderSetting aimRange;
    private final SliderSetting speed;
    private final SliderSetting fov;
    private final SliderSetting sortMode;
    private final SliderSetting multipointHorizontal;
    private final SliderSetting multipointVertical;
    private final SliderSetting randomization;
    private final SliderSetting aimTolerance;
    private final ButtonSetting aimInvis;
    private final ButtonSetting ignoreTeammates;
    private final ButtonSetting ignoreBehindWalls;
    private final ButtonSetting ignoreBehindEntities;
    private final ButtonSetting requireMouseDown;
    private final ButtonSetting weaponOnly;
    private final ButtonSetting disableInInventory;
    private final ButtonSetting disableWhileMining;
    private final ButtonSetting keepMoveDirection;

    private long nextClickTime;
    private Random rand;

    private double attackRangeVal;
    private double aimRangeVal;
    private int speedVal;
    private int fovVal;
    private double multipointH;
    private double multipointV;
    private float randomizationPercent;
    private float aimToleranceDeg;
    private boolean useBackupRotations;
    private boolean allowThroughBlocks;
    private boolean allowThroughEntities;
    private boolean ignoreTeammatesVal;
    private boolean aimInvisVal;

    private static EntityPlayer lastCrosshairTarget;
    private EntityPlayer activeTarget;
    private float[] lastAimRotations;

    public SilentAura() {
        super("Silent Aura", category.combat);
        this.registerSetting(targetCps = new SliderSetting("Target CPS", 8.0, 1.0, 20.0, 0.5));
        this.registerSetting(attackRange = new SliderSetting("Range (attack)", 3.0, 3.0, 6.0, 0.05));
        this.registerSetting(aimRange = new SliderSetting("Range (aim)", 4.5, 3.0, 6.0, 0.05));
        this.registerSetting(speed = new SliderSetting("Speed", 10, 1, 30, 1));
        this.registerSetting(fov = new SliderSetting("FOV", "°", 90.0, 15.0, 360.0, 1.0));
        this.registerSetting(sortMode = new SliderSetting("Sort", 1, SORT_MODES));
        this.registerSetting(multipointHorizontal = new SliderSetting("Multipoint horizontal", "%", 0, 0, 100, 1));
        this.registerSetting(multipointVertical = new SliderSetting("Multipoint vertical", "%", 0, 0, 100, 1));
        this.registerSetting(randomization = new SliderSetting("Randomization", "%", 50, 0, 100, 1));
        this.registerSetting(aimTolerance = new SliderSetting("Aim tolerance", "°", 4.0, 1.0, 12.0, 0.5));
        this.registerSetting(aimInvis = new ButtonSetting("Target invis", false));
        this.registerSetting(ignoreTeammates = new ButtonSetting("Ignore teammates", true));
        this.registerSetting(ignoreBehindWalls = new ButtonSetting("Ignore behind walls", false));
        this.registerSetting(ignoreBehindEntities = new ButtonSetting("Ignore behind entities", false));
        this.registerSetting(requireMouseDown = new ButtonSetting("Require mouse down", true));
        this.registerSetting(weaponOnly = new ButtonSetting("Weapon only", false));
        this.registerSetting(disableInInventory = new ButtonSetting("Disable in inventory", true));
        this.registerSetting(disableWhileMining = new ButtonSetting("Disable while mining", false));
        this.registerSetting(keepMoveDirection = new ButtonSetting("Keep move direction", true));
    }

    public static EntityPlayer getCrosshairTarget() {
        return lastCrosshairTarget;
    }

    @Override
    public String getInfo() {
        return "Silent";
    }

    @Override
    public void guiUpdate() {
        refreshCachedSettings();
    }

    @Override
    public void onEnable() {
        rand = new Random();
        nextClickTime = 0L;
        refreshCachedSettings();
        clearTarget();
    }

    @Override
    public void onDisable() {
        nextClickTime = 0L;
        clearTarget();
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onClientRotation(ClientRotationEvent e) {
        clearTarget();

        if (!canRun()) {
            return;
        }
        if (ModuleManager.bedAura != null && ModuleManager.bedAura.shouldOverrideMouseOver()) {
            return;
        }
        if (ModuleManager.killAura != null
                && ModuleManager.killAura.isEnabled()
                && KillAura.target != null) {
            return;
        }

        EntityPlayer target = selectTarget();
        if (target == null) {
            return;
        }

        double distance = RotationUtils.distanceFromEyeToClosestOnAABB(target);
        if (distance > aimRangeVal) {
            return;
        }

        float[] rot = RotationHelper.get().getRotationsToTarget(
                target,
                e,
                speedVal,
                multipointH,
                multipointV,
                randomizationPercent,
                useBackupRotations,
                aimRangeVal,
                allowThroughBlocks,
                allowThroughEntities
        );
        if (rot == null) {
            return;
        }

        activeTarget = target;
        lastCrosshairTarget = target;
        lastAimRotations = rot;

        RotationHelper.get().forceMovementFix = true;
        RotationHelper.get().setServerRelativeMovementInputs(!keepMoveDirection.isToggled());
        e.yaw = rot[0];
        e.pitch = rot[1];
    }

    @SubscribeEvent
    public void onPrePlayerInteract(PrePlayerInteractEvent e) {
        if (!canRun()) {
            return;
        }
        if (ModuleManager.killAura != null
                && ModuleManager.killAura.isEnabled()
                && KillAura.target != null) {
            return;
        }
        if (activeTarget == null || activeTarget.isDead) {
            return;
        }

        double distance = RotationUtils.distanceFromEyeToClosestOnAABB(activeTarget);
        if (distance > attackRangeVal) {
            return;
        }

        if (!isAimedOnTarget(activeTarget)) {
            return;
        }

        long now = System.currentTimeMillis();
        if (nextClickTime == 0L) {
            nextClickTime = now;
        }
        if (nextClickTime > now) {
            return;
        }

        nextClickTime = now + nextDelay();
        ((IAccessorMinecraft) mc).setRightClickDelayTimer(0);
        int key = mc.gameSettings.keyBindAttack.getKeyCode();
        KeyBinding.onTick(key);
        ReflectionUtils.setButton(0, true);
    }

    private boolean isAimedOnTarget(EntityPlayer target) {
        float[] checkRot = lastAimRotations;
        if (checkRot == null) {
            checkRot = new float[] { RotationUtils.serverRotations[0], RotationUtils.serverRotations[1] };
        }

        if (RotationUtils.isPossibleToHit(target, attackRangeVal, checkRot)) {
            return true;
        }

        if (mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY
                && mc.objectMouseOver.entityHit == target) {
            return true;
        }

        float yawDelta = Math.abs(MathHelper.wrapAngleTo180_float(
                checkRot[0] - RotationUtils.serverRotations[0]));
        float pitchDelta = Math.abs(checkRot[1] - RotationUtils.serverRotations[1]);
        if (yawDelta > aimToleranceDeg || pitchDelta > aimToleranceDeg) {
            return false;
        }
        return Math.abs(Utils.aimDifference(target, true)) <= aimToleranceDeg
                && Math.abs(Utils.pitchDifference(target, true)) <= aimToleranceDeg;
    }

    private EntityPlayer selectTarget() {
        float viewYaw = mc.thePlayer.rotationYaw;
        Float serverYaw = RotationHelper.get().getServerYaw();
        if (serverYaw != null) {
            viewYaw = serverYaw;
        }

        List<EntityPlayer> candidates = new ArrayList<>();
        double aimRangeSq = aimRangeVal * aimRangeVal;

        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == mc.thePlayer || player.isDead || player.deathTime != 0) {
                continue;
            }
            if (Utils.isFriended(player) || AntiBot.isBot(player)) {
                continue;
            }
            if (ignoreTeammatesVal && Utils.isTeammate(player)) {
                continue;
            }
            if (!aimInvisVal && player.isInvisible()) {
                continue;
            }
            if (RotationUtils.distanceSqFromEyeToClosestOnAABB(player) > aimRangeSq) {
                continue;
            }
            if (fovVal != 360) {
                float angleToEntity = RotationUtils.angle(player.posX, player.posZ);
                if (!Utils.inFov(viewYaw, (float) fovVal, angleToEntity)) {
                    continue;
                }
            }
            candidates.add(player);
        }

        if (candidates.isEmpty()) {
            return null;
        }

        Comparator<EntityPlayer> primary = getSortComparator();
        candidates.sort(primary.thenComparingDouble(p -> mc.thePlayer.getDistanceSqToEntity(p)));

        if (useBackupRotations) {
            for (EntityPlayer candidate : candidates) {
                if (RotationUtils.hasValidAimPoint(candidate, multipointH, multipointV, aimRangeVal,
                        allowThroughBlocks, allowThroughEntities)) {
                    return candidate;
                }
            }
            return null;
        }

        return candidates.get(0);
    }

    private Comparator<EntityPlayer> getSortComparator() {
        switch ((int) sortMode.getInput()) {
            case 0:
                return Comparator.comparingDouble(p -> p.getHealth() + p.getAbsorptionAmount());
            case 2:
                return Comparator.comparingInt(p -> p.hurtTime);
            case 3:
                return Comparator.comparingDouble(p -> mc.thePlayer.getDistanceSqToEntity(p));
            case 1:
            default:
                return Comparator.comparingDouble(p -> {
                    double yawDelta = Math.abs(Utils.aimDifference(p, false));
                    double pitchDelta = Math.abs(Utils.pitchDifference(p, false));
                    return yawDelta + pitchDelta;
                });
        }
    }

    private void refreshCachedSettings() {
        attackRangeVal = attackRange.getInput();
        aimRangeVal = aimRange.getInput();
        speedVal = (int) speed.getInput();
        fovVal = (int) fov.getInput();
        multipointH = multipointHorizontal.getInput();
        multipointV = multipointVertical.getInput();
        randomizationPercent = (float) randomization.getInput();
        aimToleranceDeg = (float) aimTolerance.getInput();
        useBackupRotations = ignoreBehindWalls.isToggled() || ignoreBehindEntities.isToggled();
        allowThroughBlocks = !ignoreBehindWalls.isToggled();
        allowThroughEntities = !ignoreBehindEntities.isToggled();
        ignoreTeammatesVal = ignoreTeammates.isToggled();
        aimInvisVal = aimInvis.isToggled();
    }

    private void clearTarget() {
        activeTarget = null;
        lastCrosshairTarget = null;
        lastAimRotations = null;
    }

    private boolean canRun() {
        if (!Utils.nullCheck() || mc.thePlayer.isDead) {
            return false;
        }
        if (requireMouseDown.isToggled() && !Mouse.isButtonDown(0)) {
            return false;
        }
        if (weaponOnly.isToggled() && !Utils.holdingWeapon()) {
            return false;
        }
        if (disableWhileMining.isToggled() && Utils.isMining()) {
            return false;
        }
        if (disableInInventory.isToggled() && mc.currentScreen != null) {
            return false;
        }
        return true;
    }

    private long nextDelay() {
        int cps = Math.max(1, (int) targetCps.getInput());
        int baseDelay = 1000 / cps;
        int finalDelay = baseDelay + (rand.nextInt(21) - 10);
        return Math.max(55, Math.min(200, finalDelay));
    }
}
