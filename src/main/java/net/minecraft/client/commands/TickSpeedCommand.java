package net.minecraft.client.commands;

import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

/**
 * /tickspeed [n] [on|off] [reset]
 *
 * <p>Raises the client's own tick rate, so keys, the mouse and the GUI are polled more often and
 * register sooner.</p>
 *
 * <p>The world deliberately does not come along. Everything to do with physics, movement packets,
 * sound timing and particle lifetimes is held to the 20 per second the server runs at, so the
 * client cannot outrun it and get pulled back. That is what this does instead of the obvious
 * version, which is just to raise the game's tick rate and let the player move at double speed
 * into a wall.</p>
 *
 * <p>Because the world still ticks at 20, the gain is input latency and how often anything
 * reacting to input gets a chance to run - not a faster game. Frame rate drops as the rate rises,
 * so the useful ceiling is whatever the machine can frame.</p>
 *
 * <p>Settings are stored in {@value ClientConfig#FILE_NAME} in the game directory and rewritten on
 * every change.</p>
 */
public class TickSpeedCommand implements ClientCommand
{
    public String getName()
    {
        return "tickspeed";
    }

    public List<String> getAliases()
    {
        return Arrays.asList("tickrate", "tps");
    }

    public String getUsage()
    {
        return "/tickspeed | /tickspeed <"
            + (int)TickSpeed.MIN_TICKS + "-" + (int)TickSpeed.MAX_TICKS + "> | /tickspeed on|off | /tickspeed reset";
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
                reply(mc, "Usage: /tickspeed reset", EnumChatFormatting.RED);
                return true;
            }

            TickSpeed.reset();
            reply(mc, "Tick speed reset to defaults. " + TickSpeed.getConfigFile().getName() + " rewritten.",
                EnumChatFormatting.GREEN);
            return true;
        }

        if (sub.equals("on") || sub.equals("off"))
        {
            if (args.length > 1)
            {
                reply(mc, "Usage: /tickspeed on|off", EnumChatFormatting.RED);
                return true;
            }

            TickSpeed.setEnabled(sub.equals("on"));
            reply(mc, "Tick speed " + (TickSpeed.isEnabled() ? "enabled" : "disabled, back to the game's own 20.")
                + TickSpeed.unreachable(mc.getFpsCounter()),
                TickSpeed.isEnabled() ? EnumChatFormatting.GREEN : EnumChatFormatting.YELLOW);
            return true;
        }

        Float value = parseRate(sub);

        if (value == null)
        {
            reply(mc, "Unknown option \"" + args[0] + "\". Usage: " + getUsage(), EnumChatFormatting.RED);
            return true;
        }

        // Setting a rate implies wanting it, the same way other mods treat a subcommand that only
        // makes sense when the mod is on.
        TickSpeed.setEnabled(true);
        TickSpeed.setTicks(value);

        float got = TickSpeed.getTicks();

        reply(mc, "Tick rate " + TickSpeed.trim(got)
            + (TickSpeed.trim(got).equals(sub) ? "" : " (clamped from " + sub + ")")
            + ". World stays at 20."
            + TickSpeed.unreachable(mc.getFpsCounter()),
            got > TickSpeed.BASE_TICKS ? EnumChatFormatting.GREEN : EnumChatFormatting.AQUA);
        return true;
    }

    /**
     * Reads a tick rate, rejecting anything that is not a number so a typo lands on the usage
     * line rather than being quietly clamped to a limit.
     */
    private static Float parseRate(String text)
    {
        try
        {
            float value = Float.parseFloat(text);
            return Float.isNaN(value) ? null : Float.valueOf(value);
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    private void status(Minecraft mc)
    {
        boolean on = TickSpeed.isEnabled();
        float rate = TickSpeed.getTicks();

        reply(mc, "Tick speed " + (on ? "on at " + TickSpeed.trim(rate) : "off, the game's own 20")
            + "  (" + TickSpeed.getConfigFile().getName() + ")",
            on ? EnumChatFormatting.GREEN : EnumChatFormatting.YELLOW);

        if (on)
        {
            int fps = mc.getFpsCounter();
            reply(mc, "  " + fps + " fps, so the loop can reach " + TickSpeed.trim(TickSpeed.reachable(fps))
                + " ticks per second at most.", EnumChatFormatting.GRAY);
        }

        reply(mc, "  Physics, movement packets, sound and particles stay at 20 either way.", EnumChatFormatting.GRAY);
    }

    private void reply(Minecraft mc, String text, EnumChatFormatting colour)
    {
        mc.ingameGUI.getChatGUI().printChatMessage(new ChatComponentText(colour + text));
    }
}
