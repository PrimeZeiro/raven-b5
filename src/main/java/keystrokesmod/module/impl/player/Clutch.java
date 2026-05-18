package keystrokesmod.module.impl.player;

import keystrokesmod.event.ClientRotationEvent;
import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.helper.RotationHelper;
import keystrokesmod.mixin.impl.accessor.IAccessorMinecraft;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockWall;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Places a block under the player while falling. Only uses valid neighbor placement (never air-place).
 */
public class Clutch extends Module {
    private static final EnumFacing[] FACE_ORDER = {
            EnumFacing.UP, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST, EnumFacing.DOWN
    };

    private final SliderSetting minFallDistance;
    private final SliderSetting placeDelay;
    private final ButtonSetting switchToBlock;
    private final ButtonSetting silentAim;
    private final ButtonSetting swing;

    private long lastPlace = 0L;
    private int savedSlot = -1;
    private boolean placeQueued;
    private Placement pendingPlacement;

    public Clutch() {
        super("Clutch", category.player);
        this.registerSetting(minFallDistance = new SliderSetting("Min fall distance", " blocks", 1.5, 0.5, 10.0, 0.1));
        this.registerSetting(placeDelay = new SliderSetting("Place delay", " ms", 0, 0, 500, 25));
        this.registerSetting(switchToBlock = new ButtonSetting("Switch to block", true));
        this.registerSetting(silentAim = new ButtonSetting("Silent aim", true));
        this.registerSetting(swing = new ButtonSetting("Swing", true));
    }

    @Override
    public void onDisable() {
        lastPlace = 0L;
        placeQueued = false;
        pendingPlacement = null;
        restoreSlot();
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onClientRotation(ClientRotationEvent e) {
        placeQueued = false;

        if (ModuleManager.bedAura != null && ModuleManager.bedAura.shouldOverrideMouseOver()) {
            pendingPlacement = null;
            return;
        }
        if (!canRun()) {
            pendingPlacement = null;
            restoreSlot();
            return;
        }

        long now = System.currentTimeMillis();
        if (Utils.timeBetween(lastPlace, now) < (long) placeDelay.getInput()) {
            return;
        }

        int blockSlot = getBlockSlot();
        if (blockSlot == -1) {
            pendingPlacement = null;
            return;
        }

        ItemStack stack = mc.thePlayer.inventory.getStackInSlot(blockSlot);
        if (stack == null || !(stack.getItem() instanceof ItemBlock)) {
            pendingPlacement = null;
            return;
        }

        double reach = mc.playerController.getBlockReachDistance();
        Placement placement = findBestPlacement(stack, reach);
        if (placement == null) {
            pendingPlacement = null;
            return;
        }

        pendingPlacement = placement;
        equipBlockSlot(blockSlot);

        float baseYaw = e.yaw != null ? e.yaw : RotationUtils.serverRotations[0];
        float basePitch = e.pitch != null ? e.pitch : RotationUtils.serverRotations[1];
        float[] rots = RotationUtils.getRotationsToBlock(placement.support, placement.face, baseYaw, basePitch);
        if (rots == null) {
            pendingPlacement = null;
            return;
        }

        if (silentAim.isToggled()) {
            RotationHelper.get().forceMovementFix = true;
            e.setYaw(rots[0]);
            e.setPitch(rots[1]);
        } else {
            mc.thePlayer.rotationYaw = rots[0];
            mc.thePlayer.rotationPitch = rots[1];
        }

        RotationHelper rh = RotationHelper.get();
        rh.beginSwap(mc.thePlayer, rots[0], rots[1], true);
        MovingObjectPosition mop = RotationUtils.rayCastBlock(reach, rots[0], rots[1]);
        rh.endSwap(mc.thePlayer);

        if (mop != null
                && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && mop.getBlockPos().equals(placement.support)
                && mop.sideHit == placement.face) {
            placement.hitVec = mop.hitVec;
            placeQueued = true;
            return;
        }

        // Neighbor placement with a computed face hit (still requires a solid support block)
        placement.hitVec = faceHitVec(placement.support, placement.face);
        placeQueued = true;
    }

    @SubscribeEvent
    public void onPreUpdate(PreUpdateEvent e) {
        if (!placeQueued || pendingPlacement == null || !canRun()) {
            return;
        }

        placeQueued = false;
        Placement placement = pendingPlacement;

        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemBlock)) {
            return;
        }

        BlockPos resultPos = placement.support.offset(placement.face);
        if (!BlockUtils.replaceable(resultPos)) {
            return;
        }

        ((IAccessorMinecraft) mc).setRightClickDelayTimer(0);

