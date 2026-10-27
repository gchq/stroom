/*
 * Copyright 2024 Crown Copyright
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

package stroom.security.identity.client.component;

import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.ExtendedUiConfig;
import stroom.util.shared.NullSafe;

import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.Window;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// The authentication error page ([AuthenticationError]): the error in the URL, and the UI
/// config's message.
public class AuthenticationErrorController extends SignInPageController {

    private final AuthenticationError view;
    private final UiConfigCache uiConfigCache;

    /// Made by GIN, with its page.
    ///
    /// @param eventBus      The application's event bus.
    /// @param view          The page.
    /// @param uiConfigCache Gives the message every authentication error shows.
    @Inject
    public AuthenticationErrorController(final EventBus eventBus,
                                         final AuthenticationError view,
                                         final UiConfigCache uiConfigCache) {
        super(eventBus);
        this.view = view;
        this.uiConfigCache = uiConfigCache;
        initWidget(view);
    }

    @Override
    public void onShown() {
        uiConfigCache.get(extendedUiConfig -> {
            view.setErrorText(Window.Location.getParameter("error"));
            final String authErrorMessage = NullSafe.get(extendedUiConfig, ExtendedUiConfig::getAuthErrorMessage);
            if (NullSafe.isNonBlankString(authErrorMessage)) {
                view.setGenericMessage(SafeHtmlUtils.fromTrustedString(authErrorMessage));
            }
            hideLoading();
        });
    }
}
