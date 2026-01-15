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

package stroom.pipeline.client.gin;

import stroom.core.client.gin.PluginModule;
import stroom.editor.client.view.EditorMenuPresenter;
import stroom.pipeline.client.PipelinePlugin;
import stroom.pipeline.client.TextConverterPlugin;
import stroom.pipeline.client.XsltPlugin;
import stroom.pipeline.client.presenter.PipelinePresenter;
import stroom.pipeline.client.presenter.TextConverterPresenter;
import stroom.pipeline.client.presenter.TextConverterSettingsPresenter;
import stroom.pipeline.client.presenter.TextConverterSettingsPresenter.TextConverterSettingsView;
import stroom.pipeline.client.presenter.XsltPresenter;
import stroom.pipeline.client.view.TextConverterSettingsViewImpl;
import stroom.pipeline.stepping.client.presenter.ElementPresenter;
import stroom.pipeline.stepping.client.presenter.ElementPresenter.ElementView;
import stroom.pipeline.stepping.client.presenter.StepControlPresenter;
import stroom.pipeline.stepping.client.presenter.StepControlPresenter.StepControlView;
import stroom.pipeline.stepping.client.presenter.StepLocationLinkPresenter;
import stroom.pipeline.stepping.client.presenter.StepLocationLinkPresenter.StepLocationLinkView;
import stroom.pipeline.stepping.client.presenter.StepLocationPresenter;
import stroom.pipeline.stepping.client.presenter.StepLocationPresenter.StepLocationView;
import stroom.pipeline.stepping.client.presenter.SteppingFilterPresenter;
import stroom.pipeline.stepping.client.presenter.SteppingFilterPresenter.SteppingFilterView;
import stroom.pipeline.stepping.client.presenter.SteppingPresenter;
import stroom.pipeline.stepping.client.presenter.SteppingPresenter.SteppingView;
import stroom.pipeline.stepping.client.presenter.XPathFilterPresenter;
import stroom.pipeline.stepping.client.presenter.XPathFilterPresenter.XPathFilterView;
import stroom.pipeline.stepping.client.view.ElementViewImpl;
import stroom.pipeline.stepping.client.view.StepControlViewImpl;
import stroom.pipeline.stepping.client.view.StepLocationLinkViewImpl;
import stroom.pipeline.stepping.client.view.StepLocationViewImpl;
import stroom.pipeline.stepping.client.view.SteppingFilterViewImpl;
import stroom.pipeline.stepping.client.view.SteppingViewImpl;
import stroom.pipeline.stepping.client.view.XPathFilterViewImpl;
import stroom.pipeline.structure.client.presenter.NewElementPresenter;
import stroom.pipeline.structure.client.presenter.NewElementPresenter.NewElementView;
import stroom.pipeline.structure.client.presenter.NewPipelineReferencePresenter;
import stroom.pipeline.structure.client.presenter.NewPipelineReferencePresenter.NewPipelineReferenceView;
import stroom.pipeline.structure.client.presenter.NewPropertyPresenter;
import stroom.pipeline.structure.client.presenter.NewPropertyPresenter.NewPropertyView;
import stroom.pipeline.structure.client.presenter.PipelineStructurePresenter;
import stroom.pipeline.structure.client.presenter.PipelineStructurePresenter.PipelineStructureView;
import stroom.pipeline.structure.client.presenter.PipelineTreePresenter;
import stroom.pipeline.structure.client.presenter.PipelineTreePresenter.PipelineTreeView;
import stroom.pipeline.structure.client.view.NewElementViewImpl;
import stroom.pipeline.structure.client.view.NewPipelineReferenceViewImpl;
import stroom.pipeline.structure.client.view.NewPropertyViewImpl;
import stroom.pipeline.structure.client.view.PipelineStructureViewImpl;
import stroom.pipeline.structure.client.view.PipelineTreeViewImpl;
import stroom.processor.client.presenter.BatchProcessorFilterEditPresenter;
import stroom.processor.client.presenter.BatchProcessorFilterEditPresenter.BatchProcessorFilterEditView;
import stroom.processor.client.presenter.EditFeedDependencyPresenter;
import stroom.processor.client.presenter.EditFeedDependencyPresenter.EditFeedDependencyView;
import stroom.processor.client.presenter.FeedDependencyPresenter;
import stroom.processor.client.presenter.FeedDependencyPresenter.FeedDependencyView;
import stroom.processor.client.presenter.ProcessorEditPresenter;
import stroom.processor.client.presenter.ProcessorEditPresenter.ProcessorEditView;
import stroom.processor.client.presenter.ProcessorPresenter;
import stroom.processor.client.presenter.ProcessorPresenter.ProcessorView;
import stroom.processor.client.view.BatchProcessorFilterEditViewImpl;
import stroom.processor.client.view.EditFeedDependencyViewImpl;
import stroom.processor.client.view.FeedDependencyViewImpl;
import stroom.processor.client.view.ProcessorEditViewImpl;
import stroom.processor.client.view.ProcessorViewImpl;
import stroom.processor.task.client.ProcessorTaskPlugin;
import stroom.processor.task.client.presenter.ProcessorTaskPresenter;
import stroom.processor.task.client.presenter.ProcessorTaskPresenter.ProcessorTaskView;
import stroom.processor.task.client.view.ProcessorTaskViewImpl;

