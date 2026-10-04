package net.minecraft.client.commands;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

/**
 * /fps [mode smooth|normal] [size <factor>] [pos <x> <y> [alpha]]
 *
 * <p>Bare {@code /fps} toggles the readout. Subcommands adjust it in place:
 * {@code mode} picks how the number is sampled, {@code size} scales the text, and
 * {@code pos} moves it. Negative x/y anchor to the right/bottom edge.</p>
 */
public class FpsCommand implements ClientCommand
{
    public String getName()
    {
        return "fps";
    }

    public String getUsage()
    {
        return "/fps | /fps mode <smooth|normal> | /fps size <" + trim(FpsOverlay.getMinSize())
            + "-" + trim(FpsOverlay.getMaxSize()) + "> | /fps pos <x> <y> [alpha]"
            + " | /fps color <#RRGGBB|auto> | /fps shadow <on|off> | /fps reset";
    }

    public boolean execute(Minecraft mc, String[] args)
    {
        if (args.length == 0)
        {
            FpsOverlay.toggle();
            reply(mc, "FPS display " + (FpsOverlay.isEnabled() ? "enabled" : "disabled") + ".", FpsOverlay.isEnabled() ? EnumChatFormatting.GREEN : EnumChatFormatting.YELLOW);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("mode"))
        {
            return mode(mc, args);
        }

        if (sub.equals("size"))
        {
            return size(mc, args);
        }

        if (sub.equals("pos"))
        {
            return pos(mc, args);
        }

        if (sub.equals("reset"))
        {
            FpsOverlay.reset();
            reply(mc, "FPS display reset to defaults. " + FpsOverlay.getConfigFile().getName() + " rewritten.", EnumChatFormatting.GREEN);
            return true;
        }

        if (sub.equals("color") || sub.equals("colour"))
        {
            return colour(mc, args);
        }

        if (sub.equals("shadow"))
        {
            return shadow(mc, args);
        }

        if (sub.equals("help"))
        {
            reply(mc, getUsage(), EnumChatFormatting.AQUA);
            return true;
        }

        reply(mc, "Unknown option \"" + args[0] + "\". Usage: " + getUsage(), EnumChatFormatting.RED);
        return true;
    }

    private boolean mode(Minecraft mc, String[] args)
    {
        if (args.length != 2)
        {
            reply(mc, "Usage: /fps mode <smooth|normal>", EnumChatFormatting.RED);
            return true;
        }

        String requested = args[1].toLowerCase();

        if (!requested.equals("smooth") && !requested.equals("normal"))
        {
            reply(mc, "Mode must be \"smooth\" or \"normal\".", EnumChatFormatting.RED);
            return true;
        }

        String applied = FpsOverlay.setMode(requested);
        reply(mc, "FPS mode set to " + applied + ".", EnumChatFormatting.GREEN);
        return true;
    }

    private boolean size(Minecraft mc, String[] args)
    {
        float min = FpsOverlay.getMinSize();
        float max = FpsOverlay.getMaxSize();

        if (args.length != 2)
        {
            reply(mc, "Usage: /fps size <" + trim(min) + "-" + trim(max) + ">", EnumChatFormatting.RED);
            return true;
        }

        float parsed;

        try
        {
            parsed = Float.parseFloat(args[1]);
        }
        catch (NumberFormatException var5)
        {
            reply(mc, "\"" + args[1] + "\" is not a number.", EnumChatFormatting.RED);
            return true;
        }

        if (Float.isNaN(parsed) || Float.isInfinite(parsed))
        {
            reply(mc, "\"" + args[1] + "\" is not a usable value.", EnumChatFormatting.RED);
            return true;
        }

        float clamped = Math.max(min, Math.min(max, parsed));
        FpsOverlay.setSize(clamped);

        if (clamped != parsed)
        {
            reply(mc, "Size clamped to " + trim(clamped) + " (allowed " + trim(min) + "-" + trim(max) + ").", EnumChatFormatting.YELLOW);
        }
        else
        {
            int[] rendered = FpsOverlay.getRenderedSize(mc);
            reply(mc, "Size set to " + trim(clamped) + " (" + rendered[0] + "x" + rendered[1] + " px).", EnumChatFormatting.GREEN);
        }

        return true;
    }

