package mace.mod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import java.util.List;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;

public class MaceModClient implements ClientModInitializer {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static ConfigHolder<McModConfig> HOLDER;
    public static final String MOD_ID = "macemod";

    private static final Random random = new Random();

    // Attack queue (multiple tasks can stack)
    private static final Queue<AttackTask> attackQueue = new LinkedList<>();

    public static final KeyBinding.Category MY_CATEGORY = KeyBinding.Category.create(
        Identifier.of(MOD_ID, "category.macemod")
    );

    public static final KeyBinding MY_KEYBIND = new KeyBinding(
        "key.macemod.scan",
        InputUtil.Type.KEYSYM,
        InputUtil.GLFW_KEY_G,
        MY_CATEGORY
    );

    @Override
    public void onInitializeClient() {
        HOLDER = AutoConfig.register(McModConfig.class, JanksonConfigSerializer::new);
        KeyBindingHelper.registerKeyBinding(MY_KEYBIND);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (mc.player == null) return;

            // Hotkey scan
            while (MY_KEYBIND.wasPressed()) {
                scanHotbar();
                McModConfig cfg = HOLDER.get();
                mc.player.sendMessage(net.minecraft.text.Text.translatable(
                        "message.macemod.scan_result",
                        cfg.maceSlot + 1,
                        cfg.axeSlot + 1),
                        false);
            }

            // Process queued attacks
            if (!attackQueue.isEmpty()) {
                AttackTask task = attackQueue.peek();
                task.delay--;
                if (task.delay <= 0) {
                    executeQueuedAttack(task);
                    attackQueue.poll(); // remove finished task
                }
            }

            // Auto attack logic
            LivingEntity target = findTarget();
            if (target != null) {
                McModConfig cfg = HOLDER.get();
                double reach = mc.player.isCreative() ? cfg.CreativeReach : cfg.SurvivalReach;

                if (mc.player.squaredDistanceTo(target) <= reach * reach
                        && mc.player.getAttackCooldownProgress(0) >= cfg.Cooldown) {

                    if (!isUsingShield(target) && shouldAttackWhileFalling()) {
                        queueAttack(cfg.maceSlot, target);
                    } else if (isUsingShield(target) && shouldAttackWhileFalling()) {
                        // Axe first
                        queueAttack(cfg.axeSlot, target);
                        // Follow-up mace
                        queueAttack(cfg.maceSlot, target);
                    }
                }
            }
        });
    }

    private void queueAttack(int slot, LivingEntity target) {
        if (slot < 0) return;

        // Switch immediately client-side
        mc.player.getInventory().setSelectedSlot(slot);
        mc.player.networkHandler.sendPacket(
                new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(slot)
        );

        // Add queued attack with random delay (1–3 ticks)
        attackQueue.add(new AttackTask(target, slot, 1 + random.nextInt(3)));
    }

    private void executeQueuedAttack(AttackTask task) {
        if (mc.player == null || task.target == null) return;
        McModConfig cfg = HOLDER.get();
        double reach = mc.player.isCreative() ? cfg.CreativeReach : cfg.SurvivalReach;

        if (mc.player.squaredDistanceTo(task.target) <= reach * reach
                && mc.player.getAttackCooldownProgress(0) >= cfg.Cooldown) {
            mc.interactionManager.attackEntity(mc.player, task.target);
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    private static class AttackTask {
        LivingEntity target;
        int slot;
        int delay;
        AttackTask(LivingEntity target, int slot, int delay) {
            this.target = target;
            this.slot = slot;
            this.delay = delay;
        }
    }

    private boolean shouldAttackWhileFalling() {
        McModConfig cfg = HOLDER.get();
        return mc.player.fallDistance > cfg.FallDistance && mc.player.getVelocity().y < 0;
    }

    private boolean isUsingShield(LivingEntity target) {
        return target.isBlocking();
    }

    private void scanHotbar() {
        McModConfig cfg = HOLDER.get();
        cfg.maceSlot = -1;
        cfg.axeSlot = -1;

        for (int i = 0; i < 9; i++) {
            Item item = mc.player.getInventory().getStack(i).getItem();

            if (item == Items.MACE) {
                cfg.maceSlot = i;
            }
            if (item == Items.WOODEN_AXE || item == Items.STONE_AXE ||
                item == Items.IRON_AXE || item == Items.GOLDEN_AXE ||
                item == Items.DIAMOND_AXE || item == Items.NETHERITE_AXE) {
                cfg.axeSlot = i;
            }
        }
        HOLDER.save();
    }

    private LivingEntity findTarget() {
        McModConfig cfg = HOLDER.get();
        double reach = mc.player.isCreative() ? cfg.CreativeReach : cfg.SurvivalReach;
        double maxAngle = Math.toRadians(cfg.FOV / 2.0); // half-angle in radians

        Vec3d lookVec = mc.player.getRotationVec(1.0f).normalize();
        Vec3d playerPos = mc.player.getCameraPosVec(1.0f);

        LivingEntity bestTarget = null;
        double bestDistanceSq = reach * reach;

        // Get nearby entities
        List<LivingEntity> nearby = mc.world.getEntitiesByClass(
                LivingEntity.class,
                mc.player.getBoundingBox().expand(reach),
                e -> e != mc.player && e.isAlive()
        );

        for (LivingEntity entity : nearby) {
            Vec3d entityPos = new Vec3d(entity.getX(), entity.getY(), entity.getZ());
            Vec3d toEntity = entityPos.add(0, entity.getStandingEyeHeight() * 0.5, 0).subtract(playerPos);
            double distanceSq = toEntity.lengthSquared();

            if (distanceSq > bestDistanceSq) continue;

            // Angle between look direction and entity vector
            double angle = Math.acos(lookVec.dotProduct(toEntity.normalize()));
            if (angle <= maxAngle) {
                // Closer and within FOV
                bestDistanceSq = distanceSq;
                bestTarget = entity;
            }
        }
        return bestTarget;
    }
}
