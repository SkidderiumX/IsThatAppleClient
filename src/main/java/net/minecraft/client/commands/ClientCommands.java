package net.minecraft.client.commands;

import net.minecraft.client.Minecraft;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Registry and dispatcher for client-side chat commands.
 *
 * <p>Registered commands are matched against the text typed in chat. Anything matched is
 * consumed locally and never reaches the server, which is what makes these work on
 * single-player worlds and on servers that would reject or rate-limit unknown commands.</p>
 */
public class ClientCommands
{
    private static final List<ClientCommand> COMMANDS = Collections.unmodifiableList(
            Arrays.<ClientCommand>asList(new FovCommand(), new BobCommand(), new SprintCommand(), new FpsCommand(),
                new NhkCommand(), new AnimationsCommand(), new FreeLookCommand(), new CoordsCommand(), new LookVectorCommand(), new JumpDelayCommand(), new TimeChangerCommand(), new NoHitDelayCommand(), new ConfigCommand()));

    private ClientCommands() {}

    /**
     * Offers a message to the registered commands.
     *
     * @return true if a command handled it and the message must not be sent to the server
     */
    public static boolean handle(Minecraft mc, String message)
    {
        if (mc == null || mc.thePlayer == null || mc.ingameGUI == null)
        {
            return false;
        }

        if (message == null || message.length() < 2 || message.charAt(0) != '/')
        {
            return false;
        }

        // "//foo" is the vanilla way to send a literal slash, not a command.
        if (message.charAt(1) == '/')
        {
            return false;
        }

        String[] parts = message.substring(1).trim().split("\\s+");

        if (parts.length == 0 || parts[0].isEmpty())
        {
            return false;
        }

        for (ClientCommand command : COMMANDS)
        {
            if (matches(command, parts[0]))
            {
                return command.execute(mc, Arrays.copyOfRange(parts, 1, parts.length));
            }
        }

        return false;
    }

    /** True when the typed name is the command's own or one of its aliases. */
    private static boolean matches(ClientCommand command, String typed)
    {
        if (command.getName().equalsIgnoreCase(typed))
        {
            return true;
        }

        for (String alias : command.getAliases())
        {
            if (alias.equalsIgnoreCase(typed))
            {
                return true;
            }
        }

        return false;
    }

    public static List<ClientCommand> getCommands()
    {
        return COMMANDS;
    }
}