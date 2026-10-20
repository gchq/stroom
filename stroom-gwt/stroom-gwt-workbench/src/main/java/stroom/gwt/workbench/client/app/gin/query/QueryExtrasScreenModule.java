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

import stroom.data.client.presenter.DataUploadPresenter;
import stroom.data.client.presenter.DataUploadPresenter.DataUploadView;
import stroom.data.client.view.DataUploadViewImpl;
import stroom.iframe.client.presenter.IFrameContentPresenter;
import stroom.iframe.client.presenter.IFrameContentPresenter.IFrameContentView;
import stroom.iframe.client.view.IFrameContentViewImpl;

import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;

/// Single bindings from Stroom's GIN modules whose other bindings the query batch's screens don't
/// need: `DataUploadPresenter` from `FeedModule` (`MetaPresenter`, which a query's data links
/// open, depends on it) and `IFrameContentPresenter` from the app's `AppModule`
/// (`HyperlinkEventHandlerImpl`, which handles a table's links, depends on it).
public class QueryExtrasScreenModule extends AbstractPresenterModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
        bindPresenterWidget(DataUploadPresenter.class,
                DataUploadView.class,
                DataUploadViewImpl.class);
        bindPresenterWidget(IFrameContentPresenter.class,
                IFrameContentView.class,
                IFrameContentViewImpl.class);
    }
}
