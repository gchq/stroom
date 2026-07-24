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

package stroom.core.servlet;

import stroom.ui.config.shared.ThemeCssUtil;
import stroom.ui.config.shared.UiConfig;
import stroom.ui.config.shared.UserPreferences;
import stroom.ui.config.shared.UserPreferencesService;
import stroom.util.io.CloseableUtil;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ResourcePaths;

import jakarta.inject.Provider;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;

public abstract class AppServlet extends HttpServlet {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AppServlet.class);

    private static final String TITLE = "@TITLE@";
    private static final String ON_CONTEXT_MENU = "@ON_CONTEXT_MENU@";
    private static final String ROOT_CLASS = "@ROOT_CLASS@";
    private static final String BOOTSTRAP = "@BOOTSTRAP@";

    private final Provider<UiConfig> uiConfigProvider;
    private final Provider<UserPreferencesService> userPreferencesServiceProvider;

    AppServlet(final Provider<UiConfig> uiConfigProvider,
               final Provider<UserPreferencesService> userPreferencesServiceProvider) {
        this.uiConfigProvider = uiConfigProvider;
        this.userPreferencesServiceProvider = userPreferencesServiceProvider;
    }

    private String getHtmlTemplate() {
        return LazyTemplateHolder.getTemplate();
    }

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
            throws IOException {

        LOGGER.debug(() -> LogUtil.message("Using servlet {} for requestURI: {}, servletPath: {}",
                this.getClass().getSimpleName(), request.getServletPath(), request.getServletPath()));

        final PrintWriter pw = response.getWriter();
        response.setContentType("text/html");

        final UserPreferencesService userPreferencesService = userPreferencesServiceProvider.get();
        final UiConfig uiConfig = uiConfigProvider.get();

        final UserPreferences userPreferences = userPreferencesService.fetchDefault();
        final String classNames = ThemeCssUtil.getCurrentPreferenceClasses(userPreferences);

        String html = getHtmlTemplate();
        html = html.replace(ROOT_CLASS, classNames);
        html = html.replace(TITLE, uiConfig.getHtmlTitle());
        html = html.replace(ON_CONTEXT_MENU, uiConfig.getOncontextmenu());
        if (useBootstrap()) {
            html = html.replace(BOOTSTRAP, getBootstrapScript(getScript()));
        } else {
            // Load the GWT script directly without auth check (e.g., for the sign-in page)
            html = html.replace(BOOTSTRAP,
                    "<script type=\"text/javascript\" src='" + getScript() + "'></script>");
        }

        pw.write(html);
        pw.close();
    }

    abstract String getScript();

    /**
     * Whether to use the bootstrap auth-check script that verifies authentication
     * via the BFF status endpoint before loading the GWT application.
     * Subclasses can override to return false if the page should load the GWT
     * script directly without an auth check (e.g., the sign-in page, which IS
     * the login UI and must not redirect to the IdP).
     */
    boolean useBootstrap() {
        return true;
    }

    /**
     * Returns an inline JavaScript snippet that checks authentication status
     * via the BFF auth flow endpoint before loading the GWT application script.
     * If the user is not authenticated, the browser is redirected to the IdP.
     */
    private String getBootstrapScript(final String gwtScriptPath) {
        // Build the status path from the same shared constant the resource is served under, so the
        // bootstrap fetch can never drift from AuthFlowResource's @Path (e.g. the noauth removal).
        final String statusPath = ResourcePaths.buildAuthenticatedApiPath(
                ResourcePaths.AUTH_FLOW_PATH, "/status");
        return """
                <script type="text/javascript">
                (function() {
                  fetch('%s?redirect_uri='
                    + encodeURIComponent(window.location.href))
                    .then(function(resp) {
                      if (!resp.ok) throw new Error('Auth check failed: ' + resp.status);
                      return resp.json();
                    })
                    .then(function(auth) {
                      if (auth.authenticated) {
                        var s = document.createElement('script');
                        s.type = 'text/javascript';
                        s.src = '%s';
                        document.head.appendChild(s);
                      } else {
                        window.location.href = auth.redirectUrl;
                      }
                    })
                    .catch(function(err) {
                      var el = document.getElementById('loadingText');
                      if (el) el.textContent = 'Authentication error: ' + err.message;
                      console.error('Bootstrap auth check failed', err);
                    });
                })();
                </script>""".formatted(statusPath, gwtScriptPath);
    }


    // --------------------------------------------------------------------------------


    /**
     * Initialization-on-demand holder idiom
     */
    private static class LazyTemplateHolder {

        private static final String TEMPLATE;

        static {
            final InputStream inputStream = AppServlet.class.getResourceAsStream("app.html");
            TEMPLATE = StreamUtil.streamToString(inputStream);
            CloseableUtil.closeLogAndIgnoreException(inputStream);
        }

        private static String getTemplate() {
            return TEMPLATE;
        }
    }
}
