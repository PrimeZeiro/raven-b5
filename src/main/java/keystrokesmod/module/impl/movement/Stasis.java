package keystrokesmod.module.impl.movement;

import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.event.PrePlayerInputEvent;
import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.module.Module;
import keystrokesmod.utility.Utils;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class Stasis extends Module {
    private boolean allowNextC03;

    public Stasis() {
        super("Stasis", category.movement);
    }

    @Override
    public void onEnable() {
        allowNextC03 = false;
    }

    @SubscribeEvent
    public void onPreUpdate(PreUpdateEvent e) {
        if (!Utils.nullCheck()) {
            return;
        }
        if (mc.thePlayer.hurtTime != 0) {
            return;
        }

        mc.thePlayer.motionX = 0.0D;
        mc.thePlayer.motionY = 0.0D;
        mc.thePlayer.motionZ = 0.0D;
    }

    @SubscribeEvent
    public void onPrePlayerInput(PrePlayerInputEvent e) {
        e.setForward(0.0F);
        e.setStrafe(0.0F);
    }

    @SubscribeEvent
    public void onSendPacket(SendPacketEvent e) {
        if (!(e.getPacket() instanceof C03PacketPlayer)) {
            return;
        }
        if (allowNextC03) {
            allowNextC03 = false;
            return;
        }
        if (!Utils.nullCheck() || mc.thePlayer.hurtTime != 0) {
            return;
        }

        if (!(e.getPacket() instanceof C03PacketPlayer.C05PacketPlayerLook)) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onReceivePacket(ReceivePacketEvent e) {
        if (e.getPacket() instanceof S08PacketPlayerPosLook) {
            allowNextC03 = true;
        }
    }
}
