package net.minecraft.client.commands;

import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;

/**
 * Standalone FPS readout drawn over the game.
 *
 * <p>Kept separate from the F3 debug screen so it can be positioned and scaled freely.
 * Config lives here rather than in {@code GameSettings} because these are display
 * preferences with no bearing on gameplay. State is persisted in
 * {@value ClientConfig#FILE_NAME} in the game directory, alongside every other mod setting,
 * and written on every change, so nothing is lost to a crash.</p>
 */
public final class FpsOverlay
{
    public enum Mode
    {
        /** Vanilla behaviour: a whole-number count sampled once per second. */
        NORMAL,
        /** Interpolated between frames, so the number moves instead of jumping. */
        SMOOTH
    }

    private static final float MIN_SIZE = 0.5F;
    private static final float MAX_SIZE = 8.0F;

    private static boolean enabled;
    private static Mode mode = Mode.NORMAL;

    /** Text scale factor; 1.0 is the normal GUI font size. */
    private static float size = 1.0F;

    /** Top-left corner in scaled GUI pixels. Negative values anchor to the far edge. */
    private static int posX = 4;
    private static int posY = 4;

    /** Opacity, 0-255. The overlay is a 2D surface, so the third axis is used as alpha. */
    private static int alpha = 255;

    /**
     * When true the readout is coloured green/yellow/red by frame rate and
     * {@link #colour} is ignored. Set false by {@code /fps color <hex>}.
     */
    private static boolean autoColour = true;

    /** Fixed text colour as 0xRRGGBB, used only while {@link #autoColour} is false. */
    private static int colour = 0xFFFFFF;

    /** Whether to draw the 1px offset drop shadow behind the glyphs. */
    private static boolean shadow = true;

    private static int lastFrameCounter;
    private static long lastFrameTimeNanos;
    private static double smoothedFps = -1.0D;

    private FpsOverlay() {}

    public static boolean isEnabled()
    {
        return enabled;
    }

    public static void setEnabled(boolean value)
    {
        enabled = value;
        resetSmoothing();
        save();
    }

    public static void toggle()
    {
        setEnabled(!enabled);
    }

    public static Mode getMode()
    {
        return mode;
    }

