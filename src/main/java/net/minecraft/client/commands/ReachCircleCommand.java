package net.minecraft.client.commands;

import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

/**
 * /reachcircle [on|off] [reset]
 *
 * <p>Draws a ring on the ground at the player's feet, three blocks out, so the distance they can
 * reach is visible without having to judge it by eye.</p>
 *
 * <p>Settings are stored in {@value ClientConfig#FILE_NAME} in the game directory and rewritten on
 * every change.</p>
 */
public class ReachCircleCommand implements ClientCommand
{
    public String getName()
    {
        return "reachcircle";
    }

    public List<String> getAliases()
    {
        return Arrays.asList("reach");
    }

    public String getUsage()
    {
        return "/reachcircle [on|off] [reset]";
    }

    public boolean execute(Minecraft mc, String[] args)
    {
        if (args.length == 0)
        {
            ReachCircle.setEnabled(!ReachCircle.isEnabled());
            report(mc);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("reset"))
        {
            if (args.length > 1)
            {
                reply(mc, "Usage: " + getUsage(), EnumChatFormatting.RED);
                return true;
            }

            ReachCircle.reset();
            report(mc);
            return true;
        }

        if (args.length > 1)
        {
            reply(mc, "Usage: " + getUsage(), EnumChatFormatting.RED);
            return true;
        }

        if (sub.equals("on") || sub.equals("true"))
        {
            ReachCircle.setEnabled(true);
        }
        else if (sub.equals("off") || sub.equals("false"))
        {
            ReachCircle.setEnabled(false);
        }
        else
        {
            reply(mc, "\"" + args[0] + "\" is not on or off. Usage: " + getUsage(), EnumChatFormatting.RED);
            return true;
        }

        report(mc);
        return true;
    }

    private void report(Minecraft mc)
    {
        boolean on = ReachCircle.isEnabled();
        reply(mc, "Reach circle " + (on ? "on" : "off") + "."
            + (on ? "  A " + format(ReachCircle.RADIUS) + " block ring at your feet." : ""),
            on ? EnumChatFormatting.GREEN : EnumChatFormatting.YELLOW);

        if (on)
        {
            reply(mc, "  " + ReachCircle.getConfigFile().getName(), EnumChatFormatting.GRAY);
        }
    }

    /** Drops a trailing zero so the radius reads as 3 rather than 3.0. */
    private static String format(float value)
    {
        return value == (long)value ? String.valueOf((long)value) : String.valueOf(value);
    }

    private void reply(Minecraft mc, String text, EnumChatFormatting colour)
    {
        mc.ingameGUI.getChatGUI().printChatMessage(new ChatComponentText(colour + text));
    }
}