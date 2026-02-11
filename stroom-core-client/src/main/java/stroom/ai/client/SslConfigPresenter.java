/*
 * Copyright 2025 Crown Copyright
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

package stroom.ai.client;

import stroom.ai.client.SslConfigPresenter.SslConfigView;
import stroom.document.client.event.DirtyUiHandlers;
import stroom.entity.client.presenter.MarkdownConverter;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.security.client.api.ClientSecurityContext;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class SslConfigPresenter extends MyPresenterWidget<SslConfigView> {

    @Inject
    public SslConfigPresenter(final EventBus eventBus,
                              final SslConfigView view,
                              final DocSelectionBoxPresenter docSelectionBoxPresenter,
                              final MarkdownConverter markdownConverter,
                              final AskStroomAiClient askStroomAiClient,
                              final ClientSecurityContext clientSecurityContext,
                              final Provider<AskStroomAiConfigPresenter> askStroomAiConfigPresenterProvider) {
        super(eventBus, view);

    }

    public interface SslConfigView extends View, Focus, HasUiHandlers<DirtyUiHandlers> {

        void setKeyStoreView(View view);

        void setTrustStoreView(View view);

        boolean isHostnameVerificationEnabled();

        void setHostnameVerificationEnabled(boolean enabled);

        String getSslProtocol();

        void setSslProtocol(String sslProtocol);
    }
}
