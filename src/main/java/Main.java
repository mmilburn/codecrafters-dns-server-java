import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import server.DNSServer;

class CommandLineArgs {

    @Parameter(names = "--resolver", description = "Resolver to forward queries to")
    private String resolver;

    public String getResolver() {
        return resolver;
    }
}

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
