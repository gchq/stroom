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

package stroom.gwt.workbench.client.app.gin.content;

import stroom.content.client.presenter.ContentTabPanePresenter;
import stroom.core.client.ContentManager;
import stroom.explorer.client.presenter.FindInContentPresenter;
import stroom.explorer.client.presenter.FindInContentPresenter.FindInContentView;
import stroom.explorer.client.view.FindInContentViewImpl;
import stroom.widget.presenter.client.gin.AbstractPresenterModule;

import com.google.inject.Singleton;

/// The bindings of Stroom's app-wide `AppModule` (`stroom-app-gwt`,
/// `stroom/app/client/gin/AppModule.java`) that the `content` batch's screens need: the Find and
/// Find In Content dialogs, the tab session chooser, the content tab pane and the content manager.
/// A story shows a screen that Stroom opens on an app-wide event by adding a handler for
/// the event, as the screen's area plugin does in Stroom.
public class ContentAppScreenModule extends AbstractPresenterModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
        bind(ContentManager.class).in(Singleton.class);
        bind(ContentTabPanePresenter.class);
        bindPresenterWidget(FindInContentPresenter.class,
                FindInContentView.class,
                FindInContentViewImpl.class);
    }
}
