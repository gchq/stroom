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
import io.dropwizard.setup.Bootstrap;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.Config;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import java.util.EnumSet;

public class App extends Application<Config> {
    private final Logger LOGGER = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) throws Exception {
        // Hibernate requires JBoss Logging. The SLF4J API jar wasn't being detected so this sets it manually.
        System.setProperty("org.jboss.logging.provider", "slf4j");
        new App().run(args);
    }

    @Override
    public void initialize(Bootstrap<Config> bootstrap) {
        bootstrap.addBundle(new AssetsBundle("/ui", "/", "stroom.jsp", "ui"));
//        bootstrap.addBundle(new AssetsBundle("/swagger", "/", "swagger.json", "swaggerSpec"));
    }

    @Override
    public void run(Config configuration, io.dropwizard.setup.Environment environment) throws Exception {
        // The order in which the following are run is important.
        Environment.configure(environment);
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
    }

    private static final void configureCors(io.dropwizard.setup.Environment environment) {
        FilterRegistration.Dynamic cors = environment.servlets().
                addFilter("CORS", CrossOriginFilter.class);
        cors.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, new String[]{"/*"});
        cors.setInitParameter(
                CrossOriginFilter.ACCESS_CONTROL_ALLOW_METHODS_HEADER, "GET,PUT,POST,DELETE,OPTIONS");
        cors.setInitParameter(CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, "*");
        cors.setInitParameter(CrossOriginFilter.ACCESS_CONTROL_ALLOW_HEADERS_HEADER, "*");
        cors.setInitParameter(CrossOriginFilter.ACCESS_CONTROL_ALLOW_CREDENTIALS_HEADER, "true");
    }
}
