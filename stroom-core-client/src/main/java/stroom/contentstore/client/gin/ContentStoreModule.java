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

package stroom.contentstore.client.gin;

import stroom.contentstore.client.ContentStorePlugin;
import stroom.contentstore.client.presenter.ContentStoreCredentialsDialogPresenter;
import stroom.contentstore.client.presenter.ContentStoreCredentialsDialogPresenter.ContentStoreCredentialsDialogView;
import stroom.contentstore.client.presenter.ContentStorePresenter;
import stroom.contentstore.client.presenter.ContentStorePresenter.ContentStoreView;
import stroom.contentstore.client.view.ContentStoreCredentialsDialogViewImpl;
import stroom.contentstore.client.view.ContentStoreViewImpl;
import stroom.core.client.gin.PluginModule;

/**
 * Ensures the GIN injection works in GWT.
 */
public class ContentStoreModule extends PluginModule {

    @Override
    protected void configure() {
        // Generate menu item for ContentStore
        bindPlugin(ContentStorePlugin.class);

        // Tie the presenters, View interfaces and Views together
        bindPresenterWidget(ContentStorePresenter.class,
                ContentStoreView.class,
                ContentStoreViewImpl.class);

        // Tie up the Credentials dialog
        bindPresenterWidget(ContentStoreCredentialsDialogPresenter.class,
                ContentStoreCredentialsDialogView.class,
                ContentStoreCredentialsDialogViewImpl.class);

    }

}
