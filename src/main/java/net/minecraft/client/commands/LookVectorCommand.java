package net.minecraft.client.commands;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

/**
 * /lookvector [apply] [on|off] [reset]
 *
 * <p>Hands the free look offset to the player's own rotation, so the body turns to face where the
 * camera was pointing. Free look leaves the body alone while it is engaged; this is what makes
 * the body catch up before you drop it.</p>
 *
 * <p>Meant to be typed while free look is still holding the offset. Toggle mode keeps it banked
 * for as long as the toggle is on, so the offset is there whenever the command lands. Hold mode
 * starts easing it away the moment the key comes up, so what gets applied there is the direction
 * the camera was last pointing - which is the same number, just read a moment later.</p>
 *
 * <p>Settings are stored in {@value ClientConfig#FILE_NAME} in the game directory and rewritten
 * on every change.</p>
 */
public class LookVectorCommand implements ClientCommand
{
    public String getName()
    {
        return "lookvector";
    }

    public List<String> getAliases()
    {
        return Arrays.asList("realvector", "commitlook");
    }

    public String getUsage()
    {
        return "/lookvector | /lookvector apply | /lookvector on|off | /lookvector reset";
    }

    public boolean execute(Minecraft mc, String[] args)
    {
        if (args.length == 0)
        {
            status(mc);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("help"))
        {
            reply(mc, getUsage(), EnumChatFormatting.AQUA);
            return true;
        }

        if (sub.equals("apply") || sub.equals("commit"))
        {
            if (args.length > 1)
            {
                reply(mc, "Usage: /lookvector apply", EnumChatFormatting.RED);
                return true;
            }

            apply(mc);
            return true;
        }

        if (sub.equals("reset"))
        {
            if (args.length > 1)
            {
                reply(mc, "Usage: /lookvector reset", EnumChatFormatting.RED);
                return true;
            }

            LookVector.reset();
            reply(mc, "Real look vector reset to defaults. " + LookVector.getConfigFile().getName() + " rewritten.",
                EnumChatFormatting.GREEN);
            return true;
        }

        if (sub.equals("on") || sub.equals("off"))
        {
            if (args.length > 1)
            {
                reply(mc, "Usage: /lookvector on|off", EnumChatFormatting.RED);
                return true;
            }

            LookVector.setEnabled(sub.equals("on"));
            reply(mc, "Real look vector " + (LookVector.isEnabled() ? "enabled" : "disabled") + ".",
                LookVector.isEnabled() ? EnumChatFormatting.GREEN : EnumChatFormatting.YELLOW);
            return true;
        }

        reply(mc, "Unknown option \"" + args[0] + "\". Usage: " + getUsage(), EnumChatFormatting.RED);
        return true;
    }

    private void apply(Minecraft mc)
    {
        EntityPlayerSP player = mc.thePlayer;

        // Read the pending offset before the commit clears it, so the report can name the numbers
        // that were actually handed over.
        float yaw = FreeLook.getPendingYaw();
        float pitch = FreeLook.getPendingPitch();

        LookVector.Availability result = LookVector.apply(mc);

        if (result != LookVector.Availability.OK)
        {
            report(mc, result);
            return;
        }

        reply(mc, "Body turned to match the free look camera, yaw " + trim(yaw)
            + ", pitch " + trim(pitch) + ".", EnumChatFormatting.GREEN);

        if (player != null)
        {
            reply(mc, "  You now face yaw " + trim(player.rotationYaw)
                + ", pitch " + trim(player.rotationPitch) + ".",
                EnumChatFormatting.GRAY);
        }

        reply(mc, "  Sent to the server on the next movement packet.", EnumChatFormatting.GRAY);
    }

    /** Explains the refusal in a way that says what to do next, never just "failed". */
    private void report(Minecraft mc, LookVector.Availability result)
    {
        switch (result)
        {
            case DISABLED:
                reply(mc, "Real look vector is off. Turn it on with /lookvector on.", EnumChatFormatting.RED);
                return;

            case FREE_LOOK_OFF:
                reply(mc, "Free look is disabled, so there is no offset to apply. Use /freelook on.",
                    EnumChatFormatting.RED);
                return;

            case NO_PLAYER:
                reply(mc, "No player in the world.", EnumChatFormatting.RED);
                return;

            default:
                reply(mc, "Nothing to apply - the camera is already on the body. Engage free look, look somewhere else, then run this.",
                    EnumChatFormatting.YELLOW);
        }
    }

    private void status(Minecraft mc)
    {
        boolean on = LookVector.isEnabled();

        reply(mc, "Real look vector " + (on ? "on" : "off")
            + "  (" + LookVector.getConfigFile().getName() + ")",
            on ? EnumChatFormatting.GREEN : EnumChatFormatting.YELLOW);

        if (!on)
        {
            return;
        }

        if (!FreeLook.isEnabled())
        {
            reply(mc, "  Free look is off, so nothing can be applied. Use /freelook on.", EnumChatFormatting.YELLOW);
            return;
        }

        if (FreeLook.hasOffset())
        {
            reply(mc, "  Waiting to apply: yaw " + trim(FreeLook.getPendingYaw())
                + ", pitch " + trim(FreeLook.getPendingPitch()) + ".",
                EnumChatFormatting.AQUA);
            return;
        }

        reply(mc, "  No offset banked. Engage free look, look somewhere else, then /lookvector apply.",
            EnumChatFormatting.GRAY);
    }

    /** Drops a trailing zero, so a yaw of 90 reads as 90 rather than 90.0. */
    static String trim(float value)
    {
        String text = String.format(Locale.ROOT, "%.2f", value);
        return text.endsWith(".00") ? text.substring(0, text.length() - 3) : text;
    }

    private void reply(Minecraft mc, String text, EnumChatFormatting colour)
    {
        mc.ingameGUI.getChatGUI().printChatMessage(new ChatComponentText(colour + text));
    }
}