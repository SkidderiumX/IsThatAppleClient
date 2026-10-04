package net.minecraft.client.commands;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

/**
 * /anim [on|off] [setting &lt;on|off&gt;] [smoothspeed &lt;value&gt;] [reset]
 *
 * <p>Old animations, driven entirely from chat. The master switch is off by default, so
 * nothing here changes vanilla behaviour until you type {@code /anim on}. Individual
 * settings then refine what is active.</p>
 *
 * <p>Settings are stored in {@value ClientConfig#FILE_NAME} in the game directory and
 * rewritten on every change.</p>
 */
public class AnimationsCommand implements ClientCommand
{
    /** Chat name to config key, in the order they are listed back to the user. */
    private static final Map<String, String> FLAGS = Collections.unmodifiableMap(
            new LinkedHashMap<String, String>()
            {
                {
                    put("blockhit", "blockHit");
                    put("pushing", "pushing");
                    put("pushingparticles", "pushingParticle");
                    put("sneak", "sneak");
                    put("smoothsneak", "smoothSneak");
                    put("health", "health");
                    put("armordamage", "armorDamage");
                    put("switch", "itemSwitch");
                    put("rod", "rod");
                }
            });

    public String getName()
    {
        return "anim";
    }

    public String getUsage()
    {
        return "/anim | /anim on|off | /anim <" + join(FLAGS.keySet(), "|") + "> <on|off>"
            + " | /anim smoothspeed <0.5-20> | /anim reset";
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
            Animations.reset();
            reply(mc, "Animations reset to defaults. " + Animations.getConfigFile().getName() + " rewritten.",
                EnumChatFormatting.GREEN);
            return true;
        }

        if (sub.equals("on") || sub.equals("off"))
        {
            if (args.length > 1)
            {
                reply(mc, "Usage: /anim on|off", EnumChatFormatting.RED);
                return true;
            }

            Animations.setEnabled(sub.equals("on"));
            reply(mc, "Old animations " + (Animations.isEnabled() ? "enabled" : "disabled") + ".",
                Animations.isEnabled() ? EnumChatFormatting.GREEN : EnumChatFormatting.YELLOW);
            return true;
        }

        if (sub.equals("smoothspeed") || sub.equals("speed"))
        {
            return speed(mc, args);
        }

        String key = FLAGS.get(sub);

        if (key == null)
        {
            reply(mc, "Unknown setting \"" + args[0] + "\". Usage: " + getUsage(), EnumChatFormatting.RED);
            return true;
        }

        return flag(mc, args, key);
    }

    private boolean flag(Minecraft mc, String[] args, String key)
    {
        if (args.length == 1)
        {
            reply(mc, "Usage: /anim " + args[0] + " <on|off>", EnumChatFormatting.RED);
            reply(mc, "Current: " + (Animations.rawFlag(key) ? "on" : "off"), EnumChatFormatting.AQUA);
            return true;
        }

        if (args.length > 2)
        {
            reply(mc, "Usage: /anim " + args[0] + " <on|off>", EnumChatFormatting.RED);
            return true;
        }

        Boolean parsed = parseState(args[1]);

        if (parsed == null)
        {
            reply(mc, "\"" + args[1] + "\" is not a state. Use on or off.", EnumChatFormatting.RED);
            return true;
        }

        Animations.setFlag(key, parsed.booleanValue());
        reply(mc, key + " " + (parsed.booleanValue() ? "on" : "off")
            + (Animations.isEnabled() ? "." : " (inactive, /anim is off)"),
            parsed.booleanValue() ? EnumChatFormatting.GREEN : EnumChatFormatting.YELLOW);
        return true;
    }

    private boolean speed(Minecraft mc, String[] args)
    {
        if (args.length != 2)
        {
            reply(mc, "Usage: /anim smoothspeed <0.5-20>", EnumChatFormatting.RED);
            reply(mc, "Current: " + trim(Animations.getSmoothSneakSpeed()), EnumChatFormatting.AQUA);
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

        if (Float.isNaN(parsed) || Float.isInfinite(parsed))
        {
            reply(mc, "\"" + args[1] + "\" is not a usable value.", EnumChatFormatting.RED);
            return true;
        }

        float clamped = Math.max(0.5F, Math.min(20.0F, parsed));
        Animations.setSmoothSneakSpeed(clamped);

        if (clamped != parsed)
        {
            reply(mc, "Smooth sneak speed clamped to " + trim(clamped) + " (allowed 0.5-20).", EnumChatFormatting.YELLOW);
        }
        else
        {
            reply(mc, "Smooth sneak speed set to " + trim(clamped) + ".", EnumChatFormatting.GREEN);
        }

        if (!Animations.isSmoothSneak())
        {
            reply(mc, "Smooth sneak itself is off, so this has no effect yet.", EnumChatFormatting.YELLOW);
        }

        return true;
    }

    private void status(Minecraft mc)
    {
        boolean on = Animations.isEnabled();
        reply(mc, "Old animations " + (on ? "enabled" : "disabled")
            + "  (" + Animations.getConfigFile().getName() + ")", on ? EnumChatFormatting.GREEN : EnumChatFormatting.YELLOW);

        for (Map.Entry<String, String> entry : FLAGS.entrySet())
        {
            reply(mc, "  " + entry.getKey() + ": " + (Animations.rawFlag(entry.getValue()) ? "on" : "off"),
                on ? EnumChatFormatting.GRAY : EnumChatFormatting.DARK_GRAY);
        }

        reply(mc, "  smoothspeed: " + trim(Animations.getSmoothSneakSpeed()), on ? EnumChatFormatting.GRAY : EnumChatFormatting.DARK_GRAY);
    }

    /** Accepts on/off and true/false, matching the other commands. */
    private static Boolean parseState(String value)
    {
        String requested = value.toLowerCase();

        if (requested.equals("on") || requested.equals("true"))
        {
            return Boolean.TRUE;
        }

        if (requested.equals("off") || requested.equals("false"))
        {
            return Boolean.FALSE;
        }

        return null;
    }

    private static String trim(float value)
    {
        return value == Math.round(value) ? String.valueOf((long)value) : String.valueOf(value);
    }

    private static String join(Iterable<String> values, String separator)
    {
        StringBuilder builder = new StringBuilder();
        boolean first = true;

        for (String value : values)
        {
            if (!first)
            {
                builder.append(separator);
            }

            builder.append(value);
            first = false;
        }

        return builder.toString();
    }

    private void reply(Minecraft mc, String text, EnumChatFormatting colour)
    {
        mc.ingameGUI.getChatGUI().printChatMessage(new ChatComponentText(colour + text));
    }
}
