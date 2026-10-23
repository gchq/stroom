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

package stroom.gwt.workbench.client.app.gin;

import stroom.credentials.client.presenter.AccessTokenSecretPresenter;
import stroom.credentials.client.presenter.AccessTokenSecretPresenter.AccessTokenSecretView;
import stroom.credentials.client.presenter.CredentialSettingsPresenter;
import stroom.credentials.client.presenter.CredentialSettingsPresenter.CredentialSettingsView;
import stroom.credentials.client.presenter.CredentialsManagerDialogPresenter;
import stroom.credentials.client.presenter.CredentialsPresenter;
import stroom.credentials.client.presenter.CredentialsPresenter.CredentialsView;
import stroom.credentials.client.presenter.KeyStoreFileSecretPresenter;
import stroom.credentials.client.presenter.KeyStoreFileSecretPresenter.KeyStoreFileSecretView;
import stroom.credentials.client.presenter.KeyStoreSecretPresenter;
import stroom.credentials.client.presenter.KeyStoreSecretPresenter.KeyStoreSecretView;
import stroom.credentials.client.presenter.ReplaceSecretPresenter;
import stroom.credentials.client.presenter.ReplaceSecretPresenter.ReplaceSecretView;
import stroom.credentials.client.presenter.SshKeySecretPresenter;
import stroom.credentials.client.presenter.SshKeySecretPresenter.SshKeySecretView;
import stroom.credentials.client.presenter.UsernamePasswordSecretPresenter;
import stroom.credentials.client.presenter.UsernamePasswordSecretPresenter.UsernamePasswordSecretView;
import stroom.credentials.client.view.AccessTokenSecretViewImpl;
import stroom.credentials.client.view.CredentialSettingsViewImpl;
import stroom.credentials.client.view.CredentialsManagerViewImpl;
import stroom.credentials.client.view.CredentialsViewImpl;
import stroom.credentials.client.view.KeyStoreFileSecretViewImpl;
import stroom.credentials.client.view.KeyStoreSecretViewImpl;
import stroom.credentials.client.view.ReplaceSecretViewImpl;
import stroom.credentials.client.view.SshKeySecretViewImpl;
import stroom.credentials.client.view.UsernamePasswordSecretViewImpl;
import stroom.widget.presenter.client.gin.AbstractPresenterModule;

/// The presenter and view bindings of Stroom's `CredentialsModule`
/// (`stroom/credentials/client/gin/CredentialsModule.java`), without its plugins and app services.
/// Presenters that Stroom binds with a listener are bound here as presenter widgets without one: a
/// story shows one by registering it as the handler of its event, as the listener would.
public class CredentialsScreenModule extends AbstractPresenterModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
        bindPresenterWidget(CredentialsPresenter.class,
                CredentialsView.class,
                CredentialsViewImpl.class);
        bindPresenterWidget(CredentialSettingsPresenter.class,
                CredentialSettingsView.class,
                CredentialSettingsViewImpl.class);
        bindPresenterWidget(CredentialsManagerDialogPresenter.class,
                CredentialsManagerDialogPresenter.CredentialsManagerDialogView.class,
                CredentialsManagerViewImpl.class);
        bindPresenterWidget(AccessTokenSecretPresenter.class,
                AccessTokenSecretView.class,
                AccessTokenSecretViewImpl.class);
        bindPresenterWidget(SshKeySecretPresenter.class,
                SshKeySecretView.class,
                SshKeySecretViewImpl.class);
        bindPresenterWidget(KeyStoreSecretPresenter.class,
                KeyStoreSecretView.class,
                KeyStoreSecretViewImpl.class);
        bindPresenterWidget(UsernamePasswordSecretPresenter.class,
                UsernamePasswordSecretView.class,
                UsernamePasswordSecretViewImpl.class);
        bindPresenterWidget(ReplaceSecretPresenter.class,
                ReplaceSecretView.class,
                ReplaceSecretViewImpl.class);
        bindPresenterWidget(KeyStoreFileSecretPresenter.class,
                KeyStoreFileSecretView.class,
                KeyStoreFileSecretViewImpl.class);
    }
}

