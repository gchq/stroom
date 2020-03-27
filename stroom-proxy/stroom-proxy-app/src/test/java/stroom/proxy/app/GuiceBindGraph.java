package stroom.proxy.app;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.grapher.graphviz.GraphvizGrapher;
import com.google.inject.grapher.graphviz.GraphvizModule;
import io.dropwizard.setup.Environment;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.proxy.app.guice.ProxyModule;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;

public class GuiceBindGraph {

    private static final Logger LOGGER = LoggerFactory.getLogger(GuiceBindGraph.class);

    @Test
    public void produceGraph() throws IOException {

        Environment environment = Mockito.mock(Environment.class);

        Config config = new Config();
        config.setProxyConfig(new ProxyConfig());
        Injector injector = Guice.createInjector(new ProxyModule(config, environment));

        graph(("build/ProxyModule.dot"), injector);
    }

    private void graph(String filename, Injector demoInjector) throws IOException {
        Path dotFile = Paths.get(filename);
        PrintWriter out = new PrintWriter(new File(filename), "UTF-8");

        Injector injector = Guice.createInjector(new GraphvizModule());
        GraphvizGrapher grapher = injector.getInstance(GraphvizGrapher.class);
        grapher.setOut(out);
        grapher.setRankdir("TB");

        grapher.graph(demoInjector);
        LOGGER.info("Produced GraphViz file {}, view with xdot", dotFile.toAbsolutePath().normalize());
    }
}
