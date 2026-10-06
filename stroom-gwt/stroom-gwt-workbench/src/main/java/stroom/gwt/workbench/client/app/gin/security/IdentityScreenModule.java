/*
 * Copyright 2026 Crown Copyright
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

package stroom.gwt.workbench.client.app.gin.security;

import stroom.security.identity.client.presenter.AccountsPresenter;
import stroom.security.identity.client.presenter.AccountsPresenter.AccountsView;
import stroom.security.identity.client.presenter.ChangePasswordPresenter;
import stroom.security.identity.client.presenter.ChangePasswordPresenter.ChangePasswordView;
import stroom.security.identity.client.presenter.EditAccountPresenter;
import stroom.security.identity.client.presenter.EditAccountPresenter.EditAccountView;
import stroom.security.identity.client.view.AccountsViewImpl;
import stroom.security.identity.client.view.ChangePasswordViewImpl;
import stroom.security.identity.client.view.EditAccountViewImpl;

import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;

/// The presenter and view bindings of Stroom's `ChangePasswordModule`
/// (`stroom/security/identity/client/gin/ChangePasswordModule.java`), without its plugins and app services.
/// Presenters that Stroom binds with a GWTP proxy are bound as presenter widgets with a null
/// proxy: a story shows one by registering it as the handler of its event, as the proxy would.
public class IdentityScreenModule extends AbstractPresenterModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
        // CurrentPasswordPresenter is left out: it needs Stroom's CurrentUser (not the harness's
        // ClientSecurityContext), whose graph (the splash screen, the current activity) the stories
        // don't have, so its story creates it with `new`.
        bindPresenterWidget(ChangePasswordPresenter.class,
                ChangePasswordView.class,
                ChangePasswordViewImpl.class);
        bindPresenterWidget(AccountsPresenter.class,
                AccountsView.class,
                AccountsViewImpl.class);
        bindPresenterWidget(EditAccountPresenter.class,
                EditAccountView.class,
                EditAccountViewImpl.class);
    }
}

