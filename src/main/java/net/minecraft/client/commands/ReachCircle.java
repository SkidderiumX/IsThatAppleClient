package net.minecraft.client.commands;

import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;

/**
 * Reach circle: a ring drawn on the ground at the player's feet, showing the distance they can
 * reach.
 *
 * <p>Drawn from {@link #render} once the world has finished rendering, which puts it over the
 * terrain instead of through it, and keeps it occluded by whatever stands in front of it.</p>
 *
 * <p>The ring is a flat annulus rather than a line loop. A one-pixel line lying on the ground
 * shimmers badly as the camera turns, and the driver is free to clamp the width to a single
 * pixel anyway, whereas a band with a real width in blocks reads the same from every angle and
 * costs the same to draw.</p>
 *
 * <p>Settings are stored in {@value ClientConfig#FILE_NAME} in the game directory and rewritten on
 * every change.</p>
 */
public class ReachCircle
{
    /**
     * Radius in blocks, measured on the ground around the player.
     *
     * <p>Three blocks, the classic survival reach. Note this is not read from the player
     * controller: in this build {@code PlayerControllerMP.getBlockReachDistance} answers 4.5 in
     * survival and 5 in creative, because it measures from the eye rather than the feet, so
     * driving the circle from it would not produce the ring this mod is named for.</p>
     */
    public static final float RADIUS = 3.0F;

    /** Half the width of the drawn band, in blocks. */
    private static final float HALF_THICKNESS = 0.05F;

    /** Straight edges around the ring. 96 keeps each facet well under a tenth of a block. */
    private static final int SEGMENTS = 96;

    /** Lifts the band clear of the block it rests on, so the two surfaces cannot z-fight. */
    private static final double LIFT = 0.02D;

    private static final float ALPHA = 0.8F;

    private static boolean enabled;

    private ReachCircle() {}

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

    // --- rendering ---------------------------------------------------------------

    /**
     * Draws the ring around the player, if the mod is on.
     *
     * <p>Safe to call with no world loaded, which is what the headless tests look like.</p>
     *
     * @param partialTicks interpolation factor between the last tick and this one
     */
    public static void render(float partialTicks)
    {
        if (!enabled)
        {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();

        if (mc == null || mc.thePlayer == null || mc.theWorld == null)
        {
            return;
        }

        EntityPlayerSP player = mc.thePlayer;
        double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * (double)partialTicks;
        double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * (double)partialTicks;
        double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * (double)partialTicks;

        float inner = Math.max(0.01F, RADIUS - HALF_THICKNESS);
        float outer = RADIUS + HALF_THICKNESS;

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableTexture2D();
        // The band is walked inside out, so which way the triangles face depends on the winding.
        // Drawing both sides is simpler than getting that right and costs nothing at this size.
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);

        renderer.begin(5, DefaultVertexFormats.POSITION_COLOR);

        // One turn more than the ring actually needs closes it: the strip runs all the way round
        // and then repeats the first pair of vertices.
        for (int i = 0; i <= SEGMENTS; ++i)
        {
            double angle = Math.PI * 2.0D * i / SEGMENTS;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);

            // Offsets rather than setTranslation, which is how the selection box draws its own
            // world space geometry.
            renderer.pos(dx * inner - x, LIFT - y, dz * inner - z).color(1.0F, 1.0F, 1.0F, ALPHA).endVertex();
            renderer.pos(dx * outer - x, LIFT - y, dz * outer - z).color(1.0F, 1.0F, 1.0F, ALPHA).endVertex();
        }

        tessellator.draw();

        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
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
        enabled = false;
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