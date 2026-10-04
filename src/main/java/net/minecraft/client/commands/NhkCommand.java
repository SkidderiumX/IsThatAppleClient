package net.minecraft.client.commands;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

/**
 * /nhk &lt;on|off&gt; - toggles NoHurtCam.
 *
 * <p>When on, taking damage no longer tilts the view, so the crosshair stays where you
 * put it. The death roll and the red damage flash still play, so you can still tell you
 * were hit.</p>
 */
public class NhkCommand implements ClientCommand
{
    public String getName()
    {
        return "nhk";
    }

    public String getUsage()
    {
        return "/nhk <on|off>";
    }

    public boolean execute(Minecraft mc, String[] args)
    {
        if (args.length == 0)
        {
            mc.gameSettings.noHurtCam = !mc.gameSettings.noHurtCam;
            report(mc);
            return true;
        }

        if (args.length > 1)
        {
            reply(mc, "Usage: " + getUsage(), EnumChatFormatting.RED);
            return true;
        }

        String requested = args[0].toLowerCase();

        if (requested.equals("on") || requested.equals("true"))
        {
            mc.gameSettings.noHurtCam = true;
        }
        else if (requested.equals("off") || requested.equals("false"))
        {
            mc.gameSettings.noHurtCam = false;
        }
        else
        {
            reply(mc, "\"" + args[0] + "\" is not a state. Usage: " + getUsage(), EnumChatFormatting.RED);
            return true;
        }

        report(mc);
        return true;
    }

    private void report(Minecraft mc)
    {
        boolean on = mc.gameSettings.noHurtCam;
        reply(mc, "No hurt cam " + (on ? "on" : "off") + ".",
            on ? EnumChatFormatting.GREEN : EnumChatFormatting.YELLOW);
    }

    private void reply(Minecraft mc, String text, EnumChatFormatting colour)
    {
        mc.ingameGUI.getChatGUI().printChatMessage(new ChatComponentText(colour + text));
    }
}
