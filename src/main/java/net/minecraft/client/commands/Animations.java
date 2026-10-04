package net.minecraft.client.commands;

import java.io.File;
import net.minecraft.entity.Entity;

/**
 * Old Animations, exposed as command-driven settings.
 *
 * <p>Ported from the {@code AnimationsMod} of the Glide client, which reached vanilla
 * through seven mixins. Those mixins become plain calls into this class from the same
 * vanilla methods. Everything is off by default: {@code /anim on} opts in.</p>
 *
 * <p>Persisted in {@value ClientConfig#FILE_NAME} in the game directory, alongside every other
 * mod setting, and written on every change.</p>
 */
public final class Animations
{
    /** Lowest accepted {@link #smoothSneakSpeed}. */
    private static final float MIN_SMOOTH_SNEAK_SPEED = 0.5F;

    /** Highest accepted {@link #smoothSneakSpeed}. */
    private static final float MAX_SMOOTH_SNEAK_SPEED = 20.0F;

    /** Master switch. Mirrors {@code mod.isToggled()} in the original. */
    private static boolean enabled;

    /** Swing the hand while mining instead of freezing the swing at rest. */
    private static boolean blockHit = true;

    /** Show block break particles and swing on use-key, without real block damage. */
    private static boolean pushing = true;

    /** Emit the crack particles that go with {@link #pushing}. */
    private static boolean pushingParticle = true;

    /** Lower the camera and held item while sneaking instead of snapping. */
    private static boolean sneak = true;

    /** Ease into the sneak height over time rather than instantly. */
    private static boolean smoothSneak = false;

    /** Smoothing rate for {@link #smoothSneak}, 0.5-20. */
    private static float smoothSneakSpeed = 6.0F;

    /** Freeze the heart row at the value it had before damage. */
    private static boolean health = true;

    /** Render damaged armour as the untinted base texture. */
    private static boolean armorDamage = false;

    /** Smooth the item switch animation. */
    private static boolean itemSwitch = false;

    /** Old-style rod/fishing line rendering for flat items. */
    private static boolean rod = false;

    // --- eye height smoothing runtime ------------------------------------------
    private static float height;
    private static float previousHeight;
    private static float smoothValue;
    private static long smoothLastMs;

    private Animations() {}

    // --- accessors used by the vanilla hooks ------------------------------------

    public static boolean isEnabled()
    {
        return enabled;
    }

    public static void setEnabled(boolean value)
    {
        enabled = value;
        save();
    }

    public static boolean isBlockHit()
    {
        return enabled && blockHit;
    }

    public static boolean isPushing()
    {
        return enabled && pushing;
    }

    public static boolean isPushingParticle()
    {
        return enabled && pushingParticle;
    }

    public static boolean isSneak()
    {
        return enabled && sneak;
    }

    public static boolean isSmoothSneak()
    {
        return enabled && smoothSneak;
    }

    public static void setSmoothSneakSpeed(float value)
    {
        smoothSneakSpeed = clampSpeed(value);
        save();
    }

    public static float getSmoothSneakSpeed()
    {
        return smoothSneakSpeed;
    }

    /** Smallest accepted sneak smoothing rate. */
    public static float getMinSmoothSneakSpeed()
    {
        return MIN_SMOOTH_SNEAK_SPEED;
    }

    /** Largest accepted sneak smoothing rate. */
    public static float getMaxSmoothSneakSpeed()
    {
        return MAX_SMOOTH_SNEAK_SPEED;
    }

    public static boolean isHealth()
    {
        return enabled && health;
    }

    public static boolean isArmorDamage()
    {
        return enabled && armorDamage;
    }

    public static boolean isItemSwitch()
    {
        return enabled && itemSwitch;
    }

    public static boolean isRod()
    {
        return enabled && rod;
    }

    /** Sets one flag by config key name and persists it. Used by the command. */
    static void setFlag(String key, boolean value)
    {
        assign(key, value);
        save();
    }

    /**
     * Assigns one flag by config key name without persisting.
     *
     * @throws IllegalArgumentException if the key is not a known flag
     */
    private static void assign(String key, boolean value)
    {
        if (key.equals("blockHit"))
        {
            blockHit = value;
        }
        else if (key.equals("pushing"))
        {
            pushing = value;
        }
        else if (key.equals("pushingParticle"))
        {
            pushingParticle = value;
        }
        else if (key.equals("sneak"))
        {
            sneak = value;
        }
        else if (key.equals("smoothSneak"))
        {
            smoothSneak = value;
        }
        else if (key.equals("health"))
        {
            health = value;
        }
        else if (key.equals("armorDamage"))
        {
            armorDamage = value;
        }
        else if (key.equals("itemSwitch"))
        {
            itemSwitch = value;
        }
        else if (key.equals("rod"))
        {
            rod = value;
        }
        else
        {
            throw new IllegalArgumentException("Unknown animations flag: " + key);
        }
    }

