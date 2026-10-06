package net.minecraft.client.commands;

import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

/**
 * /timechanger [ticks|day|noon|sunset|night|midnight|sunrise] [on|off] [reset]
 *
 * <p>Pins the client's clock to a chosen time of day. The server is never told: it keeps its own
 * time, and the client simply overwrites its local copy every tick, so the sky holds still while
 * everything else plays on.</p>
 *
 * <p>Settings are stored in {@value ClientConfig#FILE_NAME} in the game directory and rewritten on
 * every change.</p>
 */
public class TimeChangerCommand implements ClientCommand
{
    public String getName()
    {
        return "timechanger";
    }

    public List<String> getAliases()
    {
        return Arrays.asList("tc");
    }

    public String getUsage()
    {
        return "/timechanger | /timechanger <" + TimeChanger.MIN_TIME + "-" + TimeChanger.MAX_TIME
            + "|day|noon|sunset|night|midnight|sunrise> | /timechanger on|off | /timechanger reset";
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
                reply(mc, "Usage: /timechanger reset", EnumChatFormatting.RED);
                return true;
            }

            TimeChanger.reset();
            reply(mc, "Time changer reset to defaults. " + TimeChanger.getConfigFile().getName() + " rewritten.",
                EnumChatFormatting.GREEN);
            return true;
        }

        if (sub.equals("on") || sub.equals("off"))
        {
            if (args.length > 1)
            {
                reply(mc, "Usage: /timechanger on|off", EnumChatFormatting.RED);
                return true;
            }

            TimeChanger.setEnabled(sub.equals("on"));
            reply(mc, "Time changer " + (TimeChanger.isEnabled() ? "enabled at " + describe(TimeChanger.getTime()) : "disabled, back to the server's time")
                + ".",
                TimeChanger.isEnabled() ? EnumChatFormatting.GREEN : EnumChatFormatting.YELLOW);
            return true;
        }

        Long value = parseTime(sub);

        if (value == null)
        {
            reply(mc, "Unknown option \"" + args[0] + "\". Usage: " + getUsage(), EnumChatFormatting.RED);
            return true;
        }

        TimeChanger.setEnabled(true);
        TimeChanger.setTime(value.longValue());

        long got = TimeChanger.getTime();

        reply(mc, "Client time pinned at " + describe(got) + "."
                + (got != value.longValue() ? " (clamped from " + value + ")" : ""),
            EnumChatFormatting.GREEN);
        return true;
    }

    /**
     * Reads a time of day as ticks or as one of the vanilla preset names, rejecting anything else
     * so a typo lands on the usage line rather than being quietly clamped.
     */
    private static Long parseTime(String text)
    {
        if (text.equals("sunrise"))
        {
            return Long.valueOf(TimeChanger.SUNRISE);
        }

        if (text.equals("day"))
        {
            return Long.valueOf(TimeChanger.DAY);
        }

        if (text.equals("noon"))
        {
            return Long.valueOf(TimeChanger.NOON);
        }

        if (text.equals("sunset"))
        {
            return Long.valueOf(TimeChanger.SUNSET);
        }

        if (text.equals("night"))
        {
            return Long.valueOf(TimeChanger.NIGHT);
        }

        if (text.equals("midnight"))
        {
            return Long.valueOf(TimeChanger.MIDNIGHT);
        }

        try
        {
            return Long.valueOf(Long.parseLong(text));
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    /** Names the time for chat: the vanilla preset when it matches one, else ticks and day part. */
    static String describe(long time)
    {
        if (time == TimeChanger.SUNRISE)
        {
            return "sunrise (0)";
        }

        if (time == TimeChanger.DAY)
        {
            return "day (1000)";
        }

        if (time == TimeChanger.NOON)
        {
            return "noon (6000)";
        }

        if (time == TimeChanger.SUNSET)
        {
            return "sunset (12000)";
        }

        if (time == TimeChanger.NIGHT)
        {
            return "night (13000)";
        }

        if (time == TimeChanger.MIDNIGHT)
        {
            return "midnight (18000)";
        }

        String part = time < 12000L ? "daytime" : (time < 23000L ? "nighttime" : "dawn");
        return time + " ticks (" + part + ")";
    }

    private void status(Minecraft mc)
    {
        boolean on = TimeChanger.isEnabled();
        long time = TimeChanger.getTime();

        reply(mc, "Time changer " + (on ? "on at " + describe(time) : "off, following the server")
                + "  (" + TimeChanger.getConfigFile().getName() + ")",
            on ? EnumChatFormatting.GREEN : EnumChatFormatting.YELLOW);

        if (!on)
        {
            reply(mc, "  Set one with /timechanger noon to hold midday.", EnumChatFormatting.GRAY);
        }
    }

    private void reply(Minecraft mc, String text, EnumChatFormatting colour)
    {
        mc.ingameGUI.getChatGUI().printChatMessage(new ChatComponentText(colour + text));
    }
}
