package com.example;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public class ExampleModClient implements ClientModInitializer {

    public static boolean triggerBot = true;
    public static boolean aimAssist = false;
    public static boolean autoSprint = true;
    public static boolean onlyCrits = true;

    private static KeyBinding guiKey;

    @Override
    public void onInitializeClient() {
        guiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.skillmask.gui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                "category.skillmask"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            while (guiKey.wasPressed()) {
                client.setScreen(new SkillMaskGui());
            }

            // 1. AutoSprint
            if (autoSprint && client.player.forwardSpeed > 0 && !client.player.horizontalCollision && !client.player.isSneaking()) {
                client.player.setSprinting(true);
            }

            // 2. AimAssist (плавная доводка под GrimAC)
            if (aimAssist && client.currentScreen == null) {
                PlayerEntity target = getClosestPlayer(client, 3.6);
                if (target != null) {
                    smoothAim(client, target, 0.14f);
                }
            }

            // 3. TriggerBot (с критами в падении)
            if (triggerBot && client.currentScreen == null && client.crosshairTarget instanceof EntityHitResult hit) {
                Entity entity = hit.getEntity();
                if (entity instanceof PlayerEntity target && target.isAlive() && target != client.player) {
                    boolean canCrit = !onlyCrits || (client.player.fallDistance > 0.05f && !client.player.isOnGround() && !client.player.isTouchingWater());
                    if (client.player.getAttackCooldownProgress(0.5f) >= 1.0f && canCrit) {
                        client.interactionManager.attackEntity(client.player, target);
                        client.player.swingHand(Hand.MAIN_HAND);
                    }
                }
            }
        });

        // Лёгкий 2D HUD без шейдеров для максимального FPS на MobileGlues
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.options.hudHidden) return;

            context.drawTextWithShadow(client.textRenderer, "§bSkillMask§fClient §71.21.11", 6, 6, 0xFFFFFF);
            int y = 18;
            if (triggerBot) { context.drawTextWithShadow(client.textRenderer, "§7- §aTriggerBot", 6, y, 0xFFFFFF); y += 10; }
            if (aimAssist)  { context.drawTextWithShadow(client.textRenderer, "§7- §aAimAssist", 6, y, 0xFFFFFF); y += 10; }
            if (autoSprint) { context.drawTextWithShadow(client.textRenderer, "§7- §aAutoSprint", 6, y, 0xFFFFFF); y += 10; }
            if (onlyCrits)  { context.drawTextWithShadow(client.textRenderer, "§7- §eOnlyCrits", 6, y, 0xFFFFFF); }
        });
    }

    private PlayerEntity getClosestPlayer(MinecraftClient client, double range) {
        PlayerEntity closest = null;
        double minDist = range;
        for (PlayerEntity p : client.world.getPlayers()) {
            if (p == client.player || !p.isAlive() || p.isSpectator()) continue;
            double dist = client.player.distanceTo(p);
            if (dist < minDist) {
                minDist = dist;
                closest = p;
            }
        }
        return closest;
    }

    private void smoothAim(MinecraftClient client, PlayerEntity target, float speed) {
        Vec3d eyePos = client.player.getEyePos();
        Vec3d targetPos = target.getPos().add(0, target.getHeight() * 0.75, 0);
        double dx = targetPos.x - eyePos.x;
        double dy = targetPos.y - eyePos.y;
        double dz = targetPos.z - eyePos.z;
        double distXZ = Math.sqrt(dx * dx + dz * dz);

        float targetYaw = (float) (MathHelper.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
        float targetPitch = (float) (-(MathHelper.atan2(dy, distXZ) * (180.0 / Math.PI)));

        float yawDiff = MathHelper.wrapDegrees(targetYaw - client.player.getYaw());
        float pitchDiff = MathHelper.wrapDegrees(targetPitch - client.player.getPitch());

        if (Math.abs(yawDiff) < 65.0f) {
            client.player.setYaw(client.player.getYaw() + yawDiff * speed);
            client.player.setPitch(MathHelper.clamp(client.player.getPitch() + pitchDiff * speed, -90.0f, 90.0f));
        }
    }

    public static class SkillMaskGui extends Screen {
        protected SkillMaskGui() {
            super(Text.literal("SkillMaskClient"));
        }

        @Override
        protected void init() {
            int cx = this.width / 2 - 75;
            int cy = this.height / 2 - 55;

            this.addDrawableChild(ButtonWidget.builder(Text.literal("TriggerBot: " + (triggerBot ? "§aON" : "§cOFF")), b -> {
                triggerBot = !triggerBot;
                b.setMessage(Text.literal("TriggerBot: " + (triggerBot ? "§aON" : "§cOFF")));
            }).dimensions(cx, cy, 150, 20).build());

            this.addDrawableChild(ButtonWidget.builder(Text.literal("OnlyCrits: " + (onlyCrits ? "§aON" : "§cOFF")), b -> {
                onlyCrits = !onlyCrits;
                b.setMessage(Text.literal("OnlyCrits: " + (onlyCrits ? "§aON" : "§cOFF")));
            }).dimensions(cx, cy + 25, 150, 20).build());

            this.addDrawableChild(ButtonWidget.builder(Text.literal("AimAssist: " + (aimAssist ? "§aON" : "§cOFF")), b -> {
                aimAssist = !aimAssist;
                b.setMessage(Text.literal("AimAssist: " + (aimAssist ? "§aON" : "§cOFF")));
            }).dimensions(cx, cy + 50, 150, 20).build());

            this.addDrawableChild(ButtonWidget.builder(Text.literal("AutoSprint: " + (autoSprint ? "§aON" : "§cOFF")), b -> {
                autoSprint = !autoSprint;
                b.setMessage(Text.literal("AutoSprint: " + (autoSprint ? "§aON" : "§cOFF")));
            }).dimensions(cx, cy + 75, 150, 20).build());
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(this.width / 2 - 85, this.height / 2 - 75, this.width / 2 + 85, this.height / 2 + 105, 0xAA101015);
            context.drawCenteredTextWithShadow(this.textRenderer, "§bSkillMaskClient §fMenu", this.width / 2, this.height / 2 - 68, 0xFFFFFF);
            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean shouldPause() {
            return false;
        }
    }
}