    private boolean pos(Minecraft mc, String[] args)
    {
        if (args.length != 3 && args.length != 4)
        {
            reply(mc, "Usage: /fps pos <x> <y> [alpha]", EnumChatFormatting.RED);
            reply(mc, "Negative x/y anchors to the far edge. Alpha is 0-255.", EnumChatFormatting.AQUA);
            return true;
        }

        int x;
        int y;
        int opacity = FpsOverlay.getAlpha();

        try
        {
            x = Integer.parseInt(args[1]);
            y = Integer.parseInt(args[2]);

            if (args.length == 4)
            {
                opacity = Integer.parseInt(args[3]);
            }
        }
        catch (NumberFormatException var5)
        {
            reply(mc, "Position must be whole numbers.", EnumChatFormatting.RED);
            return true;
        }

        if (opacity < 0 || opacity > 255)
        {
            reply(mc, "Alpha must be between 0 and 255 (got " + opacity + ").", EnumChatFormatting.RED);
            return true;
        }

        FpsOverlay.setPos(x, y, opacity);

        int[] rendered = FpsOverlay.getRenderedSize(mc);
        reply(mc, "Position set to " + x + ", " + y + ", alpha " + opacity
            + "  (" + rendered[0] + "x" + rendered[1] + " px)", EnumChatFormatting.GREEN);
        return true;
    }

    private static String trim(float value)
    {
        if (value == Math.round(value))
        {
            return String.valueOf((long)value);
        }

        return String.valueOf(value);
    }

    private boolean colour(Minecraft mc, String[] args)
    {
        if (args.length != 2)
        {
            reply(mc, "Usage: /fps color <#RRGGBB|auto>", EnumChatFormatting.RED);
            reply(mc, "Current: " + describeColour(), EnumChatFormatting.AQUA);
            return true;
        }

        String requested = args[1].trim();

        if (requested.equalsIgnoreCase("auto") || requested.equalsIgnoreCase("reset"))
        {
            FpsOverlay.setAutoColour();
            reply(mc, "FPS colour back to automatic (green/yellow/red by frame rate).", EnumChatFormatting.GREEN);
            return true;
        }

        int parsed = FpsOverlay.parseHexOr(requested, -1);

        if (parsed < 0)
        {
            reply(mc, "\"" + args[1] + "\" is not a hex colour. Use #RRGGBB, e.g. #FF8800.", EnumChatFormatting.RED);
            return true;
        }

        FpsOverlay.setColour(parsed);
        reply(mc, "FPS colour set to #" + String.format("%06X", parsed) + ". Pass /fps color auto to undo.", EnumChatFormatting.GREEN);
        return true;
    }

    private String describeColour()
    {
        return FpsOverlay.isAutoColour() ? "auto" : String.format("#%06X", FpsOverlay.getColour());
    }

    private boolean shadow(Minecraft mc, String[] args)
    {
        if (args.length != 2)
        {
            reply(mc, "Usage: /fps shadow <on|off>", EnumChatFormatting.RED);
            reply(mc, "Current: " + (FpsOverlay.isShadow() ? "on" : "off"), EnumChatFormatting.AQUA);
            return true;
        }

        String requested = args[1].toLowerCase();
        Boolean parsed = null;

        if (requested.equals("on") || requested.equals("true"))
        {
            parsed = Boolean.TRUE;
        }
        else if (requested.equals("off") || requested.equals("false"))
        {
            parsed = Boolean.FALSE;
        }

        if (parsed == null)
        {
            reply(mc, "Shadow must be \"on\" or \"off\".", EnumChatFormatting.RED);
            return true;
        }

        FpsOverlay.setShadow(parsed.booleanValue());
        reply(mc, "FPS shadow " + (parsed.booleanValue() ? "on" : "off") + ".", parsed.booleanValue() ? EnumChatFormatting.GREEN : EnumChatFormatting.YELLOW);
        return true;
    }

    private void reply(Minecraft mc, String text, EnumChatFormatting colour)
    {
        mc.ingameGUI.getChatGUI().printChatMessage(new ChatComponentText(colour + text));
    }
}