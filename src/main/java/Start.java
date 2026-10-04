import net.minecraft.client.main.Main;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Start {

    public static void main(String[] args)
    {
        System.setProperty("org.lwjgl.librarypath", new File("../natives/" + (System.getProperty("os.name").startsWith("Windows") ? "windows" : "linux")).getAbsolutePath());

        // Only supply defaults for options the caller didn't already pass.
        // jopt-simple rejects duplicates, and a launcher (TLauncher/vanilla)
        // supplies these through minecraftArguments, while an IDE run config
        // passes nothing and needs every one of them.
        List<String> arguments = new ArrayList<String>();

        addDefault(arguments, args, "--version", "GradleMCP");
        addDefault(arguments, args, "--accessToken", "0");
        addDefault(arguments, args, "--assetsDir", "assets");
        addDefault(arguments, args, "--assetIndex", "1.8");
        addDefault(arguments, args, "--userProperties", "{}");

        arguments.addAll(Arrays.asList(args));
        Main.main(arguments.toArray(new String[arguments.size()]));
    }

    private static void addDefault(List<String> arguments, String[] args, String option, String value)
    {
        if (!hasOption(args, option))
        {
            arguments.add(option);
            arguments.add(value);
        }
    }

    private static boolean hasOption(String[] args, String option)
    {
        for (int i = 0; i < args.length; ++i)
        {
            if (option.equals(args[i]))
            {
                return true;
            }
        }

        return false;
    }
}
