package mace.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class CameraHelper {
    private static final Minecraft mc = Minecraft.getInstance();

    /**
     * Smoothly rotates the player's camera towards a target entity.
     * Uses Java 25 optimized Math functions.
     */
    public static void smoothLookAt(Entity target, McModConfig cfg) {
        var player = mc.player;
        if (player == null || target == null) return;

        // 1. Calculate the vector to the target's center (chest height)
        Vec3 targetPos = target.getBoundingBox().getCenter();
        Vec3 playerPos = player.getEyePosition(1.0F);
        Vec3 diff = targetPos.subtract(playerPos);

        // 2. Calculate target Yaw and Pitch
        double distanceXZ = Math.hypot(diff.x, diff.z);
        float targetYaw = (float) (Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0F);
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(diff.y, distanceXZ)));

        // 3. Shortest Path Logic (Prevents 360-degree spins)
        float currentYaw = player.getYRot();
        float currentPitch = player.getXRot();

        float yawDiff = Mth.wrapDegrees(targetYaw - currentYaw);
        float pitchDiff = Mth.wrapDegrees(targetPitch - currentPitch);

        // 4. Human-like Deadzone 
        // If we are within 0.5 degrees, stop moving to prevent "vibrating"
        if (Math.abs(yawDiff) < 0.5f && Math.abs(pitchDiff) < 0.5f) {
            return;
        }

        // 5. Apply Smoothing Factor (Lerp)
        // lower values = smoother/slower, higher = snappier
        float speed = cfg.Smoothing;
        
        player.setYRot(currentYaw + (yawDiff * speed));
        player.setXRot(currentPitch + (pitchDiff * speed));
    }
}