package stroom.guice;

import stroom.app.guice.AppModule;
import stroom.app.guice.BootstrapModule;
import stroom.config.app.AppConfig;
import stroom.config.app.Config;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.grapher.graphviz.GraphvizGrapher;
import com.google.inject.grapher.graphviz.GraphvizModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

public class GuiceBindGraph {

    private static final Logger LOGGER = LoggerFactory.getLogger(GuiceBindGraph.class);

    public static void main(String[] args) throws IOException {
        // This produces a dot file but xdot and dot seem unable to deal with it, probably
        // due to the size.
        new GuiceBindGraph().produceGraph();
    }

    public void produceGraph() throws IOException {

        Config config = new Config();
        config.setAppConfig(new AppConfig());
        final Injector injector = Guice.createInjector(
                new BootstrapModule(config, Path.of("dummy/path/to/config.yml")),
                new AppModule());

        graph(("build/AppModule.dot"), injector);
    }

    private void graph(String filename, Injector demoInjector) throws IOException {
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
