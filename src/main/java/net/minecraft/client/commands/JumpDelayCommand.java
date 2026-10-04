package net.minecraft.client.commands;

import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

/**
 * /jumpdelay [n] [on|off] [reset]
 *
 * <p>Puts a gap between jumps while the jump key is held, so holding it hops at a set rate instead
 * of every time the player touches ground. Zero is what this build has always done.</p>
 *
 * <p>This is the gap vanilla already had and then threw away. {@code EntityLivingBase} keeps a
 * {@code jumpTicks} field that is decremented a tick at a time and gates the jump on it reaching
 * zero, but vanilla zeroes it the moment it jumps, so the gate never bites. This reinstates it
 * behind a setting. One difference from vanilla: the gap runs down whatever the key is doing, so
 * letting go mid-gap and pressing again does not hand back a free instant jump.</p>
 *
 * <p>In ticks. Those are world ticks, and {@link TickSpeed} holds the world to 20 a second, so
 * raising the tick rate will not quietly shorten the gap.</p>
 *
 * <p>Settings are stored in {@value ClientConfig#FILE_NAME} in the game directory and rewritten on
 * every change.</p>
 */
public class JumpDelayCommand implements ClientCommand
{
    public String getName()
    {
        return "jumpdelay";
    }

    public List<String> getAliases()
    {
        return Arrays.asList("hopdelay", "jdelay");
    }

    public String getUsage()
    {
        return "/jumpdelay | /jumpdelay <" + JumpDelay.MIN_DELAY + "-" + JumpDelay.MAX_DELAY
            + "> | /jumpdelay on|off | /jumpdelay reset";
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

        if (sub.equals("reset"))
        {
            if (args.length > 1)
            {
                reply(mc, "Usage: /jumpdelay reset", EnumChatFormatting.RED);
                return true;
            }

            JumpDelay.reset();
            reply(mc, "Jump delay reset to defaults. " + JumpDelay.getConfigFile().getName() + " rewritten.",
                EnumChatFormatting.GREEN);
            return true;
        }

        if (sub.equals("on") || sub.equals("off"))
        {
            if (args.length > 1)
            {
                reply(mc, "Usage: /jumpdelay on|off", EnumChatFormatting.RED);
                return true;
            }

            JumpDelay.setEnabled(sub.equals("on"));
            reply(mc, "Jump delay " + (JumpDelay.isEnabled() ? "enabled" : "disabled")
                + ". Hold jump and you hop every " + JumpDelay.getDelay() + " ticks.",
                JumpDelay.isEnabled() ? EnumChatFormatting.GREEN : EnumChatFormatting.YELLOW);
            return true;
        }

        Integer value = parseDelay(sub);

        if (value == null)
        {
            reply(mc, "Unknown option \"" + args[0] + "\". Usage: " + getUsage(), EnumChatFormatting.RED);
            return true;
        }

        JumpDelay.setEnabled(true);
        JumpDelay.setDelay(value);

        int got = JumpDelay.getDelay();

        reply(mc, got == 0
                ? "No jump delay. Holding jump hops the moment you land, as before."
                : "Jump delay " + got + " tick" + (got == 1 ? "" : "s") + " ("
                    + (got * 50) + " ms). Hold jump and you hop that often."
                    + (got != value.intValue() ? " (clamped from " + value + ")" : ""),
            got == 0 ? EnumChatFormatting.YELLOW : EnumChatFormatting.GREEN);
        return true;
    }

    /**
     * Reads a tick count, rejecting anything that is not a number so a typo lands on the usage
     * line rather than being quietly clamped to zero.
     */
    private static Integer parseDelay(String text)
    {
        try
        {
            return Integer.valueOf(Integer.parseInt(text));
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    private void status(Minecraft mc)
    {
        boolean on = JumpDelay.isEnabled();
        int delay = JumpDelay.getDelay();

        reply(mc, "Jump delay " + (on ? "on at " + delay + " tick" + (delay == 1 ? "" : "s")
                : "off") + "  (" + JumpDelay.getConfigFile().getName() + ")",
            on ? EnumChatFormatting.GREEN : EnumChatFormatting.YELLOW);

        if (on && delay == 0)
        {
            reply(mc, "  Zero ticks, so this is doing nothing. Set one with /jumpdelay 10 for vanilla's half second.",
                EnumChatFormatting.GRAY);
        }
        else if (on)
        {
            reply(mc, "  " + (delay * 50) + " ms between jumps.", EnumChatFormatting.GRAY);
        }

        int left = JumpDelay.getCooldown();

        if (left > 0)
        {
            reply(mc, "  Waiting " + left + " tick" + (left == 1 ? "" : "s") + " before the next jump can start.",
                EnumChatFormatting.AQUA);
        }
    }

    private void reply(Minecraft mc, String text, EnumChatFormatting colour)
    {
        mc.ingameGUI.getChatGUI().printChatMessage(new ChatComponentText(colour + text));
    }
}
