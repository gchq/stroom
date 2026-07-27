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

import stroom.ui.config.shared.UiConfig;
import stroom.ui.config.shared.UserPreferencesService;
import stroom.util.shared.IsServlet;
import stroom.util.shared.ResourcePaths;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Set;

/**
 * Serves the page that a user lands on after following the password reset link that was emailed to
 * them. Only used by the internal identity provider.
 */
public class ResetPasswordServlet extends AppServlet implements IsServlet {

    private static final Set<String> PATH_SPECS = Set.of(ResourcePaths.RESET_PASSWORD_PATH);

    @Inject
    ResetPasswordServlet(final Provider<UiConfig> uiConfigProvider,
                         final Provider<UserPreferencesService> userPreferencesServiceProvider) {
        super(uiConfigProvider, userPreferencesServiceProvider);
    }

    String getScript() {
        return "ui/stroom/stroom.nocache.js";
    }

//    @Override
//    boolean useBootstrap() {
//        // Anyone reaching this page cannot sign in, which is the whole point of it, so it must load the
//        // GWT script directly. Checking authentication first would redirect them to the IdP.
//        return false;
//    }

    @Override
    public Set<String> getPathSpecs() {
        return PATH_SPECS;
    }

    @Override
    public String getName() {
        return ResourcePaths.RESET_PASSWORD_SERVLET_NAME;
    }
}
