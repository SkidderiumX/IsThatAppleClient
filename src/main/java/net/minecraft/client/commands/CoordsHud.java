package net.minecraft.client.commands;

import java.io.File;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;

/**
 * Position HUD: the player's coordinates and look direction, drawn over the game.
 *
 * <p>Separate from the F3 screen, which rounds to two decimals and spends most of its width on
 * other things. This shows every value to {@value #DECIMALS} decimal places, which is deep enough
 * to see a single tick of movement or a fraction of a degree of look drift.</p>
 *
 * <p>How much of that is real depends on the field being read, and the two are not the same:</p>
 *
 * <ul>
 *   <li>{@code Entity.posX/posY/posZ} are doubles, so at ordinary coordinates they resolve far
 *       finer than a tenth of a billionth of a block. Those digits are genuine.</li>
 *   <li>{@code rotationYaw} and {@code rotationPitch} are floats. A float carries about seven
 *       significant decimal digits, so past roughly the fifth decimal place of a yaw near 180 the
 *       readout is showing rounding, not measurement. Printing ten places is still the honest
 *       thing here: it shows the float's actual granularity instead of hiding it behind a shorter
 *       number that looks more precise than it is.</li>
 * </ul>
 *
 * <p>Values are read raw rather than interpolated between ticks, so they step at the tick rate
 * like vanilla's own coordinate display does, and they match the server's view of the player.</p>
 *
 * <p>Settings are stored in {@value ClientConfig#FILE_NAME} in the game directory and rewritten on
 * every change.</p>
 */
public class CoordsHud
{
    /** Decimal places shown for every value, i.e. a resolution of 1e-10. */
    public static final int DECIMALS = 10;

    private static final float MIN_SIZE = 0.5F;
    private static final float MAX_SIZE = 3.0F;

    private static boolean enabled;

    /** Text scale factor; 1.0 is the normal GUI font size. */
    private static float size = 1.0F;

    /** Top-left corner in scaled GUI pixels. Negative values anchor to the far edge. */
    private static int posX = 4;

    /**
     * Sits one line below the FPS readout's default spot, so the two do not overlap when both are
     * left at their defaults.
     */
    private static int posY = 14;

    private CoordsHud() {}

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

    public static float getSize()
    {
        return size;
    }

    /** Smallest accepted text scale. */
    public static float getMinSize()
    {
        return MIN_SIZE;
    }

    /** Largest accepted text scale. */
    public static float getMaxSize()
    {
        return MAX_SIZE;
    }

    public static void setSize(float value)
    {
        if (Float.isNaN(value) || Float.isInfinite(value))
        {
            size = 1.0F;
        }
        else
        {
            size = Math.max(MIN_SIZE, Math.min(MAX_SIZE, value));
        }

        save();
    }

    public static int getPosX()
    {
        return posX;
    }

    public static int getPosY()
    {
        return posY;
    }

    public static void setPos(int x, int y)
    {
        posX = x;
        posY = y;
        save();
    }

    // --- text --------------------------------------------------------------------

    /**
     * Formats one value at full precision.
     *
     * <p>{@link Locale#ROOT} is not optional. The default locale decides the decimal separator, so
     * on a machine set to a language that uses a comma every value here would read as a single
     * comma-separated field.</p>
     */
    static String number(double value)
    {
        if (Double.isNaN(value) || Double.isInfinite(value))
        {
            return Double.isNaN(value) ? "NaN" : (value > 0.0D ? "Inf" : "-Inf");
        }

        return String.format(Locale.ROOT, "%." + DECIMALS + "f", value);
    }

    /**
     * Yaw folded into a single turn.
     *
     * <p>Minecraft lets yaw grow without bound, so a long session would otherwise drift to four or
     * five digits before the sign. The fold costs no precision: it subtracts whole turns.</p>
     */
    static float wrapYaw(float value)
    {
        value = value % 360.0F;

        if (value >= 180.0F)
        {
            value -= 360.0F;
        }
        else if (value < -180.0F)
        {
            value += 360.0F;
        }

        return value;
    }

    /** Builds the two readout lines for the given player. */
    static String build(EntityPlayerSP player)
    {
        return "XYZ: " + number(player.posX) + " / " + number(player.posY) + " / " + number(player.posZ)
            + "\nYaw: " + number(wrapYaw(player.rotationYaw))
            + "  Pitch: " + number(player.rotationPitch);
    }

    // --- rendering ---------------------------------------------------------------

    /**
     * Draws the readout. Called from the game overlay each frame while enabled.
     *
     * <p>Returns without touching GL when there is no player, which is what the headless tests and
     * the main menu rely on.</p>
     */
    public static void render(Minecraft mc, ScaledResolution resolution, float partialTicks)
    {
        if (!enabled || mc == null || mc.ingameGUI == null || mc.thePlayer == null)
        {
            return;
        }

        String text = build(mc.thePlayer);
        FontRenderer font = mc.ingameGUI.getFontRenderer();

        // Negative coordinates anchor to the right/bottom edge so the readout stays put when the
        // window is resized.
        int anchorX = posX < 0 ? resolution.getScaledWidth() + posX : posX;
        int anchorY = posY < 0 ? resolution.getScaledHeight() + posY : posY;

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.translate((float)anchorX, (float)anchorY, 0.0F);
        GlStateManager.scale(size, size, size);
        font.drawStringWithShadow(text, 0.0F, 0.0F, 0xFFFFFF);
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
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

    /** Restores every value to its default in memory. */
    static void resetToDefaults()
    {
        enabled = false;
        size = 1.0F;
        posX = 4;
        posY = 14;
    }

    /** Restores every value to its default and rewrites the config file. */
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