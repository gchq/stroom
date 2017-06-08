/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.startup;

import io.dropwizard.jetty.MutableServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

public class Servlets {

    final ServletHolder upgradeDispatcherServletHolder;

    public Servlets(MutableServletContextHandler servletContextHandler){
        upgradeDispatcherServletHolder = Servlets.newUpgradeDispatcherServlet();

        addServlet(servletContextHandler, upgradeDispatcherServletHolder, 3, "*.rpc", new String[]{
                "/dispatch.rpc",
                "/dynamic.css",
                "/script",
                "/datafeed",
                "/datafeed/*",
                "/resourcestore/*",
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
            String[] furtherServletMappings){
        servletContextHandler.addServlet(servletHolder, servletMapping);
        if(furtherServletMappings != null && furtherServletMappings.length > 0){
            servletHolder.getRegistration().addMapping(furtherServletMappings);
        }
        servletHolder.getRegistration().setLoadOnStartup(loadOnStartup);
    }

    private static ServletHolder newUpgradeDispatcherServlet() {
        ServletHolder servlet = new ServletHolder(stroom.util.upgrade.UpgradeDispatcherServlet.class);
        servlet.setName("spring");
        servlet.setInitParameter("spring.profiles.active", "production,PROD_SECURITY");
        servlet.setInitParameter("upgrade-class", "stroom.upgrade.StroomUpgradeHandler");
        servlet.setInitParameter("contextClass", "org.springframework.web.context.support.AnnotationConfigWebApplicationContext");
        servlet.setInitParameter("contextConfigLocation", "stroom.spring.ScopeConfiguration,\n" +
                "                stroom.spring.PersistenceConfiguration,\n" +
                "                stroom.spring.ServerComponentScanConfiguration,\n" +
                "                stroom.spring.ServerConfiguration,\n" +
                "                stroom.spring.CachedServiceConfiguration,\n" +
                "                stroom.logging.spring.EventLoggingConfiguration,\n" +
                "                stroom.index.spring.IndexConfiguration,\n" +
                "                stroom.search.spring.SearchConfiguration,\n" +
                "                stroom.script.spring.ScriptConfiguration,\n" +
                "                stroom.visualisation.spring.VisualisationConfiguration,\n" +
                "                stroom.dashboard.spring.DashboardConfiguration,\n" +
                "                stroom.spring.CoreClientConfiguration,\n" +
                "                stroom.spring.MetaDataStatisticConfiguration,\n" +
                "                stroom.statistics.spring.StatisticsConfiguration,\n" +
                "                stroom.security.spring.SecurityConfiguration");
        return servlet;
    }
}
