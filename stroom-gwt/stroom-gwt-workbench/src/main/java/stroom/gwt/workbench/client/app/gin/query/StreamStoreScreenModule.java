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

package stroom.gwt.workbench.client.app.gin.query;

import stroom.data.client.presenter.DataPresenter;
import stroom.data.client.presenter.DataPresenter.DataView;
import stroom.data.client.presenter.DataPreviewTabPresenter;
import stroom.data.client.presenter.DataPreviewTabPresenter.DataPreviewTabView;
import stroom.data.client.presenter.MetaPresenter;
import stroom.data.client.presenter.MetaPresenter.MetaView;
import stroom.data.client.presenter.ProcessorTaskPresenter;
import stroom.data.client.presenter.ProcessorTaskPresenter.StreamTaskView;
import stroom.data.client.presenter.SourcePresenter;
import stroom.data.client.presenter.SourcePresenter.SourceView;
import stroom.data.client.presenter.SourceTabPresenter;
import stroom.data.client.presenter.SourceTabPresenter.SourceTabView;
import stroom.data.client.view.DataPreviewTabViewImpl;
import stroom.data.client.view.DataViewImpl;
import stroom.data.client.view.MetaViewImpl;
import stroom.data.client.view.SourceTabViewImpl;
import stroom.data.client.view.SourceViewImpl;
import stroom.data.client.view.StreamTaskViewImpl;
import stroom.widget.presenter.client.gin.AbstractPresenterModule;

/// The presenter and view bindings of Stroom's `StreamStoreModule`
/// (`stroom/data/client/gin/StreamStoreModule.java`), without its plugins and app services.
/// A story shows a screen that Stroom opens on an app-wide event by adding a handler for
/// the event, as the screen's area plugin does in Stroom.
public class StreamStoreScreenModule extends AbstractPresenterModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
        bindPresenterWidget(MetaPresenter.class,
                MetaView.class,
                MetaViewImpl.class);
        bindPresenterWidget(DataPreviewTabPresenter.class,
                DataPreviewTabView.class,
                DataPreviewTabViewImpl.class);
        bindPresenterWidget(DataPresenter.class,
                DataView.class,
                DataViewImpl.class);
        bindPresenterWidget(ProcessorTaskPresenter.class,
                StreamTaskView.class,
                StreamTaskViewImpl.class);
        bindPresenterWidget(SourceTabPresenter.class,
                SourceTabView.class,
                SourceTabViewImpl.class);
        bindPresenterWidget(SourcePresenter.class,
                SourceView.class,
                SourceViewImpl.class);
    }
}
