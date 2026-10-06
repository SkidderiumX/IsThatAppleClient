package net.minecraft.client.commands;

import java.io.File;

/**
 * No hit delay: removes the click throttle vanilla puts between hits.
 *
 * <p>Every click that does not hit anything sets a 10 tick cooldown before the next click is
 * read, so the fastest vanilla click rate is one hit every half second whenever a swing misses.
 * While this is on that cooldown is never armed and every click goes straight through, up to one
 * per tick however fast the loop runs.</p>
 *
 * <p>Nothing else changes: the swing, the reach and what a hit does are all vanilla's. Turning
 * the mod off hands the throttle straight back.</p>
 *
 * <p>Settings are stored in {@value ClientConfig#FILE_NAME} in the game directory and rewritten on
 * every change.</p>
 */
public class NoHitDelay
{
    private static boolean enabled = true;

    private NoHitDelay() {}

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
