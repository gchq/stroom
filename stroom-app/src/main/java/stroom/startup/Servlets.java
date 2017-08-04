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
 *
 */

package stroom.startup;

import io.dropwizard.jetty.MutableServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import stroom.dashboard.server.logging.spring.EventLoggingConfiguration;
import stroom.dashboard.spring.DashboardConfiguration;
import stroom.index.spring.IndexConfiguration;
import stroom.pipeline.spring.PipelineConfiguration;
import stroom.ruleset.spring.RuleSetConfiguration;
import stroom.script.spring.ScriptConfiguration;
import stroom.search.spring.SearchConfiguration;
import stroom.security.spring.SecurityConfiguration;
import stroom.spring.CoreClientConfiguration;
import stroom.spring.MetaDataStatisticConfiguration;
import stroom.spring.PersistenceConfiguration;
import stroom.spring.ScopeConfiguration;
import stroom.spring.ServerComponentScanConfiguration;
import stroom.spring.ServerConfiguration;
import stroom.statistics.spring.StatisticsConfiguration;
import stroom.visualisation.spring.VisualisationConfiguration;

public class Servlets {

    final ServletHolder upgradeDispatcherServletHolder;

    public Servlets(MutableServletContextHandler servletContextHandler) {
        upgradeDispatcherServletHolder = Servlets.newUpgradeDispatcherServlet();

        addServlet(servletContextHandler, upgradeDispatcherServletHolder, 3, "*.rpc", new String[]{
                "/dispatch.rpc",
                "/dynamic.css",
                "/script",
                "/datafeed",
                "/datafeed/*",
                "/resourcestore/*",
                "/importfile.rpc",
                "/export/*",
                "/echo",
                "/debug",
                "/status",
                "/sessionList",
                "/gwtRequest"
        });
    }

    private static void addServlet(
            MutableServletContextHandler servletContextHandler,
            ServletHolder servletHolder,
            int loadOnStartup,
            String servletMapping,
            String[] furtherServletMappings) {
        servletContextHandler.addServlet(servletHolder, servletMapping);
        if (furtherServletMappings != null && furtherServletMappings.length > 0) {
            servletHolder.getRegistration().addMapping(furtherServletMappings);
        }
        servletHolder.getRegistration().setLoadOnStartup(loadOnStartup);
    }

    private static ServletHolder newUpgradeDispatcherServlet() {
        final String configLocation = createConfigLocationString(
                ScopeConfiguration.class,
                PersistenceConfiguration.class,
                ServerComponentScanConfiguration.class,
                ServerConfiguration.class,
                RuleSetConfiguration.class,
                EventLoggingConfiguration.class,
                PipelineConfiguration.class,
                IndexConfiguration.class,
                SearchConfiguration.class,
                ScriptConfiguration.class,
                VisualisationConfiguration.class,
                DashboardConfiguration.class,
                CoreClientConfiguration.class,
                MetaDataStatisticConfiguration.class,
                StatisticsConfiguration.class,
                SecurityConfiguration.class
        );

        ServletHolder servlet = new ServletHolder(stroom.util.upgrade.UpgradeDispatcherServlet.class);
        servlet.setName("spring");
        servlet.setInitParameter("spring.profiles.active", "production,PROD_SECURITY");
        servlet.setInitParameter("upgrade-class", "stroom.upgrade.StroomUpgradeHandler");
        servlet.setInitParameter("contextClass", "org.springframework.web.context.support.AnnotationConfigWebApplicationContext");
        servlet.setInitParameter("contextConfigLocation", configLocation);
        return servlet;
    }

    private static String createConfigLocationString(final Class<?>... classes) {
        final StringBuilder sb = new StringBuilder();
        for (final Class<?> clazz : classes) {
            if (sb.length() > 0) {
                sb.append("\n,");
            }
            sb.append(clazz.getCanonicalName());
        }
        return sb.toString();
    }
}
