package net.minecraft.client.commands;

import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

/**
 * /nohitdelay [on|off] [reset]
 *
 * <p>Removes the 10 tick cooldown vanilla arms after every click that misses, so clicks register
 * up to once a tick instead of at most twice a second on misses. The swing, the reach and what a
 * hit does are untouched.</p>
 *
 * <p>Settings are stored in {@value ClientConfig#FILE_NAME} in the game directory and rewritten on
 * every change.</p>
 */
public class NoHitDelayCommand implements ClientCommand
{
    public String getName()
    {
        return "nohitdelay";
    }

    public List<String> getAliases()
    {
        return Arrays.asList("nhd");
    }

    public String getUsage()
    {
        return "/nohitdelay | /nohitdelay on|off | /nohitdelay reset";
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
                reply(mc, "Usage: /nohitdelay reset", EnumChatFormatting.RED);
                return true;
            }

            NoHitDelay.reset();
            reply(mc, "No hit delay reset to defaults. " + NoHitDelay.getConfigFile().getName() + " rewritten.",
                EnumChatFormatting.GREEN);
            return true;
        }

        if (sub.equals("on") || sub.equals("off"))
        {
            if (args.length > 1)
            {
                reply(mc, "Usage: /nohitdelay on|off", EnumChatFormatting.RED);
                return true;
            }

            NoHitDelay.setEnabled(sub.equals("on"));
            reply(mc, "No hit delay " + (NoHitDelay.isEnabled() ? "enabled, every click goes through"
                    : "disabled, back to vanilla's 10 tick cooldown on misses") + ".",
                NoHitDelay.isEnabled() ? EnumChatFormatting.GREEN : EnumChatFormatting.YELLOW);
            return true;
        }

        reply(mc, "Unknown option \"" + args[0] + "\". Usage: " + getUsage(), EnumChatFormatting.RED);
        return true;
    }

    private void status(Minecraft mc)
    {
        boolean on = NoHitDelay.isEnabled();

        reply(mc, "No hit delay " + (on ? "on, every click goes through" : "off, vanilla's 10 tick cooldown on misses")
                + "  (" + NoHitDelay.getConfigFile().getName() + ")",
            on ? EnumChatFormatting.GREEN : EnumChatFormatting.YELLOW);
    }

    private void reply(Minecraft mc, String text, EnumChatFormatting colour)
    {
        mc.ingameGUI.getChatGUI().printChatMessage(new ChatComponentText(colour + text));
    }
}
