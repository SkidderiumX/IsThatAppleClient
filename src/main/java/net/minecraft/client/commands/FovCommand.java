package net.minecraft.client.commands;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

/**
 * /fov &lt;value&gt; - changes the field of view.
 *
 * <p>/fov with no argument reports the current value and /fov reset restores the
 * vanilla default of 70.</p>
 */
public class FovCommand implements ClientCommand
{
    private static final float MIN_FOV = 1.0F;
    private static final float MAX_FOV = 179.0F;
    private static final float DEFAULT_FOV = 70.0F;

    public String getName()
    {
        return "fov";
    }

    public String getUsage()
    {
        return "/fov <1-179> | /fov reset";
    }

    public boolean execute(Minecraft mc, String[] args)
    {
        GameSettings settings = mc.gameSettings;

        if (args.length == 0)
        {
            reply(mc, "FOV is " + format(settings.getOptionFloatValue(GameSettings.Options.FOV)) + ".", EnumChatFormatting.AQUA);
            return true;
        }

        if (args.length > 1)
        {
            reply(mc, "Usage: " + getUsage(), EnumChatFormatting.RED);
            return true;
        }

        if (args[0].equalsIgnoreCase("reset"))
        {
            settings.setOptionFloatValue(GameSettings.Options.FOV, DEFAULT_FOV);
            reply(mc, "FOV reset to " + format(DEFAULT_FOV) + ".", EnumChatFormatting.GREEN);
            return true;
        }

        float requested;

        try
        {
            requested = Float.parseFloat(args[0]);
        }
        catch (NumberFormatException var4)
        {
            reply(mc, "\"" + args[0] + "\" is not a number. Usage: " + getUsage(), EnumChatFormatting.RED);
            return true;
        }

        // Float.parseFloat happily accepts "NaN" and "Infinity"; letting those through
        // would poison the projection matrix and black-screen the client.
        if (Float.isNaN(requested) || Float.isInfinite(requested))
        {
            reply(mc, "\"" + args[0] + "\" is not a usable value. Usage: " + getUsage(), EnumChatFormatting.RED);
            return true;
        }

        float applied = Math.max(MIN_FOV, Math.min(MAX_FOV, requested));
        settings.setOptionFloatValue(GameSettings.Options.FOV, applied);

        if (applied != requested)
        {
            reply(mc, "FOV clamped to " + format(applied) + " (allowed range is " + format(MIN_FOV) + "-" + format(MAX_FOV) + ").", EnumChatFormatting.YELLOW);
        }
        else
        {
            reply(mc, "FOV set to " + format(applied) + ".", EnumChatFormatting.GREEN);
        }

        return true;
    }

    private void reply(Minecraft mc, String text, EnumChatFormatting colour)
    {
        mc.ingameGUI.getChatGUI().printChatMessage(new ChatComponentText(colour + text));
    }

    /** Formats without a trailing ".0" so "/fov 90" reports "90" rather than "90.0". */
    private static String format(float value)
    {
        if (value == Math.round(value))
        {
            return String.valueOf((long) value);
        }

        return String.valueOf(Math.round(value * 100.0F) / 100.0F);
    }
}