    /** @return the mode name now active, so the caller can echo it back */
    public static String setMode(String requested)
    {
        Mode parsed = Mode.valueOf(requested.toUpperCase());
        mode = parsed;
        resetSmoothing();
        save();
        return mode.name().toLowerCase();
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
        size = clampSize(value);
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

    public static int getAlpha()
    {
        return alpha;
    }

    public static void setPos(int x, int y, int opacity)
    {
        posX = x;
        posY = y;
        alpha = Math.max(0, Math.min(255, opacity));
        save();
    }

    public static boolean isAutoColour()
    {
        return autoColour;
    }

    public static int getColour()
    {
        return colour;
    }

    /**
     * Pins the readout to one colour, turning the frame-rate colour coding off.
     *
     * @param rgb 0xRRGGBB
     */
    public static void setColour(int rgb)
    {
        colour = rgb & 0xFFFFFF;
        autoColour = false;
        save();
    }

    /**
     * Sets the fixed colour without touching {@link #autoColour}.
     *
     * <p>Split out because the two keys have to be applied independently when the config file
     * is read. Writing them through {@link #setColour} would clear {@code autoColour} on every
     * load, which is exactly the bug the "colour present without autoColour" fixup guards.</p>
     *
     * @param rgb 0xRRGGBB
     */
    static void setColourOnly(int rgb)
    {
        colour = rgb & 0xFFFFFF;
        save();
    }

    /** Turns the frame-rate colouring on or off without clearing {@link #colour}. */
    static void setAutoColour(boolean on)
    {
        autoColour = on;
        save();
    }

    /** Restores the green/yellow/red frame-rate colouring. */
    public static void setAutoColour()
    {
        setAutoColour(true);
    }

    public static boolean isShadow()
    {
        return shadow;
    }

    public static void setShadow(boolean value)
    {
        shadow = value;
        save();
    }

    /** Frame-rate colour coding, ignored once a fixed colour is set. */
    private static int autoColourFor(double fps)
    {
        if (fps < 0.0D)
        {
            return 0xFFFFFF;
        }

        return fps >= 55.0D ? 0x55FF55 : (fps >= 25.0D ? 0xFFFF55 : 0xFF5555);
    }

    /** Drops the smoothing history so a mode or size change starts from a clean slate. */
    private static void resetSmoothing()
    {
        lastFrameCounter = 0;
        lastFrameTimeNanos = 0L;
        smoothedFps = -1.0D;
    }

    private static float clampSize(float value)
    {
        if (Float.isNaN(value) || Float.isInfinite(value))
        {
            return 1.0F;
        }

        return Math.max(MIN_SIZE, Math.min(MAX_SIZE, value));
    }

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

    /**
     * Restores every value to its default and rewrites the config file.
     *
     * <p>Public because {@code /fps reset} exposes it.</p>
     */
    public static void reset()
    {
        resetToDefaults();
        resetSmoothing();
        save();
    }

    /** Restores defaults in memory. Does not write the file; see {@link ClientConfig#load()}. */
    static void resetToDefaults()
    {
        enabled = false;
        mode = Mode.NORMAL;
        size = 1.0F;
        posX = 4;
        posY = 4;
        alpha = 255;
        autoColour = true;
        colour = 0xFFFFFF;
        shadow = true;
    }

    /**
     * Parses {@code #RRGGBB} or bare {@code RRGGBB}.
     *
     * @param fallback returned when the text is not exactly six hex digits
     * @return the 0xRRGGBB value, or {@code fallback}
     */
    static int parseHexOr(String value, int fallback)
    {
        String text = value.trim();

        if (text.startsWith("#"))
        {
            text = text.substring(1);
        }

        if (text.length() != 6)
        {
            return fallback;
        }

        try
        {
            return Integer.parseInt(text, 16) & 0xFFFFFF;
        }
        catch (NumberFormatException exception)
        {
            return fallback;
        }
    }

    /** Writes the config file. Never throws. */
    public static void save()
    {
        ClientConfig.save();
    }

    /**
     * Instantaneous FPS from frames rendered since the previous call.
     *
     * <p>Uses the same {@code fpsCounter} Minecraft increments once per frame that the
     * F3 line is derived from, so this updates every frame rather than once a second.</p>
     */
    private static double instantFps(Minecraft mc)
    {
        int frames = mc.getFpsCounter() - lastFrameCounter;
        long now = System.nanoTime();

        if (lastFrameTimeNanos != 0L && frames > 0)
        {
            double seconds = (double)(now - lastFrameTimeNanos) / 1.0E9D;

            if (seconds > 0.0D)
            {
                double fps = (double)frames / seconds;

                // Exponential moving average, so the readout eases rather than flickers.
                smoothedFps = smoothedFps < 0.0D ? fps : smoothedFps + (fps - smoothedFps) * 0.1D;
            }
        }

        lastFrameCounter = mc.getFpsCounter();
        lastFrameTimeNanos = now;
        return smoothedFps;
    }

    private static String format(double fps)
    {
        if (fps < 0.0D)
        {
            return "-- FPS";
        }

        return mode == Mode.SMOOTH ? String.format("%.1f FPS", fps) : (int)(fps + 0.5D) + " FPS";
    }

    /**
     * Draws the readout. Called from the game overlay each frame while enabled.
     */
    public static void render(Minecraft mc, ScaledResolution resolution, float partialTicks)
    {
        if (!enabled || mc.ingameGUI == null)
        {
            return;
        }

        double fps = mode == Mode.SMOOTH ? instantFps(mc) : mc.getDebugFPS();
        String text = format(fps);

        FontRenderer font = mc.ingameGUI.getFontRenderer();
        int rgb = autoColour ? autoColourFor(fps) : colour;
        int argb = (alpha << 24) | rgb;

        // Negative coordinates anchor to the right/bottom edge so the readout stays
        // put when the window is resized.
        int anchorX = posX < 0 ? resolution.getScaledWidth() + posX : posX;
        int anchorY = posY < 0 ? resolution.getScaledHeight() + posY : posY;

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.translate((float)anchorX, (float)anchorY, 0.0F);
        GlStateManager.scale(size, size, size);

        // drawStringWithShadow does the shadow and the glyphs in one pass, so this is a
        // choice rather than an addition - drawing both would render the text twice.
        if (shadow)
        {
            font.drawStringWithShadow(text, 0.0F, 0.0F, argb);
        }
        else
        {
            font.drawString(text, 0.0F, 0.0F, argb, false);
        }

        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    /** Exposes the drawn width/height so the command can report the effective size. */
    public static int[] getRenderedSize(Minecraft mc)
    {
        FontRenderer font = mc.ingameGUI.getFontRenderer();
        // Widest string each mode can produce, so the reported box never understates.
        String widest = mode == Mode.SMOOTH ? "000.0 FPS" : "000 FPS";
        int w = font.getStringWidth(widest);
        int h = font.FONT_HEIGHT;
        return new int[] {(int)((float)w * size), (int)((float)h * size)};
    }
}