        if (mc.playerController.onPlayerRightClick(
                mc.thePlayer, mc.theWorld, held,
                placement.support, placement.face, placement.hitVec)) {
            if (swing.isToggled()) {
                mc.thePlayer.swingItem();
            }
            lastPlace = System.currentTimeMillis();
        }
    }

    /**
     * Finds the best legal placement: air cell under the player with a solid neighbor to click (no air-place).
     */
    private Placement findBestPlacement(ItemStack stack, double reach) {
        List<BlockPos> targets = collectTargetCells();
        Placement best = null;
        int bestScore = Integer.MIN_VALUE;
        double bestDist = Double.MAX_VALUE;
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
        double reachSq = reach * reach;

        for (BlockPos target : targets) {
            if (!BlockUtils.replaceable(target)) {
                continue;
            }

            for (EnumFacing towardSupport : FACE_ORDER) {
                BlockPos support = target.offset(towardSupport);
                EnumFacing clickFace = towardSupport.getOpposite();

                if (!isValidSupport(support, stack, clickFace)) {
                    continue;
                }
                if (!support.offset(clickFace).equals(target)) {
                    continue;
                }

                double distSq = eye.squareDistanceTo(new Vec3(
                        support.getX() + 0.5,
                        support.getY() + 0.5,
                        support.getZ() + 0.5));
                if (distSq > reachSq) {
                    continue;
                }

                int score = scorePlacement(target, clickFace);
                if (score > bestScore || (score == bestScore && distSq < bestDist)) {
                    bestScore = score;
                    bestDist = distSq;
                    best = new Placement(support, clickFace, faceHitVec(support, clickFace));
                }
            }
        }

        return best;
    }

    private int scorePlacement(BlockPos target, EnumFacing clickFace) {
        int score = 0;
        if (clickFace == EnumFacing.UP) {
            score += 100;
        }
        AxisAlignedBB box = mc.thePlayer.getEntityBoundingBox();
        double cx = (box.minX + box.maxX) * 0.5;
        double cz = (box.minZ + box.maxZ) * 0.5;
        double dx = (target.getX() + 0.5) - cx;
        double dz = (target.getZ() + 0.5) - cz;
        score -= (int) (dx * dx + dz * dz);
        return score;
    }

    private boolean isValidSupport(BlockPos support, ItemStack stack, EnumFacing clickFace) {
        if (BlockUtils.replaceable(support)) {
            return false;
        }
        Block block = BlockUtils.getBlock(support);
        if (BlockUtils.isInteractable(block) || block instanceof BlockFence || block instanceof BlockWall) {
            return false;
        }
        return BlockUtils.canPlaceBlockOnSide(stack, support, clickFace);
    }

    private List<BlockPos> collectTargetCells() {
        Set<BlockPos> cells = new LinkedHashSet<>();
        AxisAlignedBB box = mc.thePlayer.getEntityBoundingBox();

        int minX = MathHelper.floor_double(box.minX);
        int maxX = MathHelper.floor_double(box.maxX);
        int minZ = MathHelper.floor_double(box.minZ);
        int maxZ = MathHelper.floor_double(box.maxZ);

        int feetY = MathHelper.floor_double(box.minY);
        int predictedFeetY = MathHelper.floor_double(box.minY + mc.thePlayer.motionY * 3.0D);

        addColumnCells(cells, minX, maxX, minZ, maxZ, feetY);
        addColumnCells(cells, minX, maxX, minZ, maxZ, feetY - 1);
        if (predictedFeetY != feetY) {
            addColumnCells(cells, minX, maxX, minZ, maxZ, predictedFeetY);
            addColumnCells(cells, minX, maxX, minZ, maxZ, predictedFeetY - 1);
        }

        return new ArrayList<>(cells);
    }

    private void addColumnCells(Set<BlockPos> cells, int minX, int maxX, int minZ, int maxZ, int y) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                cells.add(new BlockPos(x, y, z));
            }
        }
    }

    private Vec3 faceHitVec(BlockPos support, EnumFacing face) {
        double x = support.getX() + 0.5;
        double y = support.getY() + 0.5;
        double z = support.getZ() + 0.5;
        x += face.getFrontOffsetX() * 0.5;
        y += face.getFrontOffsetY() * 0.5;
        z += face.getFrontOffsetZ() * 0.5;
        return new Vec3(x, y, z);
    }

    private void equipBlockSlot(int blockSlot) {
        if (!switchToBlock.isToggled()) {
            return;
        }
        if (mc.thePlayer.inventory.currentItem != blockSlot) {
            if (savedSlot == -1) {
                savedSlot = mc.thePlayer.inventory.currentItem;
            }
            Utils.switchSlot(blockSlot, true);
        }
    }

    private int getBlockSlot() {
        for (int slot = 0; slot < InventoryPlayer.getHotbarSize(); ++slot) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (stack != null && stack.stackSize > 0 && stack.getItem() instanceof ItemBlock) {
                ItemBlock itemBlock = (ItemBlock) stack.getItem();
                if (itemBlock.getBlock().isFullCube()) {
                    return slot;
                }
            }
        }
        for (int slot = 0; slot < InventoryPlayer.getHotbarSize(); ++slot) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (stack != null && stack.stackSize > 0 && stack.getItem() instanceof ItemBlock) {
                return slot;
            }
        }
        return -1;
    }

    private boolean canRun() {
        return Utils.nullCheck()
                && mc.currentScreen == null
                && !mc.isGamePaused()
                && !mc.thePlayer.capabilities.isFlying
                && !mc.thePlayer.capabilities.isCreativeMode
                && isFalling();
    }

    private boolean isFalling() {
        return !mc.thePlayer.onGround
                && mc.thePlayer.motionY < 0
                && mc.thePlayer.fallDistance >= minFallDistance.getInput();
    }

    private void restoreSlot() {
        if (savedSlot != -1) {
            Utils.switchSlot(savedSlot, true);
            savedSlot = -1;
        }
    }

    private static class Placement {
        final BlockPos support;
        final EnumFacing face;
        Vec3 hitVec;

        Placement(BlockPos support, EnumFacing face, Vec3 hitVec) {
            this.support = support;
            this.face = face;
            this.hitVec = hitVec;
        }
    }
}
