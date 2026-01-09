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

    // In Mojang mappings, categories are NOT typically just String IDs but they are
    // a real object
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

            // 2. Existing Attack Queue
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

            // 3. Updated Target/Camera Logic
            LivingEntity target = findTarget();
            if (target != null) {
                // RUN SMOOTH AIM
                if (cfg.AutoAim) {
                    CameraHelper.smoothLookAt(target, cfg);
                }

                // ATTACK LOGIC
                double reach = player.isCreative() ? cfg.CreativeReach : cfg.SurvivalReach;
                if (player.distanceToSqr(target) <= reach * reach
                        && player.getAttackStrengthScale(0) >= cfg.Cooldown) {

                    if (shouldAttackWhileFalling(cfg)) {
                        if (target.isBlocking()) {
                            queueAttack(cfg.axeSlot, target);
                            queueAttack(cfg.maceSlot, target);
                        } else {
                            queueAttack(cfg.maceSlot, target);
                        }
                    }
                }
            }
        });
    }

    private void queueAttack(int slot, LivingEntity target) {
        var player = mc.player;
        ClientPacketListener connection = mc.getConnection();

        // FIX: Ensure connection and player are not null before sending packets
        if (slot < 0 || player == null || connection == null)
            return;

        player.getInventory().setSelectedSlot(slot);
        connection.send(new ServerboundSetCarriedItemPacket(slot));

        attackQueue.add(new AttackTask(target, slot, 1 + random.nextInt(3)));
    }

    private void executeQueuedAttack(AttackTask task) {
        var player = mc.player;
        MultiPlayerGameMode gameMode = mc.gameMode;

        // FIX: Check gameMode nullability (Severity 4 diagnostic fix)
        if (player == null || gameMode == null || task.target == null)
            return;

        gameMode.attack(player, task.target);
        player.swing(InteractionHand.MAIN_HAND);
    }

    private boolean shouldAttackWhileFalling(McModConfig cfg) {
        var player = mc.player;
        if (player == null)
            return false;
        return player.fallDistance > cfg.FallDistance && player.getDeltaMovement().y < 0;
    }

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

    private boolean isAxe(Item item) {
        return item == Items.WOODEN_AXE || item == Items.STONE_AXE ||
                item == Items.IRON_AXE || item == Items.GOLDEN_AXE ||
                item == Items.DIAMOND_AXE || item == Items.NETHERITE_AXE;
    }

    private LivingEntity findTarget() {
        var player = mc.player;
        var level = mc.level;
        if (player == null || level == null)
            return null;

        McModConfig cfg = HOLDER.get();
        double reach = player.isCreative() ? cfg.CreativeReach : cfg.SurvivalReach;
        double maxAngle = Math.toRadians(cfg.FOV / 2.0);

        Vec3 lookVec = player.getViewVector(1.0F);
        Vec3 playerPos = player.getEyePosition(1.0F);

        // FIX: Explicitly handle the list nullability and type safety
        List<@NotNull LivingEntity> targets = level.getEntitiesOfClass(
                LivingEntity.class,
                player.getBoundingBox().inflate(reach),
                e -> e != null && e != player && e.isAlive());

        LivingEntity best = null;
        double bestDist = reach * reach;

        for (LivingEntity e : targets) {
            Vec3 toEntity = e.getBoundingBox().getCenter().subtract(playerPos);
            double dist = toEntity.lengthSqr();
            if (dist > bestDist)
                continue;

            double angle = Math.acos(lookVec.dot(toEntity.normalize()));
            if (angle <= maxAngle) {
                best = e;
                bestDist = dist;
            }
        }
        return best;
    }

    private static class AttackTask {
        final LivingEntity target;
        final int slot; // Used in queueAttack for logic consistency
        int delay;

        AttackTask(LivingEntity target, int slot, int delay) {
            this.target = target;
            this.slot = slot;
            this.delay = delay;
        }
    }
}