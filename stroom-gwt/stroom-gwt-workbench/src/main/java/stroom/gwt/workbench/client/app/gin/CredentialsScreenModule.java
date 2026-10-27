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

import stroom.credentials.client.presenter.CredentialsManagerDialogPresenter;
import stroom.credentials.client.presenter.CredentialsPresenter;
import stroom.credentials.client.presenter.CredentialsPresenter.CredentialsView;
import stroom.credentials.client.view.CredentialsManagerViewImpl;
import stroom.credentials.client.view.CredentialsViewImpl;
import stroom.widget.presenter.client.gin.AbstractPresenterModule;

/// The presenter and view bindings of Stroom's `CredentialsModule`
/// (`stroom/credentials/client/gin/CredentialsModule.java`), without its plugins and app services.
/// A story shows a screen that Stroom opens on an app-wide event by adding a handler for
/// the event, as the screen's area plugin does in Stroom.
public class CredentialsScreenModule extends AbstractPresenterModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
        bindPresenterWidget(CredentialsPresenter.class,
                CredentialsView.class,
                CredentialsViewImpl.class);
        bindPresenterWidget(CredentialsManagerDialogPresenter.class,
                CredentialsManagerDialogPresenter.CredentialsManagerDialogView.class,
                CredentialsManagerViewImpl.class);
    }
}

