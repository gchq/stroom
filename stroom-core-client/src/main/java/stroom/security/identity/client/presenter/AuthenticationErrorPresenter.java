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

package stroom.security.identity.client.presenter;

import stroom.security.identity.client.presenter.AuthenticationErrorPresenter.AuthenticationErrorView;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.ExtendedUiConfig;
import stroom.util.shared.NullSafe;
import stroom.widget.presenter.client.View;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Visibility;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

public class AuthenticationErrorPresenter
        extends SignInPagePresenter<AuthenticationErrorView> {

    private final Element loadingText;
    private final Element loading;
    private final UiConfigCache uiConfigCache;

    @Inject
    public AuthenticationErrorPresenter(final EventBus eventBus,
                                        final AuthenticationErrorView authenticationErrorView,
                                        final UiConfigCache uiConfigCache) {
        super(eventBus, authenticationErrorView);
        this.uiConfigCache = uiConfigCache;

        loadingText = RootPanel.get("loadingText").getElement();
        loading = RootPanel.get("loading").getElement();
    }

    @Override
    public void onShown() {
        uiConfigCache.get(extendedUiConfig -> {
            final String error = Window.Location.getParameter("error");
            getView().setErrorText(error);
            getView().focus();
            final String authErrorMessage = NullSafe.get(extendedUiConfig, ExtendedUiConfig::getAuthErrorMessage);
            if (NullSafe.isNonBlankString(authErrorMessage)) {
                getView().setGenericMessage(SafeHtmlUtils.fromTrustedString(authErrorMessage));
            }
            loading.getStyle().setOpacity(0);
            // Hidden, not just transparent, so screen readers stop reading "Loading..."
            loading.getStyle().setVisibility(Visibility.HIDDEN);
        });
    }


    // --------------------------------------------------------------------------------

    // --------------------------------------------------------------------------------


    public interface AuthenticationErrorView extends View, Focus {

        void setErrorText(final String errorText);

        void setGenericMessage(final SafeHtml genericMessage);
    }
}
