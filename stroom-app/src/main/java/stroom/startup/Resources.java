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

import io.dropwizard.jersey.setup.JerseyEnvironment;
import org.eclipse.jetty.servlet.ServletHolder;
import org.springframework.context.ApplicationContext;
import stroom.index.shared.IndexService;
import stroom.resources.AuthResource;
import stroom.resources.SearchResource;
import stroom.search.server.SearchResultCreatorManager;
import stroom.security.server.AuthenticationService;
import stroom.security.server.AuthorisationService;
import stroom.util.upgrade.UpgradeDispatcherServlet;

import javax.servlet.ServletException;

public class Resources {

    private final SearchResource searchResource;
    private final AuthResource authResource;

    public Resources(JerseyEnvironment jersey, ServletHolder upgradeDispatcherServlerHolder){
        searchResource = new SearchResource();
        jersey.register(searchResource);

        authResource = new AuthResource();
        jersey.register(authResource);

        new Thread(() -> register(upgradeDispatcherServlerHolder)).start();

    }

    public void register(ServletHolder upgradeDispatcherServletHolder) {

        boolean apisAreNotYetConfigured = true;
        while (apisAreNotYetConfigured) {
            try {
                // This checks to see if the servlet has started. It'll throw an exception if it has.
                // I don't know of another way to check to see if it's ready.
                // If we try and get the servlet manually it'll fail to initialise because it won't have the ServletContext;
                // i.e. we need to let the Spring lifecycle stuff take care of this for us.
                upgradeDispatcherServletHolder.ensureInstance();

                UpgradeDispatcherServlet servlet = (UpgradeDispatcherServlet) upgradeDispatcherServletHolder.getServlet();
                ApplicationContext applicationContext = servlet.getWebApplicationContext();

                if (applicationContext != null) {
                    configureSearchResource(applicationContext);
                    configureAuthenticationResource(applicationContext);
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

    private void configureSearchResource(ApplicationContext applicationContext){
        SearchResultCreatorManager searchResultCreatorManager = applicationContext.getBean(SearchResultCreatorManager.class);
        IndexService indexService = applicationContext.getBean(IndexService.class);
        searchResource.setIndexService(indexService);
        searchResource.setSearchResultCreatorManager(searchResultCreatorManager);
    }

    private void configureAuthenticationResource(ApplicationContext applicationContext){
        AuthenticationService authenticationService = applicationContext.getBean(AuthenticationService.class);
        authResource.setAuthenticationService(authenticationService);

        AuthorisationService authorisationService = applicationContext.getBean(AuthorisationService.class);
        authResource.setAuthorisationService(authorisationService);
    }
}