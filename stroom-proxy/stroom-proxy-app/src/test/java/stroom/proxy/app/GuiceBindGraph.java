/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.proxy.app;

import stroom.proxy.app.guice.ProxyModule;

import com.codahale.metrics.health.HealthCheckRegistry;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.grapher.graphviz.GraphvizGrapher;
import com.google.inject.grapher.graphviz.GraphvizModule;
import io.dropwizard.core.setup.Environment;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

// FIXME : BROKEN BY JAVA21
@Disabled
public class GuiceBindGraph {

    private static final Logger LOGGER = LoggerFactory.getLogger(GuiceBindGraph.class);

    @Test
    public void produceGraph() throws IOException {

        final Environment environment = Mockito.mock(Environment.class);
        Mockito.when(environment.healthChecks())
                .thenReturn(new HealthCheckRegistry());

        final Config config = new Config();
        config.setProxyConfig(new ProxyConfig());
        final Injector injector = Guice.createInjector(
                new ProxyModule(config, environment, Path.of("dummy/path/to/config.yml")));

        graph(("build/ProxyModule.dot"), injector);
    }

    private void graph(final String filename, final Injector demoInjector) throws IOException {
        final Path dotFile = Paths.get(filename);
        final PrintWriter out = new PrintWriter(new File(filename), StandardCharsets.UTF_8);

        final Injector injector = Guice.createInjector(new GraphvizModule());
        final GraphvizGrapher grapher = injector.getInstance(GraphvizGrapher.class);
        grapher.setOut(out);
        grapher.setRankdir("TB");

        grapher.graph(demoInjector);
        LOGGER.info("Produced GraphViz file {}, view with xdot", dotFile.toAbsolutePath().normalize());
    }
}
