/*
 * Copyright 2016 Crown Copyright
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

package stroom.pipeline.client.gin;

import stroom.core.client.gin.PluginModule;
import stroom.pipeline.client.PipelinePlugin;
import stroom.pipeline.client.TextConverterPlugin;
import stroom.pipeline.client.XsltPlugin;
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
import stroom.processor.task.client.ProcessorTaskPlugin;
import stroom.processor.task.client.presenter.ProcessorTaskPresenter;
import stroom.processor.task.client.presenter.ProcessorTaskPresenter.ProcessorTaskView;
import stroom.processor.task.client.view.ProcessorTaskViewImpl;

public class PipelineModule extends PluginModule {

    @Override
    protected void configure() {
        bindPlugin(TextConverterPlugin.class);
        bind(TextConverterPresenter.class);

        bindPlugin(XsltPlugin.class);
        bind(XsltPresenter.class);

        bindPlugin(PipelinePlugin.class);
        bind(PipelinePresenter.class);
        bindPresenterWidget(PipelineStructurePresenter.class, PipelineStructureView.class,
                PipelineStructureViewImpl.class);

        bindPresenterWidget(SteppingPresenter.class, SteppingView.class, SteppingViewImpl.class);
        bindPresenterWidget(SteppingFilterPresenter.class, SteppingFilterView.class,
                SteppingFilterViewImpl.class);

        bindPresenterWidget(ElementPresenter.class, ElementView.class, ElementViewImpl.class);

        // Add processor bindings.
        bindPresenterWidget(ProcessorPresenter.class, ProcessorView.class, ProcessorViewImpl.class);
        bindPresenterWidget(ProcessorEditPresenter.class, ProcessorEditView.class, ProcessorEditViewImpl.class);
        bindPresenterWidget(
                FeedDependencyPresenter.class,
                FeedDependencyView.class,
                FeedDependencyViewImpl.class);

        bindPlugin(ProcessorTaskPlugin.class);
        bindPresenterWidget(ProcessorTaskPresenter.class, ProcessorTaskView.class,
                ProcessorTaskViewImpl.class);
    }
}
