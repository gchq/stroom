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
    private static final String SCRIPT = "@SCRIPT@";
    private static final String ROOT_CLASS = "@ROOT_CLASS@";

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
        html = html.replace(SCRIPT, getScript());

        pw.write(html);
        pw.close();
    }

    abstract String getScript();


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
