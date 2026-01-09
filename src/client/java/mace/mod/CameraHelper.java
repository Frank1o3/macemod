package mace.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

public class CameraHelper {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final Random random = new Random();
    
    // Humanization parameters
    private static final float DEADZONE_DEGREES = 0.1f;
    private static final float MAX_NOISE_OFFSET = 0.15f; // Maximum pixel offset in degrees

    /**
     * Smoothly rotates the player's camera towards a target entity with human-like behavior.
     * Uses Java 25 optimized Math functions and implements realistic aiming patterns.
     * 
     * @param target The entity to look at
     * @param cfg Configuration containing smoothing and other parameters
     */
    public static void smoothLookAt(@NotNull Entity target, @NotNull McModConfig cfg) {
        var player = mc.player;
        if (player == null) return;

        // 1. Calculate the vector to the target's center (chest height)
        Vec3 targetPos = target.getBoundingBox().getCenter();
        Vec3 playerPos = player.getEyePosition(1.0F);
        
        // Add humanization noise - slight random offset to prevent pixel-perfect tracking
        double noiseX = (random.nextDouble() - 0.5) * MAX_NOISE_OFFSET;
        double noiseY = (random.nextDouble() - 0.5) * MAX_NOISE_OFFSET;
        double noiseZ = (random.nextDouble() - 0.5) * MAX_NOISE_OFFSET;
        
        Vec3 noisyTarget = targetPos.add(noiseX, noiseY, noiseZ);
        Vec3 diff = noisyTarget.subtract(playerPos);

        // 2. Calculate target Yaw and Pitch using Java 25 optimized functions
        // Math.hypot is optimized in Java 25 for better performance
        double distanceXZ = Math.hypot(diff.x, diff.z);
        float targetYaw = (float) (Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0);
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(diff.y, distanceXZ)));

        // 3. Shortest Path Logic (Prevents 360-degree spins)
        float currentYaw = player.getYRot();
        float currentPitch = player.getXRot();

        // Mth.wrapDegrees ensures we take the shortest angular path
        float yawDiff = Mth.wrapDegrees(targetYaw - currentYaw);
        float pitchDiff = Mth.wrapDegrees(targetPitch - currentPitch);

        // 4. Human-like Deadzone 
        // If we are within the deadzone, stop moving to prevent micro-jitter
        if (Math.abs(yawDiff) < DEADZONE_DEGREES && Math.abs(pitchDiff) < DEADZONE_DEGREES) {
            return;
        }

        // 5. Apply Smoothing Factor (Lerp with ease-out behavior)
        // Lower values = smoother/slower, higher = snappier
        // The lerp naturally slows down as we approach the target
        float speed = cfg.Smoothing;
        
        // Apply additional distance-based smoothing for more human-like deceleration
        float distance = (float) Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
        float adaptiveSpeed = speed;
        
        // Slow down more as we get closer to target (ease-out effect)
        if (distance < 5.0f) {
            adaptiveSpeed *= (distance / 5.0f) * 0.7f + 0.3f; // Scale down speed near target
        }
        
        // Calculate new rotation with lerp
        float newYaw = currentYaw + (yawDiff * adaptiveSpeed);
        float newPitch = currentPitch + (pitchDiff * adaptiveSpeed);
        
        // Clamp pitch to valid range [-90, 90]
        newPitch = Mth.clamp(newPitch, -90.0f, 90.0f);
        
        player.setYRot(newYaw);
        player.setXRot(newPitch);
    }
}