import com.cegesoft.jarvis.Jarvis;
import com.cegesoft.jarvis.JarvisConfig;
import com.cegesoft.jarvis.auth.UserStore;
import com.cegesoft.jarvis.radio.RadioScan;

import java.io.BufferedReader;
import java.io.Console;
import java.io.InputStreamReader;
import java.util.Arrays;

/**
 * Created by HoxiSword on 09/04/2020 for JARVIS
 */
public class Launcher {

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("scan")) {
            RadioScan.run(System.out);
            return;
        }
        if (args.length > 0 && args[0].equals("user")) {
            System.exit(setUserPassword(args));
            return;
        }
        // Registered first so that a SIGTERM (docker stop, systemd) received during boot still saves the state.
        Runtime.getRuntime().addShutdownHook(new Thread(Jarvis::stopJarvis, "JARVIS-SHUTDOWN"));
        try {
            Jarvis.startJarvis();
        } catch (RuntimeException e) {
            Jarvis.getLogger().error("JARVIS failed to start", e);
            System.exit(1);
        }
    }

    /**
     * "user <name>" : creates a web app account, or resets its password (docker compose run --rm jarvis user <name>).
     */
    private static int setUserPassword(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage : user <name>");
            return 2;
        }
        char[] password;
        Console console = System.console();
        if (console != null) {
            password = console.readPassword("Password for %s : ", args[1]);
            char[] confirmation = console.readPassword("Password again : ");
            if (password == null || !Arrays.equals(password, confirmation)) {
                System.err.println("The passwords are different.");
                return 1;
            }
        } else {
            // Without a terminal (echo "..." | java -jar JARVIS.jar user name) : first line of the input
            String line = new BufferedReader(new InputStreamReader(System.in)).readLine();
            password = line == null ? new char[0] : line.toCharArray();
        }
        try {
            UserStore users = new UserStore(JarvisConfig.fromEnvironment().getDataDir());
            boolean existed = users.find(args[1]) != null;
            users.setPassword(args[1], password);
            System.out.println((existed ? "Password changed for " : "Account created : ") + UserStore.normalize(args[1]));
            return 0;
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            return 1;
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
