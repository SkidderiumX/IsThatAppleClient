package net.minecraft.client.commands;

import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;

/**
 * Real look vector: lets the free look offset be handed to the player's own rotation, so the
 * body turns to face where the camera was pointing instead of staying where it was left.
 *
 * <p>Free look on its own never touches the body. That is what keeps walking, sprinting and the
 * rendered model honest while you aim sideways, but it also means the direction you are aiming
 * and the direction you are travelling are two different things. Committing the offset is how you
 * make the body catch up before you drop free look, which matters whenever something reads the
 * real rotation rather than the camera - attacking, the item you are holding, a packet the
 * server checks.</p>
 *
 * <p>The commit itself lives in {@link FreeLook#commitTo}, next to the offsets it moves and the
 * view it has to keep consistent with the rest of the game.</p>
 *
 * <p>Settings are stored in {@value ClientConfig#FILE_NAME} in the game directory and rewritten on
 * every change.</p>
 */
public class LookVector
{
    /**
     * Master switch. On by default, unlike the display mods.
     *
     * <p>Nothing here acts on its own: the only way to turn the body is to type the command. A
     * default of off would mean the command silently did nothing the first time anyone tried it,
     * and the one thing this mod does is not something to happen by surprise.</p>
     */
    private static boolean enabled = true;

    private LookVector() {}

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

    // --- committing --------------------------------------------------------------

    /**
     * Why a commit is not available right now, or {@link #OK} when it is.
     *
     * <p>Kept as a distinct value per case rather than a boolean so the command can tell the
     * player which one it is. "Nothing banked" and "free look is off" need different advice, and
     * an unexplained no-op is the one outcome worth avoiding here.</p>
     */
    public enum Availability
    {
        OK,
        DISABLED,
        FREE_LOOK_OFF,
        NO_PLAYER,
        NOTHING_BANKED
    }

    /**
     * Turns the body to match the free look camera.
     *
     * <p>Safe to call with no game running, which is what the headless tests look like.</p>
     *
     * @return {@link Availability#OK}, or the reason nothing was committed
     */
    public static Availability apply(Minecraft mc)
    {
        if (!enabled)
        {
            return Availability.DISABLED;
        }

        if (mc == null || mc.thePlayer == null)
        {
            return Availability.NO_PLAYER;
        }

        if (!FreeLook.isEnabled())
        {
            return Availability.FREE_LOOK_OFF;
        }

        EntityPlayerSP player = mc.thePlayer;

        if (!FreeLook.commitTo(player))
        {
            return Availability.NOTHING_BANKED;
        }

        return Availability.OK;
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

    /** Restores the default in memory. */
    static void resetToDefaults()
    {
        enabled = true;
    }

    /** Restores the default and rewrites the config file. */
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