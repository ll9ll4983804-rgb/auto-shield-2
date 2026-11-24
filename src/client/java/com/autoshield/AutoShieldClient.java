package com.autoshield;

import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Auto Shield: raises the shield BEFORE an arrow / projectile hits (predicts it), and also reacts
 * to damage as a fallback. If the shield is in the hotbar it switches to it, then goes back to
 * the previous slot once the danger has passed.
 */
public class AutoShieldClient implements ClientModInitializer {
    private static final double RANGE = 14.0;        // how far to look for projectiles (blocks)
    private static final double AIM_DOT = 0.85;      // 1.0 = flying exactly at you
    private static final int HOLD_AFTER_CLEAR = 6;   // ticks to keep the shield up after the danger is gone
    private static final double MELEE_RANGE = 6.0;   // look for mace players this close (blocks)
    private static final double MELEE_CLOSE = 4.0;   // a mace player this close counts as a threat
    private static final int DAMAGE_HOLD = 8;        // ticks to block after you took damage

    private boolean held = false;       // we are holding the use key
    private int previousSlot = -1;
    private int shieldSlot = -1;
    private int clearTicks = 0;
    private int damageTicks = 0;
    private float lastHealth = -1.0f;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            reset(mc);
            lastHealth = -1.0f;
            return;
        }

        float health = player.getHealth();
        if (lastHealth > 0.0f && health < lastHealth) {
            damageTicks = DAMAGE_HOLD;
        }
        lastHealth = health;
        if (damageTicks > 0) {
            damageTicks--;
        }

        boolean screenOpen = mc.gui.screen() != null;
        boolean manualUse = !held && mc.options.keyUse.isDown(); // you are eating / drawing a bow yourself
        boolean threat = !screenOpen && !manualUse
                && (projectileIncoming(mc, player) || maceThreat(mc, player) || damageTicks > 0);

        if (threat) {
            clearTicks = 0;
            if (!hasShieldInHand(player)) {
                Inventory inv = player.getInventory();
                for (int i = 0; i < 9; i++) {
                    if (inv.getItem(i).is(Items.SHIELD)) {
                        if (previousSlot < 0) {
                            previousSlot = inv.getSelectedSlot();
                        }
                        inv.setSelectedSlot(i);
                        shieldSlot = i;
                        break;
                    }
                }
            }
            if (hasShieldInHand(player)) {
                mc.options.keyUse.setDown(true);
                held = true;
            }
        } else if (held || previousSlot >= 0) {
            if (++clearTicks >= HOLD_AFTER_CLEAR) {
                release(mc, player);
            }
        }
    }

    private boolean hasShieldInHand(LocalPlayer player) {
        return player.getMainHandItem().is(Items.SHIELD) || player.getOffhandItem().is(Items.SHIELD);
    }

    /** True if another player holding a mace is close, or is falling onto you from above. */
    private boolean maceThreat(Minecraft mc, LocalPlayer player) {
        AABB area = player.getBoundingBox().inflate(MELEE_RANGE);
        List<Player> others = mc.level.getEntitiesOfClass(
                Player.class, area, p -> p != player && p.getMainHandItem().is(Items.MACE));
        for (Player p : others) {
            if (p.distanceTo(player) <= MELEE_CLOSE) {
                return true;
            }
            if (p.getDeltaMovement().y < -0.3 && p.getY() > player.getY()) {
                return true;
            }
        }
        return false;
    }

    /** True if a projectile that is not yours is flying toward you. */
    private boolean projectileIncoming(Minecraft mc, LocalPlayer player) {
        Vec3 me = player.getBoundingBox().getCenter();
        AABB area = player.getBoundingBox().inflate(RANGE);
        List<Projectile> list = mc.level.getEntitiesOfClass(
                Projectile.class, area, p -> p.getOwner() != player);
        for (Projectile p : list) {
            Vec3 velocity = p.getDeltaMovement();
            Vec3 toMe = me.subtract(p.position());
            if (velocity.lengthSqr() < 1.0E-4 || toMe.lengthSqr() < 1.0E-4) {
                continue;
            }
            if (velocity.normalize().dot(toMe.normalize()) > AIM_DOT) {
                return true;
            }
        }
        return false;
    }

    private void release(Minecraft mc, LocalPlayer player) {
        if (held) {
            mc.options.keyUse.setDown(false);
        }
        if (previousSlot >= 0) {
            Inventory inv = player.getInventory();
            if (inv.getSelectedSlot() == shieldSlot) {
                inv.setSelectedSlot(previousSlot);
            }
        }
        held = false;
        previousSlot = -1;
        shieldSlot = -1;
        clearTicks = 0;
    }

    private void reset(Minecraft mc) {
        if (held && mc.options != null) {
            mc.options.keyUse.setDown(false);
        }
        held = false;
        previousSlot = -1;
        shieldSlot = -1;
        clearTicks = 0;
        damageTicks = 0;
    }
}
