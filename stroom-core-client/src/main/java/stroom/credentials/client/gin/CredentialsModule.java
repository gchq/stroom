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

package stroom.credentials.client.gin;

import stroom.core.client.gin.PluginModule;
import stroom.credentials.client.CredentialsPlugin;
import stroom.credentials.client.presenter.CredentialsManagerDialogPresenter;
import stroom.credentials.client.presenter.CredentialsPresenter;
import stroom.credentials.client.presenter.CredentialsPresenter.CredentialsView;
import stroom.credentials.client.presenter.CredentialsSettingsPresenter;
import stroom.credentials.client.presenter.CredentialsSettingsPresenter.CredentialsSettingsView;
import stroom.credentials.client.view.CredentialsManagerDialogViewImpl;
import stroom.credentials.client.view.CredentialsSettingsViewImpl;
import stroom.credentials.client.view.CredentialsViewImpl;

/**
 * Ensures the GIN injection works in GWT.
 */
public class CredentialsModule extends PluginModule {

    @Override
    protected void configure() {
        // Generate menu item for Credentials UI
        bindPlugin(CredentialsPlugin.class);

        // Tie the presenters, View interfaces and Views together
        bindPresenterWidget(CredentialsPresenter.class,
                CredentialsView.class,
                CredentialsViewImpl.class);

        bindPresenterWidget(CredentialsSettingsPresenter.class,
                CredentialsSettingsView.class,
                CredentialsSettingsViewImpl.class);

        bindPresenterWidget(CredentialsManagerDialogPresenter.class,
                CredentialsManagerDialogPresenter.CredentialsManagerDialogView.class,
                CredentialsManagerDialogViewImpl.class);

    }

}
