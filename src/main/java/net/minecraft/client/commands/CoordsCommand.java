package net.minecraft.client.commands;

import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

/**
 * /coords [on|off] [size &lt;0.5-3&gt;] [pos &lt;x&gt; &lt;y&gt;] [reset]
 *
 * <p>Draws the player's coordinates and look direction over the game, every value to
 * {@value CoordsHud#DECIMALS} decimal places.</p>
 *
 * <p>Settings are stored in {@value ClientConfig#FILE_NAME} in the game directory and rewritten on
 * every change.</p>
 */
public class CoordsCommand implements ClientCommand
{
    public String getName()
    {
        return "coords";
    }

    public List<String> getAliases()
    {
        return Arrays.asList("xyz", "position");
    }

    public String getUsage()
    {
        return "/coords [on|off] | /coords size <" + trim(CoordsHud.getMinSize()) + "-"
            + trim(CoordsHud.getMaxSize()) + "> | /coords pos <x> <y> | /coords reset";
    }

    public boolean execute(Minecraft mc, String[] args)
    {
        if (args.length == 0)
        {
            CoordsHud.setEnabled(!CoordsHud.isEnabled());
            report(mc);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("size"))
        {
            return size(mc, args);
        }

        if (sub.equals("pos") || sub.equals("position"))
        {
            return pos(mc, args);
        }

        if (sub.equals("reset"))
        {
            if (args.length > 1)
            {
                reply(mc, "Usage: " + getUsage(), EnumChatFormatting.RED);
                return true;
            }

            CoordsHud.reset();
            reply(mc, "Position HUD reset to defaults. " + CoordsHud.getConfigFile().getName() + " rewritten.",
                EnumChatFormatting.GREEN);
            report(mc);
            return true;
        }

        if (sub.equals("help"))
        {
            reply(mc, getUsage(), EnumChatFormatting.AQUA);
            return true;
        }

        if (args.length > 1)
        {
            reply(mc, "Usage: " + getUsage(), EnumChatFormatting.RED);
            return true;
        }

        if (sub.equals("on") || sub.equals("true"))
        {
            CoordsHud.setEnabled(true);
        }
        else if (sub.equals("off") || sub.equals("false"))
        {
            CoordsHud.setEnabled(false);
        }
        else
        {
            reply(mc, "\"" + args[0] + "\" is not on or off. Usage: " + getUsage(), EnumChatFormatting.RED);
            return true;
        }

        report(mc);
        return true;
    }

    private boolean size(Minecraft mc, String[] args)
    {
        if (args.length != 2)
        {
            reply(mc, "Usage: /coords size <" + trim(CoordsHud.getMinSize()) + "-"
                + trim(CoordsHud.getMaxSize()) + ">", EnumChatFormatting.RED);
            return true;
        }

        float parsed;

        try
        {
            parsed = Float.parseFloat(args[1]);
        }
        catch (NumberFormatException exception)
        {
            reply(mc, "\"" + args[1] + "\" is not a number.", EnumChatFormatting.RED);
            return true;
        }

        CoordsHud.setSize(parsed);

        // A NaN or an out-of-range value is clamped rather than rejected, so echo what stuck.
        reply(mc, "Text scale " + trim(CoordsHud.getSize()) + ".", EnumChatFormatting.GREEN);
        return true;
    }

    private boolean pos(Minecraft mc, String[] args)
    {
        if (args.length == 1)
        {
            reply(mc, "Current: " + CoordsHud.getPosX() + ", " + CoordsHud.getPosY()
                + "  (negative anchors to the far edge)", EnumChatFormatting.AQUA);
            return true;
        }

        if (args.length != 3)
        {
            reply(mc, "Usage: /coords pos <x> <y>", EnumChatFormatting.RED);
            return true;
        }

        int x;
        int y;

        try
        {
            x = Integer.parseInt(args[1]);
            y = Integer.parseInt(args[2]);
        }
        catch (NumberFormatException exception)
        {
            reply(mc, "Position must be two whole numbers.", EnumChatFormatting.RED);
            return true;
        }

        CoordsHud.setPos(x, y);
        reply(mc, "Position set to " + x + ", " + y + ".", EnumChatFormatting.GREEN);
        return true;
    }

    private void report(Minecraft mc)
    {
        boolean on = CoordsHud.isEnabled();
        reply(mc, "Position HUD " + (on ? "on" : "off") + "."
            + (on ? "  X/Y/Z and yaw/pitch to " + CoordsHud.DECIMALS + " decimals." : ""),
            on ? EnumChatFormatting.GREEN : EnumChatFormatting.YELLOW);

        if (on)
        {
            reply(mc, "  " + CoordsHud.getConfigFile().getName(), EnumChatFormatting.GRAY);
        }
    }

    /** Drops a trailing zero so a scale of 1.0 reads as 1. */
    static String trim(float value)
    {
        return value == (long)value ? String.valueOf((long)value) : String.valueOf(value);
    }

    private void reply(Minecraft mc, String text, EnumChatFormatting colour)
    {
        mc.ingameGUI.getChatGUI().printChatMessage(new ChatComponentText(colour + text));
    }
}