    /** Raw stored value, ignoring the master switch. For the command's status output. */
    static boolean rawFlag(String key)
    {
        if (key.equals("blockHit"))
        {
            return blockHit;
        }

        if (key.equals("pushing"))
        {
            return pushing;
        }

        if (key.equals("pushingParticle"))
        {
            return pushingParticle;
        }

        if (key.equals("smoothSneak"))
        {
            return smoothSneak;
        }

        if (key.equals("sneak"))
        {
            return sneak;
        }

        if (key.equals("health"))
        {
            return health;
        }

        if (key.equals("armorDamage"))
        {
            return armorDamage;
        }

        if (key.equals("itemSwitch"))
        {
            return itemSwitch;
        }

        if (key.equals("rod"))
        {
            return rod;
        }

        throw new IllegalArgumentException("Unknown animations flag: " + key);
    }

    // --- eye height -------------------------------------------------------------

    /**
     * Tracks the smoothed eye height. Called once per frame from
     * {@code EntityRenderer.updateRenderer}.
     */
    public static void updateEyeHeight(Entity entity)
    {
        float now = entity.getEyeHeight();
        previousHeight = height;

        // Drop instantly, rise gradually, so crouching feels immediate but standing up
        // does not jolt the camera.
        if (now < height)
        {
            height = now;
        }
        else
        {
            height += (now - height) * 0.5F;
        }
    }

    /**
     * Replacement for {@code Entity.getEyeHeight()} in the camera and crosshair paths.
     *
     * <p>The original mixin applied {@code smoothSneak} even while the mod was toggled
     * off. That is gated on {@link #isEnabled()} here, because {@code /anim off} should
     * mean nothing applies.</p>
     */
    public static float modifyEyeHeight(Entity entity, float partialTicks)
    {
        float base;

        if (isSneak())
        {
            base = previousHeight + (height - previousHeight) * partialTicks;
        }
        else
        {
            base = entity.getEyeHeight();
        }

        if (isSmoothSneak())
        {
            return ease(base, smoothSneakSpeed * 10.0D);
        }

        return base;
    }

    /**
     * Frame-rate independent easing, ported from the client's SimpleAnimation plus
     * AnimationUtils.calculateCompensation.
     */
    private static float ease(float target, double speed)
    {
        long now = System.currentTimeMillis();

        // First call after startup: there is no previous frame to measure against, so
        // snap straight to the target rather than deriving a delta from the epoch.
        if (smoothLastMs == 0L)
        {
            smoothLastMs = now;
            smoothValue = target;
            return smoothValue;
        }

        long delta = now - smoothLastMs;
        smoothLastMs = now;

        if (speed > 28.0D)
        {
            speed = 28.0D;
        }

        double step = speed == 0.0D ? 0.0D : Math.abs(target - smoothValue) * 0.35F / (10.0D / speed);
        double add = delta * (step / 50.0D);
        float diff = smoothValue - target;

        if (diff > step)
        {
            if (smoothValue - add > target)
            {
                smoothValue -= add;
            }
            else
            {
                smoothValue = target;
            }
        }
        else if (diff < -step)
        {
            if (smoothValue + add < target)
            {
                smoothValue += add;
            }
            else
            {
                smoothValue = target;
            }
        }
        else if (Math.abs(smoothValue - target) < 0.03F)
        {
            smoothValue = target;
        }

        return smoothValue;
    }

    // --- persistence ------------------------------------------------------------

    /**
     * The single config file in the game directory.
     *
     * <p>Kept as a method on each mod so the commands can name the file without knowing that
     * {@link ClientConfig} owns it.</p>
     */
    public static File getConfigFile()
    {
        return ClientConfig.getConfigFile();
    }

    /** Restores every default in memory. Does not write the file; see {@link ClientConfig#load()}. */
    static void resetToDefaults()
    {
        enabled = false;
        blockHit = true;
        pushing = true;
        pushingParticle = true;
        sneak = true;
        smoothSneak = false;
        smoothSneakSpeed = 6.0F;
        health = true;
        armorDamage = false;
        itemSwitch = false;
        rod = false;
    }

    /** Restores every default and rewrites the config file. */
    public static void reset()
    {
        resetToDefaults();
        save();
    }

    private static float clampSpeed(float value)
    {
        if (Float.isNaN(value) || Float.isInfinite(value))
        {
            return 6.0F;
        }

        return Math.max(MIN_SMOOTH_SNEAK_SPEED, Math.min(MAX_SMOOTH_SNEAK_SPEED, value));
    }

    /** Writes the config file. Never throws. */
    public static void save()
    {
        ClientConfig.save();
    }
}
