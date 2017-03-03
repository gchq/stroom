package stroom.startup;

import io.dropwizard.setup.Environment;
import org.eclipse.jetty.servlet.ServletHolder;

public class Servlets {

    final ServletHolder upgradeDispatcherServletHolder;
    final ServletHolder loggingServletHolder;

    public Servlets(Environment environment){
        loggingServletHolder = Servlets.newLog4jServlet();
        upgradeDispatcherServletHolder = Servlets.newUpgradeDispatcherServlet();

        addServlet(environment, loggingServletHolder, 1,"", null);
        addServlet(environment, upgradeDispatcherServletHolder, 3, "/*.rpc", new String[]{
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
            Environment environment,
            ServletHolder servletHolder,
            int loadOnStartup,
            String servletMapping,
            String[] furtherServletMappings){
        environment.getApplicationContext().addServlet(servletHolder, servletMapping);
        if(furtherServletMappings != null && furtherServletMappings.length > 0){
            servletHolder.getRegistration().addMapping(furtherServletMappings);
        }
        servletHolder.getRegistration().setLoadOnStartup(loadOnStartup);
    }

    private static ServletHolder newLog4jServlet() {
        ServletHolder servlet = new ServletHolder(stroom.util.logging.Log4JServlet.class);
        servlet.setName("log4j");
        servlet.setInitParameter("log4j", "classpath:log4j.xml");
        return servlet;
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
                "                stroom.statistics.spring.StatisticsConfiguration,\n" +
                "                stroom.security.spring.SecurityConfiguration");
        return servlet;
    }
}
