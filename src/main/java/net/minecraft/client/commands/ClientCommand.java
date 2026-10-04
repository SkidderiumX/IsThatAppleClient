package net.minecraft.client.commands;

import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;

/**
 * A client-side chat command. Commands are handled entirely on the client and are
 * never forwarded to the server.
 */
public interface ClientCommand
{
    /** The name typed after the slash, matched case-insensitively. */
    String getName();

    /** One-line usage summary shown when arguments are wrong. */
    String getUsage();

    /**
     * Runs the command.
     *
     * @param args the tokens after the command name, already split on whitespace
     * @return true if handled; false falls through to the server
     */
    boolean execute(Minecraft mc, String[] args);

    /**
     * Extra names this command also answers to, matched the same way as {@link #getName()}.
     *
     * <p>Empty unless a command has a common synonym worth keeping.</p>
     */
    default List<String> getAliases()
    {
        return Collections.emptyList();
    }
}