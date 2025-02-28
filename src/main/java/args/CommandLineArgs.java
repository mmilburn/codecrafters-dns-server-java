package args;

import com.beust.jcommander.Parameter;

public class CommandLineArgs {

    @Parameter(names = "--resolver", description = "Resolver to forward queries to")
    private String resolver;

    public String getResolver() {
        return resolver;
    }
}
