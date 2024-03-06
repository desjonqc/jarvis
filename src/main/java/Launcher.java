import com.cegesoft.jarvis.Jarvis;

/**
 * Created by HoxiSword on 09/04/2020 for JARVIS
 */
public class Launcher {

    public static void main(String[] args) {
        Jarvis.startJarvis();
        Runtime.getRuntime().addShutdownHook(new Thread(Jarvis::stopJarvis));
    }
}
