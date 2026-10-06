package net.minecraft.client.commands;

import java.io.File;
import net.minecraft.world.World;

/**
 * Time changer: pins the client's clock to a chosen time of day, so the sun and the light stay
 * where they are put instead of following the server.
 *
 * <p>Only the client is touched. The server keeps its own time and keeps sending it, but every
 * client tick overwrites the local copy before anything is rendered, so the sky, the light and
 * anything else drawn from the world time hold still. Turning the mod off hands the clock straight
 * back to the server, with no resync needed because the server's time was never lost.</p>
 *
 * <p>Settings are stored in {@value ClientConfig#FILE_NAME} in the game directory and rewritten on
 * every change.</p>
 */
public class TimeChanger
{
    /** Start of the day, just after sunrise. */
    public static final long SUNRISE = 0L;

    /** Morning, the vanilla {@code /time set day}. */
    public static final long DAY = 1000L;

    /** Midday, the vanilla {@code /time set noon}. */
    public static final long NOON = 6000L;

    /** Sunset, the vanilla {@code /time set sunset}. */
    public static final long SUNSET = 12000L;

    /** Just after sunset, the vanilla {@code /time set night}. */
    public static final long NIGHT = 13000L;

    /** Midnight, the vanilla {@code /time set midnight}. */
    public static final long MIDNIGHT = 18000L;

    /** Smallest time of day offered, in ticks. */
    public static final long MIN_TIME = 0L;

    /** Largest time of day offered, in ticks. One full day is 24000 ticks. */
    public static final long MAX_TIME = 23999L;

    private static boolean enabled;

    private static long time = NOON;

    private TimeChanger() {}

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
        save();
    }

    public static long getTime()
    {
        return time;
    }

    /**
     * Sets the time of day the client is pinned to, in ticks. Clamped rather than rejected, so
     * the value is always usable.
     */
    public static void setTime(long value)
    {
        long clamped = Math.max(MIN_TIME, Math.min(MAX_TIME, value));

        if (clamped == time)
        {
            return;
        }

        time = clamped;
        save();
    }

    // --- the override ------------------------------------------------------------

    /**
     * Pins the world's clock, if the mod is on.
     *
     * <p>Called once per client tick from where the world is updated, and after the server's own
     * time has been applied, so the server's packets cannot move the clock between here and the
     * next render.</p>
     *
     * @param world the client world, or null when none is loaded
     */
    public static void applyTo(World world)
    {
        if (enabled && world != null)
        {
            world.setWorldTime(time);
        }
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
        time = NOON;
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
