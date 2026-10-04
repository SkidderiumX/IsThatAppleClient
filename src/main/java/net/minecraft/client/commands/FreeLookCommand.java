package net.minecraft.client.commands;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

/**
 * /freelook [on|off] [mode &lt;hold|toggle&gt;] [view &lt;first|third|both&gt;] [f5 &lt;on|off&gt;] [reset]
 *
 * <p>Free look, driven from chat. Unlike the animation settings there is no separate
 * behaviour to opt into, so it starts enabled: bind the key under Options &gt; Controls and
 * it works. The mode decides whether the bind has to be held or toggles free look instead, and
 * the view restricts free look to one camera or works in both.</p>
 *
 * <p>{@code f5} additionally moves the camera to the view behind the player while free look is
 * engaged and hands it back to first person on release.</p>
 *
 * <p>Settings are stored in {@value ClientConfig#FILE_NAME} in the game directory and rewritten
 * on every change.</p>
 */
public class FreeLookCommand implements ClientCommand
{
    public String getName()
    {
        return "freelook";
    }

    public String getUsage()
    {
        return "/freelook | /freelook on|off | /freelook mode <hold|toggle> | /freelook view <first|third|both> | /freelook f5 <on|off> | /freelook reset";
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
                reply(mc, "Usage: /freelook reset", EnumChatFormatting.RED);
                return true;
            }

