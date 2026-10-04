package net.minecraft.client.commands;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

/**
 * /sprint [on|off] - toggles always-sprint.
 *
 * <p>When on, moving forward starts a sprint without holding the sprint key. Sneaking,
 * using an item, blindness and an empty hunger bar still stop it, matching vanilla's own
 * conditions.</p>
 */
public class SprintCommand implements ClientCommand
{
    public String getName()
    {
        return "sprint";
    }

    public String getUsage()
    {
        return "/sprint <on|off>";
    }

    public boolean execute(Minecraft mc, String[] args)
    {
        if (args.length == 0)
        {
            mc.gameSettings.alwaysSprint = !mc.gameSettings.alwaysSprint;
            reply(mc, "Always sprint " + (mc.gameSettings.alwaysSprint ? "on" : "off") + ".", mc.gameSettings.alwaysSprint ? EnumChatFormatting.GREEN : EnumChatFormatting.YELLOW);
            return true;
        }

        if (args.length > 1)
        {
            reply(mc, "Usage: " + getUsage(), EnumChatFormatting.RED);
            return true;
        }

        String state = args[0].toLowerCase();

        if (state.equals("on"))
        {
            mc.gameSettings.alwaysSprint = true;
            reply(mc, "Always sprint on.", EnumChatFormatting.GREEN);
        }
        else if (state.equals("off"))
        {
            mc.gameSettings.alwaysSprint = false;
            reply(mc, "Always sprint off.", EnumChatFormatting.YELLOW);
        }
        else
        {
            reply(mc, "Unknown state \"" + args[0] + "\". Usage: " + getUsage(), EnumChatFormatting.RED);
        }

        return true;
    }

    private void reply(Minecraft mc, String text, EnumChatFormatting colour)
    {
        mc.ingameGUI.getChatGUI().printChatMessage(new ChatComponentText(colour + text));
    }
}