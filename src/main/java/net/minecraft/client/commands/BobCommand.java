package net.minecraft.client.commands;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

/**
 * /bob [hand|camera|off]
 *
 * <p>Walking bob in vanilla 1.8.9 moves the whole world, so the crosshair swims while
 * you aim. This exposes the two halves separately: the hand can bob on its own, with a
 * steady camera.</p>
 */
public class BobCommand implements ClientCommand
{
    public String getName()
    {
        return "bob";
    }

    public String getUsage()
    {
        return "/bob <hand|camera|off>";
    }

    public boolean execute(Minecraft mc, String[] args)
    {
        GameSettings settings = mc.gameSettings;

        if (args.length == 0)
        {
            reply(mc, "Hand bobbing is " + onOff(settings.viewBobbing) + ", camera bobbing is " + onOff(settings.cameraBobbing) + ".", EnumChatFormatting.AQUA);
            return true;
        }

        if (args.length > 1)
        {
            reply(mc, "Usage: " + getUsage(), EnumChatFormatting.RED);
            return true;
        }

        String mode = args[0].toLowerCase();

        if (mode.equals("hand"))
        {
            settings.viewBobbing = true;
            settings.cameraBobbing = false;
            reply(mc, "Hand bobbing only: the hand bobs, the camera stays steady.", EnumChatFormatting.GREEN);
        }
        else if (mode.equals("camera"))
        {
            settings.viewBobbing = true;
            settings.cameraBobbing = true;
            reply(mc, "Full view bobbing: hand and camera both bob.", EnumChatFormatting.GREEN);
        }
        else if (mode.equals("off"))
        {
            settings.viewBobbing = false;
            settings.cameraBobbing = false;
            reply(mc, "View bobbing disabled.", EnumChatFormatting.GREEN);
        }
        else
        {
            reply(mc, "Unknown mode \"" + args[0] + "\". Usage: " + getUsage(), EnumChatFormatting.RED);
        }

        return true;
    }

    private static String onOff(boolean value)
    {
        return value ? "on" : "off";
    }

    private void reply(Minecraft mc, String text, EnumChatFormatting colour)
    {
        mc.ingameGUI.getChatGUI().printChatMessage(new ChatComponentText(colour + text));
    }
}