            FreeLook.reset();
            reply(mc, "Free look reset to defaults. " + FreeLook.getConfigFile().getName() + " rewritten.",
                EnumChatFormatting.GREEN);
            return true;
        }

        if (sub.equals("on") || sub.equals("off"))
        {
            if (args.length > 1)
            {
                reply(mc, "Usage: /freelook on|off", EnumChatFormatting.RED);
                return true;
            }

            FreeLook.setEnabled(sub.equals("on"));
            reply(mc, "Free look " + (FreeLook.isEnabled() ? "enabled" : "disabled") + ".",
                FreeLook.isEnabled() ? EnumChatFormatting.GREEN : EnumChatFormatting.YELLOW);
            replyBind(mc);
            return true;
        }

        if (sub.equals("mode"))
        {
            return mode(mc, args);
        }

        if (sub.equals("view") || sub.equals("perspective"))
        {
            return view(mc, args);
        }

        if (sub.equals("f5") || sub.equals("autof5"))
        {
            return f5(mc, args);
        }

        reply(mc, "Unknown option \"" + args[0] + "\". Usage: " + getUsage(), EnumChatFormatting.RED);
        return true;
    }

    private boolean f5(Minecraft mc, String[] args)
    {
        if (args.length == 1)
        {
            reply(mc, "Usage: /freelook f5 <on|off>", EnumChatFormatting.RED);
            reply(mc, "Current: " + (FreeLook.isAutoF5() ? "on" : "off"), EnumChatFormatting.AQUA);
            return true;
        }

        if (args.length > 2)
        {
            reply(mc, "Usage: /freelook f5 <on|off>", EnumChatFormatting.RED);
            return true;
        }

        Boolean on = parseOnOff(args[1]);

        if (on == null)
        {
            reply(mc, "\"" + args[1] + "\" is not on or off.", EnumChatFormatting.RED);
            return true;
        }

        FreeLook.setAutoF5(on);
        reply(mc, "Auto third person " + (FreeLook.isAutoF5() ? "on" : "off") + "."
            + (FreeLook.isAutoF5() ? "  The camera moves behind you while free look is engaged, and back to first person on release." : ""),
            FreeLook.isAutoF5() ? EnumChatFormatting.GREEN : EnumChatFormatting.YELLOW);

        if (FreeLook.isAutoF5() && FreeLook.getView() == FreeLook.View.FIRST)
        {
            // Silently doing nothing would look like a bug, so say it plainly.
            reply(mc, "  Inert right now: the view is set to first person, which auto third person cannot override. Use /freelook view both or third.",
                EnumChatFormatting.YELLOW);
        }
        else if (FreeLook.isAutoF5() && FreeLook.getView() == FreeLook.View.THIRD)
        {
            reply(mc, "  The view is third person only, so releasing will leave you in first person where the bind is ignored until you press F5.",
                EnumChatFormatting.YELLOW);
        }

        return true;
    }

    /** Reads an on/off argument, or null when it is neither. */
    private static Boolean parseOnOff(String word)
    {
        if (word.equalsIgnoreCase("on") || word.equalsIgnoreCase("true") || word.equals("1"))
        {
            return Boolean.TRUE;
        }

        if (word.equalsIgnoreCase("off") || word.equalsIgnoreCase("false") || word.equals("0"))
        {
            return Boolean.FALSE;
        }

        return null;
    }

    private boolean mode(Minecraft mc, String[] args)
    {
        if (args.length == 1)
        {
            reply(mc, "Usage: /freelook mode <hold|toggle>", EnumChatFormatting.RED);
            reply(mc, "Current: " + FreeLook.getMode().name().toLowerCase(), EnumChatFormatting.AQUA);
            return true;
        }

        if (args.length > 2)
        {
            reply(mc, "Usage: /freelook mode <hold|toggle>", EnumChatFormatting.RED);
            return true;
        }

        FreeLook.Mode parsed;

        if (args[1].equalsIgnoreCase("hold"))
        {
            parsed = FreeLook.Mode.HOLD;
        }
        else if (args[1].equalsIgnoreCase("toggle"))
        {
            parsed = FreeLook.Mode.TOGGLE;
        }
        else
        {
            reply(mc, "\"" + args[1] + "\" is not a mode. Use hold or toggle.", EnumChatFormatting.RED);
            return true;
        }

        FreeLook.setMode(parsed);
        reply(mc, "Free look mode set to " + parsed.name().toLowerCase()
            + (parsed == FreeLook.Mode.HOLD ? " (on while the key is down)." : " (press the key to toggle)."),
            EnumChatFormatting.GREEN);
        replyBind(mc);
        return true;
    }

    private boolean view(Minecraft mc, String[] args)
    {
        if (args.length == 1)
        {
            reply(mc, "Usage: /freelook view <first|third|both>", EnumChatFormatting.RED);
            reply(mc, "Current: " + FreeLook.getView().name().toLowerCase(), EnumChatFormatting.AQUA);
            return true;
        }

        if (args.length > 2)
        {
            reply(mc, "Usage: /freelook view <first|third|both>", EnumChatFormatting.RED);
            return true;
        }

        FreeLook.View parsed;

        // Shorthands, because /freelook view 3 is quicker to type than /freelook view third.
        if (args[1].equalsIgnoreCase("first") || args[1].equals("1"))
        {
            parsed = FreeLook.View.FIRST;
        }
        else if (args[1].equalsIgnoreCase("third") || args[1].equals("3"))
        {
            parsed = FreeLook.View.THIRD;
        }
        else if (args[1].equalsIgnoreCase("both") || args[1].equalsIgnoreCase("all"))
        {
            parsed = FreeLook.View.BOTH;
        }
        else
        {
            reply(mc, "\"" + args[1] + "\" is not a view. Use first, third or both.", EnumChatFormatting.RED);
            return true;
        }

        FreeLook.setView(parsed);
        reply(mc, "Free look view set to " + parsed.name().toLowerCase() + describeView(parsed),
            EnumChatFormatting.GREEN);

        // Worth saying out loud, since setting view=third while standing in first person
        // leaves the bind doing nothing until F5 is pressed.
        if (parsed != FreeLook.View.BOTH && !FreeLook.isEnabled())
        {
            reply(mc, "  Free look is disabled, so the restriction has no effect yet.", EnumChatFormatting.YELLOW);
        }

        replyBind(mc);
        return true;
    }

    private static String describeView(FreeLook.View parsed)
    {
        switch (parsed)
        {
            case FIRST:
                return " (the bind is ignored in third person).";

            case THIRD:
                return " (the bind is ignored in first person).";

            default:
                return " (the bind works in both views).";
        }
    }

    private void status(Minecraft mc)
    {
        boolean on = FreeLook.isEnabled();
        reply(mc, "Free look " + (on ? "enabled" : "disabled")
            + "  mode: " + FreeLook.getMode().name().toLowerCase()
            + "  view: " + FreeLook.getView().name().toLowerCase()
            + "  auto f5: " + (FreeLook.isAutoF5() ? "on" : "off")
            + (on && FreeLook.isActive() ? "  active" : "")
            + "  (" + FreeLook.getConfigFile().getName() + ")",
            on ? EnumChatFormatting.GREEN : EnumChatFormatting.YELLOW);

        replyBind(mc);

        if (on)
        {
            reply(mc, "  hold while aiming; the camera eases back on release.",
                EnumChatFormatting.GRAY);
        }

        if (on && LookVector.isEnabled() && FreeLook.hasOffset())
        {
            // Only worth saying when there is actually something to hand over.
            reply(mc, "  /lookvector apply turns your body to match the camera.",
                EnumChatFormatting.GRAY);
        }
    }

    /** Reports the bind, and nags when there is none, since that is the one way to get nothing. */
    private void replyBind(Minecraft mc)
    {
        GameSettings settings = mc.gameSettings;
        int code = settings == null ? 0 : settings.keyBindFreeLook.getKeyCode();

        if (code == 0)
        {
            reply(mc, "  No key bound. Pick one in Options > Controls > Miscellaneous.", EnumChatFormatting.RED);
        }
        else
        {
            reply(mc, "  Key: " + GameSettings.getKeyDisplayString(code), EnumChatFormatting.GRAY);
        }
    }

    private void reply(Minecraft mc, String text, EnumChatFormatting colour)
    {
        mc.ingameGUI.getChatGUI().printChatMessage(new ChatComponentText(colour + text));
    }
}