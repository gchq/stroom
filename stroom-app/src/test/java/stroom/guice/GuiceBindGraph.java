package stroom.guice;

import stroom.app.guice.AppModule;
import stroom.app.guice.BootStrapModule;
import stroom.config.app.AppConfig;
import stroom.config.app.Config;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.grapher.graphviz.GraphvizGrapher;
import com.google.inject.grapher.graphviz.GraphvizModule;
import io.dropwizard.core.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

public class GuiceBindGraph {

    private static final Logger LOGGER = LoggerFactory.getLogger(GuiceBindGraph.class);

    public static void main(final String[] args) throws IOException {
        // This produces a dot file but xdot and dot seem unable to deal with it, probably
        // due to the size.
        new GuiceBindGraph().produceGraph();
    }

    public void produceGraph() throws IOException {

        final Config config = new Config();
        config.setYamlAppConfig(new AppConfig());
        final Injector injector = Guice.createInjector(
                new BootStrapModule(
                        config,
                        new Environment("Test Environment"),
                        Path.of("dummy/path/to/config.yml")),
                new AppModule());

        graph(("build/AppModule.dot"), injector);
    }

    private void graph(final String filename, final Injector demoInjector) throws IOException {
        final Path dotFile = Paths.get(filename);
        final PrintWriter out = new PrintWriter(filename, StandardCharsets.UTF_8);

        final Injector injector = Guice.createInjector(new GraphvizModule());
        final GraphvizGrapher grapher = injector.getInstance(GraphvizGrapher.class);
        grapher.setOut(out);
        grapher.setRankdir("TB");

        grapher.graph(demoInjector);
        LOGGER.info("Produced GraphViz file {}, view with xdot", dotFile.toAbsolutePath().normalize());
    }
}
