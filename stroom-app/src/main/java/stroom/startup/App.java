/*
 * Copyright 2017 Crown Copyright
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

package stroom.startup;

import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.setup.Bootstrap;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.Config;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import java.util.EnumSet;
import java.util.concurrent.CountDownLatch;

public class App extends Application<Config> {
    private final Logger LOGGER = LoggerFactory.getLogger(App.class);

    private final CountDownLatch applicationContextReadyLatch = new CountDownLatch(1);

    public static void main(String[] args) throws Exception {
        // Hibernate requires JBoss Logging. The SLF4J API jar wasn't being detected so this sets it manually.
        System.setProperty("org.jboss.logging.provider", "slf4j");
        new App().run(args);
    }

    @Override
    public void initialize(Bootstrap<Config> bootstrap) {
        // This allows us to use templating in the YAML configuration.
        bootstrap.setConfigurationSourceProvider(new SubstitutingSourceProvider(
            bootstrap.getConfigurationSourceProvider(),
            new EnvironmentVariableSubstitutor(false)));

        bootstrap.addBundle(new AssetsBundle("/ui", "/", "stroom.jsp", "ui"));
    }

    @Override
    public void run(Config configuration, io.dropwizard.setup.Environment environment) throws Exception {
        // The order in which the following are run is important.
        Environment.configure(environment);
        configureCors(environment);
        SpringContexts springContexts = new SpringContexts();
        Servlets servlets = new Servlets(environment.getApplicationContext());
        Filters filters = new Filters(environment.getApplicationContext());
        Listeners listeners = new Listeners(environment.servlets(), springContexts.rootContext);
        springContexts.start(environment, configuration);
        ServletMonitor servletMonitor = new ServletMonitor((servlets.upgradeDispatcherServletHolder));
        Resources resources = new Resources(environment.jersey(), servletMonitor);
        HealthChecks.registerHealthChecks(environment.healthChecks(), resources, servletMonitor);
        AdminTasks.registerAdminTasks(environment);
        configureCors(environment);
        //Allows us to expose a method so other threads can block until the application has fully started
        servletMonitor.registerApplicationContextListener(
                applicationContext -> {
                    applicationContextReadyLatch.countDown();
                    LOGGER.debug("applicationContextReadyLatch counted down - application started");
                });
    }

    private static final void configureCors(io.dropwizard.setup.Environment environment) {
        FilterRegistration.Dynamic cors = environment.servlets().addFilter("CORS", CrossOriginFilter.class);
        cors.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, new String[]{"/*"});
        cors.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM, "GET,PUT,POST,DELETE,OPTIONS");
        cors.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, "*");
        cors.setInitParameter(CrossOriginFilter.ALLOWED_HEADERS_PARAM, "*");
    }

    /**
     * @return Will block until the application has fully started
     */
    public void waitForApplicationStart() throws InterruptedException {
        LOGGER.debug("Waiting for the application to start");
        applicationContextReadyLatch.await();
    }

}
