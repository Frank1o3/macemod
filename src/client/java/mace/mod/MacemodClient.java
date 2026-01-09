package mace.mod;

import com.mojang.blaze3d.platform.InputConstants;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;

public class MacemodClient implements ClientModInitializer {
    private static final Minecraft mc = Minecraft.getInstance();
    private static @NotNull ConfigHolder<McModConfig> HOLDER;
    public static final String MOD_ID = "macemod";

    private static final Random random = new Random();
    private static final Queue<AttackTask> attackQueue = new LinkedList<>();
    
    // Track when we last queued an attack to add human delay
    private static int ticksSinceLastQueue = 0;
    private static int nextActionDelay = 0;

    public static final @NotNull KeyMapping.Category MY_CATEGORY = new KeyMapping.Category(
            Identifier.withDefaultNamespace("category.macemod.main"));

    public static final @NotNull KeyMapping MY_KEYBIND = new KeyMapping(
            "key.macemod.scan",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            MY_CATEGORY);

    @Override
    public void onInitializeClient() {
        HOLDER = AutoConfig.register(McModConfig.class, JanksonConfigSerializer::new);
        KeyBindingHelper.registerKeyBinding(MY_KEYBIND);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            var player = mc.player;
            var level = mc.level;
            if (player == null || level == null)
                return;

            McModConfig cfg = HOLDER.get();

            // 1. Existing Hotkey Scan
            while (MY_KEYBIND.consumeClick()) {
                scanHotbar();
                player.displayClientMessage(Component.translatable(
                        "message.macemod.scan_result",
                        cfg.maceSlot + 1,
                        cfg.axeSlot + 1), false);
            }

            // 2. Process Attack Queue with delays
            if (!attackQueue.isEmpty()) {
                AttackTask task = attackQueue.peek();
                if (task != null) {
                    task.delay--;
                    if (task.delay <= 0) {
                        executeQueuedAttack(task);
                        attackQueue.poll();
                    }
                }
            }

            // Increment delay counter
            ticksSinceLastQueue++;

            // 3. Smart Swap & Attack Logic - Multiplayer Safe
            LivingEntity target = findTarget();
            if (target != null) {
                // RUN SMOOTH AIM if enabled
                if (cfg.AutoAim) {
                    CameraHelper.smoothLookAt(target, cfg);
                }

                // ATTACK LOGIC - Smart Weapon Selection
                double reach = player.isCreative() ? cfg.CreativeReach : cfg.SurvivalReach;
                double distSqr = player.distanceToSqr(target);
                
                if (distSqr <= reach * reach && player.getAttackStrengthScale(0) >= cfg.Cooldown) {
                    // Check if enough time has passed since last action (human delay)
                    if (ticksSinceLastQueue >= nextActionDelay) {
                        performSmartAttack(target, cfg);
                        
                        // Generate next random delay in ticks
                        int minTicks = msToTicks(cfg.minDelayMs);
                        int maxTicks = msToTicks(cfg.maxDelayMs);
                        nextActionDelay = minTicks + random.nextInt(Math.max(1, maxTicks - minTicks + 1));
                        ticksSinceLastQueue = 0;
                    }
                }
            }
        });
    }

    /**
     * Smart attack logic that chooses the best weapon based on fall distance and target state.
     * Multiplayer-safe with randomized delays.
     */
    private void performSmartAttack(@NotNull LivingEntity target, @NotNull McModConfig cfg) {
        var player = mc.player;
        if (player == null) return;

        boolean isFalling = player.getDeltaMovement().y < 0;
        boolean isHighFall = player.fallDistance > cfg.fallTriggerDistance;
        
        // Smart Swap Logic - The 5-Block Rule
        if (isFalling && isHighFall) {
            // Use Mace for high falls (massive damage)
            if (target.isBlocking() && cfg.axeSlot >= 0) {
                // Target is blocking - break shield first with axe, then mace
                queueAttack(cfg.axeSlot, target, "shield_break");
                queueAttack(cfg.maceSlot, target, "mace_strike");
            } else if (cfg.maceSlot >= 0) {
                // Direct mace attack
                queueAttack(cfg.maceSlot, target, "mace_strike");
            }
        } else {
            // Normal combat - use axe for consistent damage
            if (cfg.axeSlot >= 0) {
                queueAttack(cfg.axeSlot, target, "axe_strike");
            } else if (cfg.maceSlot >= 0) {
                // Fallback to mace if no axe
                queueAttack(cfg.maceSlot, target, "mace_strike");
            }
        }
    }

    /**
     * Queue an attack with human-like randomized delay.
     * @param slot The hotbar slot to use
     * @param target The entity to attack
     * @param attackType Type of attack for debugging (optional)
     */
    private void queueAttack(int slot, @NotNull LivingEntity target, String attackType) {
        var player = mc.player;
        ClientPacketListener connection = mc.getConnection();

        if (slot < 0 || player == null || connection == null)
            return;

        // Switch to the weapon slot
        player.getInventory().setSelectedSlot(slot);
        connection.send(new ServerboundSetCarriedItemPacket(slot));

        // Add random tick delay (1-3 ticks base + config delay)
        int baseDelay = 1 + random.nextInt(3);
        attackQueue.add(new AttackTask(target, slot, baseDelay, attackType));
    }

    /**
     * Execute a queued attack task.
     */
    private void executeQueuedAttack(@NotNull AttackTask task) {
        var player = mc.player;
        MultiPlayerGameMode gameMode = mc.gameMode;

        if (player == null || gameMode == null || task.target == null || !task.target.isAlive())
            return;

        gameMode.attack(player, task.target);
        player.swing(InteractionHand.MAIN_HAND);
    }

    /**
     * Convert milliseconds to Minecraft ticks (20 ticks per second).
     */
    private int msToTicks(int milliseconds) {
        return Math.max(1, (int) Math.ceil(milliseconds / 50.0)); // 50ms per tick
    }

    /**
     * Scan hotbar for mace and axe positions.
     */
    private void scanHotbar() {
        var player = mc.player;
        if (player == null)
            return;

        McModConfig cfg = HOLDER.get();
        cfg.maceSlot = -1;
        cfg.axeSlot = -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            Item item = stack.getItem();

            if (item == Items.MACE)
                cfg.maceSlot = i;
            if (isAxe(item))
                cfg.axeSlot = i;
        }
        HOLDER.save();
    }

    /**
     * Check if an item is an axe.
     */
    private boolean isAxe(@NotNull Item item) {
        return item == Items.WOODEN_AXE || item == Items.STONE_AXE ||
                item == Items.IRON_AXE || item == Items.GOLDEN_AXE ||
                item == Items.DIAMOND_AXE || item == Items.NETHERITE_AXE;
    }

    /**
     * Find the best target within reach and FOV.
     * Multiplayer-aware - prioritizes closest enemy within view cone.
     */
    private @Nullable LivingEntity findTarget() {
        var player = mc.player;
        var level = mc.level;
        if (player == null || level == null)
            return null;

        McModConfig cfg = HOLDER.get();
        double reach = player.isCreative() ? cfg.CreativeReach : cfg.SurvivalReach;
        double maxAngle = Math.toRadians(cfg.FOV / 2.0);

        Vec3 lookVec = player.getViewVector(1.0F);
        Vec3 playerPos = player.getEyePosition(1.0F);

        // Get all living entities in range
        List<LivingEntity> targets = level.getEntitiesOfClass(
                LivingEntity.class,
                player.getBoundingBox().inflate(reach),
                e -> e != null && e != player && e.isAlive());

        LivingEntity best = null;
        double bestDist = reach * reach;

        // Find closest target within FOV cone
        for (LivingEntity e : targets) {
            Vec3 toEntity = e.getBoundingBox().getCenter().subtract(playerPos);
            double dist = toEntity.lengthSqr();
            if (dist > bestDist)
                continue;

            // Calculate angle to entity
            Vec3 normalized = toEntity.normalize();
            double dotProduct = lookVec.dot(normalized);
            
            // Clamp dot product to valid range [-1, 1] to prevent NaN from acos
            dotProduct = Math.max(-1.0, Math.min(1.0, dotProduct));
            double angle = Math.acos(dotProduct);
            
            if (angle <= maxAngle) {
                best = e;
                bestDist = dist;
            }
        }
        return best;
    }

    /**
     * Task representing a queued attack action.
     */
    private static class AttackTask {
        final @Nullable LivingEntity target;
        final int slot;
        final String attackType;
        int delay;

        AttackTask(@Nullable LivingEntity target, int slot, int delay, String attackType) {
            this.target = target;
            this.slot = slot;
            this.delay = delay;
            this.attackType = attackType;
        }
    }
}