import com.google.inject.Singleton;

public class PipelineModule extends PluginModule {

    @Override
    protected void configure() {
        bindPlugin(TextConverterPlugin.class);
        bind(TextConverterPresenter.class);
        bindPresenterWidget(TextConverterSettingsPresenter.class, TextConverterSettingsView.class,
                TextConverterSettingsViewImpl.class);

        bindPlugin(XsltPlugin.class);
        bind(XsltPresenter.class);

        bindPlugin(PipelinePlugin.class);
        bind(PipelinePresenter.class);
        bindPresenterWidget(
                StepLocationLinkPresenter.class,
                StepLocationLinkView.class,
                StepLocationLinkViewImpl.class);
        bindPresenterWidget(StepLocationPresenter.class, StepLocationView.class, StepLocationViewImpl.class);
        bindPresenterWidget(StepControlPresenter.class, StepControlView.class, StepControlViewImpl.class);

        bindPresenterWidget(PipelineTreePresenter.class, PipelineTreeView.class, PipelineTreeViewImpl.class);
        bindPresenterWidget(PipelineStructurePresenter.class, PipelineStructureView.class,
                PipelineStructureViewImpl.class);

        bindPresenterWidget(NewElementPresenter.class, NewElementView.class, NewElementViewImpl.class);
        bindPresenterWidget(NewPropertyPresenter.class, NewPropertyView.class, NewPropertyViewImpl.class);
        bindPresenterWidget(NewPipelineReferencePresenter.class, NewPipelineReferenceView.class,
                NewPipelineReferenceViewImpl.class);

        bindPresenterWidget(SteppingPresenter.class, SteppingView.class, SteppingViewImpl.class);
        bindPresenterWidget(SteppingFilterPresenter.class, SteppingFilterView.class,
                SteppingFilterViewImpl.class);
        bindPresenterWidget(XPathFilterPresenter.class, XPathFilterView.class, XPathFilterViewImpl.class);

        bindPresenterWidget(ElementPresenter.class, ElementView.class, ElementViewImpl.class);

        // Add processor bindings.
        bindPresenterWidget(ProcessorPresenter.class, ProcessorView.class, ProcessorViewImpl.class);
        bindPresenterWidget(ProcessorEditPresenter.class, ProcessorEditView.class, ProcessorEditViewImpl.class);
        bindPresenterWidget(
                BatchProcessorFilterEditPresenter.class,
                BatchProcessorFilterEditView.class,
                BatchProcessorFilterEditViewImpl.class);
        bindPresenterWidget(
                FeedDependencyPresenter.class,
                FeedDependencyView.class,
                FeedDependencyViewImpl.class);
        bindPresenterWidget(
                EditFeedDependencyPresenter.class,
                EditFeedDependencyView.class,
                EditFeedDependencyViewImpl.class);

        bindPlugin(ProcessorTaskPlugin.class);
        bindPresenterWidget(ProcessorTaskPresenter.class, ProcessorTaskView.class,
                ProcessorTaskViewImpl.class);

        bind(EditorMenuPresenter.class).in(Singleton.class);
    }
}
