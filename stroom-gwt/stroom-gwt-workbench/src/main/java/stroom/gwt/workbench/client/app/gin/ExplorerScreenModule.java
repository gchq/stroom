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

import stroom.explorer.client.presenter.EntityCheckTreePresenter;
import stroom.explorer.client.presenter.EntityCheckTreePresenter.EntityCheckTreeView;
import stroom.explorer.client.presenter.EntityTreePresenter;
import stroom.explorer.client.presenter.EntityTreePresenter.EntityTreeView;
import stroom.explorer.client.presenter.ExplorerNodeEditTagsPresenter;
import stroom.explorer.client.presenter.ExplorerNodeEditTagsPresenter.ExplorerNodeEditTagsProxy;
import stroom.explorer.client.presenter.ExplorerNodeEditTagsPresenter.ExplorerNodeEditTagsView;
import stroom.explorer.client.presenter.ExplorerNodeRemoveTagsPresenter;
import stroom.explorer.client.presenter.ExplorerNodeRemoveTagsPresenter.ExplorerNodeRemoveTagsProxy;
import stroom.explorer.client.presenter.ExplorerNodeRemoveTagsPresenter.ExplorerNodeRemoveTagsView;
import stroom.explorer.client.presenter.TypeFilterPresenter;
import stroom.explorer.client.presenter.TypeFilterPresenter.TypeFilterView;
import stroom.explorer.client.presenter.TypeFilterViewImpl;
import stroom.explorer.client.view.EntityCheckTreeViewImpl;
import stroom.explorer.client.view.EntityTreeViewImpl;
import stroom.explorer.client.view.ExplorerNodeEditTagsViewImpl;
import stroom.explorer.client.view.ExplorerNodeRemoveTagsViewImpl;

import com.google.inject.Provides;
import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;

/// The explorer presenter and view bindings of Stroom's `AppModule`
/// (`stroom-app-gwt/.../stroom/app/client/gin/AppModule.java`), without the app shell.
/// Presenters that Stroom binds with a GWTP proxy are bound as presenter widgets with a null
/// proxy: a story shows one by registering it as the handler of its event, as the proxy would.
public class ExplorerScreenModule extends AbstractPresenterModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
        bindPresenterWidget(TypeFilterPresenter.class,
                TypeFilterView.class,
                TypeFilterViewImpl.class);
        bindPresenterWidget(EntityTreePresenter.class,
                EntityTreeView.class,
                EntityTreeViewImpl.class);
        bindPresenterWidget(EntityCheckTreePresenter.class,
                EntityCheckTreeView.class,
                EntityCheckTreeViewImpl.class);
        bindPresenterWidget(ExplorerNodeEditTagsPresenter.class,
                ExplorerNodeEditTagsView.class,
                ExplorerNodeEditTagsViewImpl.class);
        bindPresenterWidget(ExplorerNodeRemoveTagsPresenter.class,
                ExplorerNodeRemoveTagsView.class,
                ExplorerNodeRemoveTagsViewImpl.class);
    }

    /// @return No proxy, see the class description.
    @Provides
    ExplorerNodeEditTagsProxy provideExplorerNodeEditTagsProxy() {
        return null;
    }

    /// @return No proxy, see the class description.
    @Provides
    ExplorerNodeRemoveTagsProxy provideExplorerNodeRemoveTagsProxy() {
        return null;
    }
}
