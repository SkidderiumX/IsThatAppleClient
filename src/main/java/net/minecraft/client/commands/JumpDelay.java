package net.minecraft.client.commands;

import java.io.File;

/**
 * Jump delay: puts a gap between jumps while the jump key is held, so holding it hops at a set
 * rate instead of every time the player touches ground.
 *
 * <p>This is the gap vanilla already had. {@code EntityLivingBase} carries a {@code jumpTicks}
 * field that is decremented once a tick and gates the jump on reaching zero, but vanilla resets
 * it to zero the moment it jumps, so the field never does anything and the gate never bites.
 * Reinstating it would have been a constant, so this keeps the same idea behind a setting. What
 * the mod does not reuse is vanilla's reset on release: the gap here runs down whatever the jump
 * key is doing, so letting go and pressing again does not hand back a free instant jump.</p>
 *
 * <p>Nothing needs to reach the server for this to hold. A walking server never simulates the
 * jump at all - it takes the client's reported position as authoritative - so the client decides
 * where the jump goes. Only riding sends a jump flag, in {@code C0CPacketInput}, and that is
 * gated on the same cooldown so the horse does not jump on a tick the player did not.</p>
 *
 * <p>Counted in ticks. Those are world ticks, and {@link TickSpeed} holds the world to 20 a
 * second whatever the client's own tick rate is, so raising that rate does not quietly shorten
 * this. The two settings cannot disagree about how long a tick is.</p>
 *
 * <p>Settings are stored in {@value ClientConfig#FILE_NAME} in the game directory and rewritten on
 * every change.</p>
 */
public class JumpDelay
{
    /** Longest gap offered, in ticks. Vanilla's unused field was set to 10, half a second. */
    public static final int MAX_DELAY = 20;

    /** No gap, which is what this build has always done. */
    public static final int MIN_DELAY = 0;

    private static boolean enabled;

    private static int delay = MIN_DELAY;

    /** Ticks left before a jump is allowed again. Not persisted: it is spent within a second. */
    private static int cooldown;

    /**
     * What {@link #step()} decided for the current tick.
     *
     * <p>Recorded rather than recomputed from the cooldown, because the cooldown is decremented
     * before it is tested and the two disagree on the tick it reaches zero: the counter is already
     * clear, but this tick's jump has still been refused. Anything else asking has to be told what
     * the jump input was actually gated on, not what the leftover counter happens to say.</p>
     */
    private static boolean allowed = true;

    private JumpDelay() {}

    // --- state -------------------------------------------------------------------

    public static boolean isEnabled()
    {
        return enabled;
    }

    /**
     * Turns the delay on or off.
     *
     * <p>Turning it off clears the cooldown, so a jump is never left blocked by a delay the user
     * has just switched off. The cooldown is only ever consumed while the jump key is down, so
     * nothing is lost that was not already in flight.</p>
     */
    public static void setEnabled(boolean value)
    {
        if (enabled == value)
        {
            return;
        }

        enabled = value;

        if (!enabled)
        {
            cooldown = 0;
        }

        save();
    }

    public static int getDelay()
    {
        return delay;
    }

    /**
     * Sets the gap in ticks. Clamped rather than rejected, so the value is always usable.
     *
     * <p>A gap already in progress is left to finish: the new delay governs the next jump, not
     * one the player is halfway through. Zero is the exception, since it means no delay at all
     * and blocking a jump for the remainder of a gap the user has just cancelled would be
     * surprising.</p>
     */
    public static void setDelay(int value)
    {
        int clamped = Math.max(MIN_DELAY, Math.min(MAX_DELAY, value));

        if (clamped == delay)
        {
            return;
        }

        delay = clamped;

        if (delay == MIN_DELAY)
        {
            cooldown = 0;
        }

        save();
    }

    // --- the countdown -----------------------------------------------------------

    /**
     * Advances the cooldown by one tick and answers whether a jump may start now.
     *
     * <p>Called once per client tick, from where the jump input is read, and ahead of the jump
     * itself, so the countdown is down before it is tested.</p>
     *
     * <p>Run before the jump key is consulted, and so not short-circuited by it - otherwise the
     * countdown would only advance while the key was held and would stall mid-gap otherwise.</p>
     *
     * @return true if a jump may start this tick
     */
    public static boolean step()
    {
        allowed = cooldown == 0;

        if (cooldown > 0)
        {
            --cooldown;
        }

        return allowed;
    }

    /**
     * Whether a jump was allowed on this tick, without advancing anything.
     *
     * <p>For the riding packet, which is sent from a different method to the one that steps the
     * countdown. Answering from the same recorded decision is what keeps the horse and the rider
     * in step: reading the leftover counter instead would report the tick the cooldown runs out
     * as clear, and the server would be told to jump on a tick the client had just refused.</p>
     */
    public static boolean allowsJump()
    {
        return allowed;
    }

    /**
     * Starts the gap. Called when a jump actually happens rather than when the key was pressed,
     * so holding the key in the air does not burn a gap the player never used.
     *
     * <p>Arms one tick short of the delay. The tick the jump fires on is not a blocked tick, and
     * the next {@code delay - 1} are, so the jump after it lands exactly {@code delay} ticks
     * later. Arming the full delay here would give a gap of {@code delay + 1}. Vanilla gets this
     * for free by decrementing before testing; here the jump happens after the countdown has
     * already been stepped for the tick.</p>
     */
    public static void onJumped()
    {
        if (enabled && delay > MIN_DELAY)
        {
            cooldown = delay - 1;
        }
    }

    // --- reporting ---------------------------------------------------------------

    /** Ticks left before a jump is allowed, or zero if none is pending. */
    public static int getCooldown()
    {
        return cooldown;
    }

    // --- persistence -------------------------------------------------------------

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

    /** Restores the defaults in memory. */
    static void resetToDefaults()
    {
        enabled = true;
        delay = MIN_DELAY;
        cooldown = 0;
        allowed = true;
    }

    /** Restores the defaults and rewrites the config file. */
    public static void reset()
    {
        resetToDefaults();
        save();
    }

    /** Writes the config file. Never throws. */
    public static void save()
    {
        ClientConfig.save();
    }
}
