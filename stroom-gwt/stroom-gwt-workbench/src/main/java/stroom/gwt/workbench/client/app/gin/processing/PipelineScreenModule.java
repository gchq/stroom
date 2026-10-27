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

package stroom.gwt.workbench.client.app.gin.processing;

import stroom.pipeline.client.presenter.PipelinePresenter;
import stroom.pipeline.client.presenter.TextConverterPresenter;
import stroom.pipeline.client.presenter.XsltPresenter;
import stroom.pipeline.stepping.client.presenter.ElementPresenter;
import stroom.pipeline.stepping.client.presenter.ElementPresenter.ElementView;
import stroom.pipeline.stepping.client.presenter.SteppingFilterPresenter;
import stroom.pipeline.stepping.client.presenter.SteppingFilterPresenter.SteppingFilterView;
import stroom.pipeline.stepping.client.presenter.SteppingPresenter;
import stroom.pipeline.stepping.client.presenter.SteppingPresenter.SteppingView;
import stroom.pipeline.stepping.client.view.ElementViewImpl;
import stroom.pipeline.stepping.client.view.SteppingFilterViewImpl;
import stroom.pipeline.stepping.client.view.SteppingViewImpl;
import stroom.pipeline.structure.client.presenter.PipelineStructurePresenter;
import stroom.pipeline.structure.client.presenter.PipelineStructurePresenter.PipelineStructureView;
import stroom.pipeline.structure.client.view.PipelineStructureViewImpl;
import stroom.processor.client.presenter.FeedDependencyPresenter;
import stroom.processor.client.presenter.FeedDependencyPresenter.FeedDependencyView;
import stroom.processor.client.presenter.ProcessorEditPresenter;
import stroom.processor.client.presenter.ProcessorEditPresenter.ProcessorEditView;
import stroom.processor.client.presenter.ProcessorPresenter;
import stroom.processor.client.presenter.ProcessorPresenter.ProcessorView;
import stroom.processor.client.view.FeedDependencyViewImpl;
import stroom.processor.client.view.ProcessorEditViewImpl;
import stroom.processor.client.view.ProcessorViewImpl;
import stroom.processor.task.client.presenter.ProcessorTaskPresenter;
import stroom.processor.task.client.presenter.ProcessorTaskPresenter.ProcessorTaskView;
import stroom.processor.task.client.view.ProcessorTaskViewImpl;
import stroom.widget.presenter.client.gin.AbstractPresenterModule;

/// The presenter and view bindings of Stroom's `PipelineModule`
/// (`stroom/pipeline/client/gin/PipelineModule.java`), without its plugins and app services.
/// A story shows a screen that Stroom opens on an app-wide event by adding a handler for
/// the event, as the screen's area plugin does in Stroom.
public class PipelineScreenModule extends AbstractPresenterModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
        bind(TextConverterPresenter.class);
        bind(XsltPresenter.class);
        bind(PipelinePresenter.class);
        bindPresenterWidget(PipelineStructurePresenter.class,
                PipelineStructureView.class,
                PipelineStructureViewImpl.class);
        bindPresenterWidget(SteppingPresenter.class,
                SteppingView.class,
                SteppingViewImpl.class);
        bindPresenterWidget(SteppingFilterPresenter.class,
                SteppingFilterView.class,
                SteppingFilterViewImpl.class);
        bindPresenterWidget(ElementPresenter.class,
                ElementView.class,
                ElementViewImpl.class);
        bindPresenterWidget(ProcessorPresenter.class,
                ProcessorView.class,
                ProcessorViewImpl.class);
        bindPresenterWidget(ProcessorEditPresenter.class,
                ProcessorEditView.class,
                ProcessorEditViewImpl.class);
        bindPresenterWidget(FeedDependencyPresenter.class,
                FeedDependencyView.class,
                FeedDependencyViewImpl.class);
        bindPresenterWidget(ProcessorTaskPresenter.class,
                ProcessorTaskView.class,
                ProcessorTaskViewImpl.class);
    }
}
