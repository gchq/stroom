package stroom.startup;

import org.eclipse.jetty.servlet.ServletHolder;
import org.springframework.context.ApplicationContext;
import stroom.resources.SearchResource;
import stroom.index.shared.IndexService;
import stroom.search.server.SearchResultCreatorManager;
import stroom.util.upgrade.UpgradeDispatcherServlet;

import javax.servlet.ServletException;

public class Resources {

    private final SearchResource searchResource;

    public Resources(io.dropwizard.setup.Environment environment, ServletHolder upgradeDispatcherServlerHolder){
        searchResource = new SearchResource();
        environment.jersey().register(searchResource);

        new Thread(() -> register(upgradeDispatcherServlerHolder, searchResource))
                .start();

    }

    public void register(ServletHolder upgradeDispatcherServletHolder, SearchResource searchResource) {

        boolean apisAreNotYetConfigured = true;
        while (apisAreNotYetConfigured) {
            try {
                // This checks to see if the servlet has started. It'll throw an exception if it has.
                // I don't know of another way to check to see if it's ready.
                // If we try and get the servlet manually it'll fail to initialise because it won't have the ServletContext;
                // i.e. we need to let the Spring lifecycle stuff take care of this for us.
                upgradeDispatcherServletHolder.ensureInstance();

                UpgradeDispatcherServlet servlet = (UpgradeDispatcherServlet) upgradeDispatcherServletHolder.getServlet();
                ApplicationContext appContext = servlet.getWebApplicationContext();

                if (appContext != null) {
                    SearchResultCreatorManager searchResultCreatorManager = appContext.getBean(SearchResultCreatorManager.class);
                    IndexService indexService = appContext.getBean(IndexService.class);
                    searchResource.setIndexService(indexService);
                    searchResource.setSearchResultCreatorManager(searchResultCreatorManager);
                    apisAreNotYetConfigured = false;
                }
            } catch (ServletException e) {
                // This could be an UnavailableException, caused by ensureInstance().
                // We don't care, we're going to keep trying.
            }
        }
    }

    public SearchResource getSearchResource(){
        return searchResource;
    }
}