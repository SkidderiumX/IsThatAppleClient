package net.minecraft.client.commands;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiClientSettings;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

/**
 * /config - opens the screen that holds every mod setting.
 *
 * <p>Also reachable as {@code /settings}. The same screen opens from the pause menu and from the
 * key bind under Options &gt; Controls, so nobody has to remember the command.</p>
 *
 * <p>{@code /config reset} puts every setting back to its default and {@code /config file} names
 * the single file they all live in, for anyone who would rather edit it by hand.</p>
 */
public class ConfigCommand implements ClientCommand
{
    public String getName()
    {
        return "config";
    }

    public String getUsage()
    {
        return "/config | /config reset | /config file";
    }

    public List<String> getAliases()
    {
        return Collections.unmodifiableList(Arrays.asList("settings", "mods"));
    }

    public boolean execute(Minecraft mc, String[] args)
    {
        if (args.length == 0)
        {
            mc.displayGuiScreen(new GuiClientSettings(mc));
            return true;
        }

        if (args.length > 1)
        {
            reply(mc, "Usage: " + getUsage(), EnumChatFormatting.RED);
            return true;
        }

        if (args[0].equalsIgnoreCase("reset"))
        {
            ClientConfig.resetAll();
            reply(mc, "Every setting reset to its default. " + ClientConfig.getConfigFile().getName() + " rewritten.",
                EnumChatFormatting.GREEN);
            return true;
        }

        if (args[0].equalsIgnoreCase("file"))
        {
            reply(mc, "Settings live in " + ClientConfig.getConfigFile().getAbsolutePath()
                + ". Delete it to start over on the next launch.", EnumChatFormatting.AQUA);
            return true;
        }

        reply(mc, "Usage: " + getUsage(), EnumChatFormatting.RED);
        return true;
    }

    private void reply(Minecraft mc, String text, EnumChatFormatting colour)
    {
        mc.ingameGUI.getChatGUI().printChatMessage(new ChatComponentText(colour + text));
    }
}
