package stroom.startup;

import io.dropwizard.setup.Environment;
import org.eclipse.jetty.servlet.ServletHolder;

public class Servlets {

    /**
     * Load all servlets.
     */
    @SuppressWarnings("unchecked")
    static void loadInto(Environment environment) throws ClassNotFoundException {
        environment.getApplicationContext().addServlet(Servlets.createLog4jServlet(), "");
        environment.getApplicationContext().addServlet(Servlets.createUpgradeDispatcherServlet(), "/thing/*");
        environment.getApplicationContext().addServlet(Servlets.createServletContainer(), "/rest/*");
    }

    private static ServletHolder createLog4jServlet() {
        ServletHolder servlet = new ServletHolder(stroom.util.logging.Log4JServlet.class);
        servlet.setName("log4j");
        servlet.setInitParameter("log4j", "classpath:log4j.xml");
        return servlet;
    }

    private static ServletHolder createUpgradeDispatcherServlet() {
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
//        if(servlet.getServletHandler() == null){
//            System.out.println("NULL!");
//        }
//        servlet.getRegistration().addMapping(
//                "*.rpc",
//                "/dynamic.css",
//                "/script",
//                "/datafeed",
//                "/datafeed/*",
//                "/resourcestore/*",
//                "/export/*",
//                "/echo",
//                "/debug",
//                "/status",
//                "/sessionList",
//                "/gwtRequest");
        return servlet;
    }

    private static ServletHolder createServletContainer(){
        ServletHolder servlet = new ServletHolder(org.glassfish.jersey.servlet.ServletContainer.class);
        servlet.setName("SpringApplication");
        servlet.setInitParameter("javax.ws.rs.Application", "stroom.search.spring.JerseyApplication");
        servlet.setInitParameter("com.sun.jersey.api.json.POJOMappingFeature", "true");
        return servlet;
    }
}
