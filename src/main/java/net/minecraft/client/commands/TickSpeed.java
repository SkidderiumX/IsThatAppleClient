package net.minecraft.client.commands;

import java.io.File;
import net.minecraft.util.Timer;

/**
 * Tick speed: runs the client's own tick loop faster, so key presses, the mouse and any GUI are
 * polled more often and register sooner.
 *
 * <p>What deliberately does <em>not</em> speed up is the world. {@code Timer.ticksPerSecond} is
 * what turns real time into a tick count, so raising it would run the player's physics, the
 * movement packets and the sound and particle clocks that many times faster than the server. The
 * server runs at a fixed 20 Hz on its own thread - including the integrated server in single
 * player - so the client would outrun it and be pulled back every tick.
 *
 * <p>So the world sections of {@code Minecraft.runTick} are held to {@value #BASE_TICKS} per
 * second however fast the input ticks go. That is a carried average rather than a round number,
 * so any tick rate lands on exactly 20 world ticks per second instead of drifting; at or below
 * the vanilla rate it short-circuits to vanilla and never touches the accumulator at all.</p>
 *
 * <p>Worth knowing: the game loop ticks at most {@value #MAX_TICKS_PER_FRAME} times per frame, so
 * a rate above ten times your frame rate cannot be reached. The command reports the ceiling.</p>
 *
 * <p>Settings are stored in {@value ClientConfig#FILE_NAME} in the game directory and rewritten on
 * every change.</p>
 */
public class TickSpeed
{
    /**
     * Ticks per second the world runs at, and what the server runs at.
     *
     * <p>Fixed, because the server's rate is not ours to choose.</p>
     */
    public static final float BASE_TICKS = 20.0F;

    /** Slowest input rate offered, which runs the world at that rate too: slow motion. */
    public static final float MIN_TICKS = 5.0F;

    /** Fastest input rate offered. Past this the frame cost starts to bite hard. */
    public static final float MAX_TICKS = 200.0F;

    /**
     * Hard ceiling in {@link Timer}: {@code elapsedTicks} is clamped to this per frame.
     *
     * <p>So the rate actually achieved is roughly {@code min(requested, fps * this)}, and asking
     * for more than the machine can frame is the same as asking for exactly that ceiling.</p>
     */
    public static final int MAX_TICKS_PER_FRAME = 10;

    private static boolean enabled;

    private static float ticks = BASE_TICKS;

    /** World ticks owed and not yet taken, so the average lands exactly on {@link #BASE_TICKS}. */
    private static double credit;

    private TickSpeed() {}

    // --- state -------------------------------------------------------------------

    public static boolean isEnabled()
    {
        return enabled;
    }

    public static void setEnabled(boolean value)
    {
        if (enabled == value)
        {
            return;
        }

        enabled = value;
        credit = 0.0D;
        save();
    }

    public static float getTicks()
    {
        return ticks;
    }

    /**
     * Sets the client's tick rate. Clamped rather than rejected, and out of range values are
     * snapped to the nearest bound, so the setting can never leave the world on a rate the
     * accumulator cannot bring back to 20 Hz.
     */
    public static void setTicks(float value)
    {
        float clamped = Float.isNaN(value) ? BASE_TICKS : Math.max(MIN_TICKS, Math.min(MAX_TICKS, value));
        boolean changed = Math.abs(clamped - ticks) > 0.0001F;

        ticks = clamped;

        if (changed)
        {
            // Start the new average from scratch rather than carrying a debt built up at the
            // old rate, which would otherwise skew the first second after the change.
            credit = 0.0D;
            save();
        }
    }

    // --- the tick split ----------------------------------------------------------

    /**
     * Points the game's own timer at the requested rate.
     *
     * <p>Called before {@code Timer.updateTimer}, which is what turns the rate into
     * {@code elapsedTicks}. Must therefore run before the timer is updated, not from inside
     * {@code runTick}, which only runs on frames that already have a tick to run.</p>
     *
     * @param timer the game loop's timer, or null off a real game
     */
    public static void applyTo(Timer timer)
    {
        if (timer == null)
        {
            return;
        }

        timer.ticksPerSecond = enabled ? ticks : BASE_TICKS;
    }

    /**
     * Whether this tick is one of the 20 world ticks per second.
     *
     * <p>Called exactly once per {@code runTick}, and only when the game is not paused, so a
     * pause neither eats the schedule nor lets a backlog build up.</p>
     *
     * @return true if the world, sound and particle work should run this tick
     */
    public static boolean isWorldTick()
    {
        if (!enabled || ticks <= BASE_TICKS)
        {
            // Vanilla, and the only case where the accumulator is not needed. Below the base
            // rate the world simply runs at the input rate, which is the slow motion.
            return true;
        }

        credit += (double)BASE_TICKS / (double)ticks;

        if (credit >= 1.0D)
        {
            credit -= 1.0D;
            return true;
        }

        return false;
    }

    // --- reporting ---------------------------------------------------------------

    /**
     * The fastest rate the game loop can actually deliver at the given frame rate.
     *
     * @param fps the measured frame rate, or zero when it is not known
     */
    public static float reachable(float fps)
    {
        return fps <= 0.0F ? MAX_TICKS : Math.min(MAX_TICKS, (float)(Math.floor(fps) * MAX_TICKS_PER_FRAME));
    }

    /**
     * Why the requested rate may not be reached, or an empty string when it should be.
     *
     * @param fps the measured frame rate, or zero when it is not known
     */
    public static String unreachable(float fps)
    {
        float limit = reachable(fps);

        return ticks > limit
            ? " The loop runs at most " + MAX_TICKS_PER_FRAME + " ticks per frame, so above "
                + trim(limit) + " at " + (int)fps + " fps you get " + trim(limit) + " instead."
            : "";
    }

    /** Drops a trailing zero, so a rate of 20 reads as 20 rather than 20.0. */
    static String trim(float value)
    {
        return value == (long)value ? String.valueOf((long)value) : String.valueOf(value);
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
        enabled = false;
        ticks = BASE_TICKS;
        credit = 0.0D;
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