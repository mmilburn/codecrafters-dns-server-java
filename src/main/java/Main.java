import args.CommandLineArgs;
import com.beust.jcommander.JCommander;
import server.DNSServer;

public class Main {
    public static void main(String[] args) {
        CommandLineArgs commandLineArgs = new CommandLineArgs();
        JCommander.newBuilder()
                .addObject(commandLineArgs)
                .build()
                .parse(args);

        DNSServer server = new DNSServer(commandLineArgs.getResolver());
        server.start();
    }
}
