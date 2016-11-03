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

import com.google.inject.Singleton;

import stroom.app.client.gin.PluginModule;
import stroom.pipeline.client.PipelinePlugin;
import stroom.pipeline.client.TextConverterPlugin;
import stroom.pipeline.client.XSLTPlugin;
import stroom.pipeline.client.presenter.PipelinePresenter;
import stroom.pipeline.client.presenter.PipelineSettingsPresenter;
import stroom.pipeline.client.presenter.PipelineSettingsPresenter.PipelineSettingsView;
import stroom.pipeline.client.presenter.TextConverterPresenter;
import stroom.pipeline.client.presenter.TextConverterSettingsPresenter;
import stroom.pipeline.client.presenter.TextConverterSettingsPresenter.TextConverterSettingsView;
import stroom.pipeline.client.presenter.XSLTPresenter;
import stroom.pipeline.client.presenter.XSLTSettingsPresenter;
import stroom.pipeline.client.presenter.XSLTSettingsPresenter.XSLTSettingsView;
import stroom.pipeline.client.view.PipelineSettingsViewImpl;
import stroom.pipeline.client.view.TextConverterSettingsViewImpl;
import stroom.pipeline.client.view.XSLTSettingsViewImpl;
import stroom.pipeline.processor.client.presenter.ProcessorPresenter;
import stroom.pipeline.processor.client.presenter.ProcessorPresenter.ProcessorView;
import stroom.pipeline.processor.client.view.ProcessorViewImpl;
import stroom.pipeline.stepping.client.PipelineSteppingPlugin;
import stroom.pipeline.stepping.client.presenter.EditorPresenter;
import stroom.pipeline.stepping.client.presenter.EditorPresenter.EditorView;
import stroom.pipeline.stepping.client.presenter.StepControlPresenter;
import stroom.pipeline.stepping.client.presenter.StepControlPresenter.StepControlView;
import stroom.pipeline.stepping.client.presenter.StepLocationPresenter;
import stroom.pipeline.stepping.client.presenter.StepLocationPresenter.StepLocationView;
import stroom.pipeline.stepping.client.presenter.SteppingFilterPresenter;
import stroom.pipeline.stepping.client.presenter.SteppingFilterPresenter.SteppingFilterSettingsProxy;
import stroom.pipeline.stepping.client.presenter.SteppingFilterPresenter.SteppingFilterSettingsView;
import stroom.pipeline.stepping.client.presenter.SteppingPresenter;
import stroom.pipeline.stepping.client.presenter.SteppingPresenter.SteppingView;
import stroom.pipeline.stepping.client.presenter.XPathFilterPresenter;
import stroom.pipeline.stepping.client.presenter.XPathFilterPresenter.XPathFilterView;
import stroom.pipeline.stepping.client.view.EditorViewImpl;
import stroom.pipeline.stepping.client.view.StepControlViewImpl;
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
import stroom.xmleditor.client.view.XMLEditorMenuPresenter;

public class PipelineModule extends PluginModule {
    @Override
    protected void configure() {
        bindPlugin(TextConverterPlugin.class);
        bind(TextConverterPresenter.class);
        bindPresenterWidget(TextConverterSettingsPresenter.class, TextConverterSettingsView.class,
                TextConverterSettingsViewImpl.class);

        bindPlugin(XSLTPlugin.class);
        bind(XSLTPresenter.class);
        bindPresenterWidget(XSLTSettingsPresenter.class, XSLTSettingsView.class, XSLTSettingsViewImpl.class);

        bindPlugin(PipelinePlugin.class);
        bindPlugin(PipelineSteppingPlugin.class);
        bind(PipelinePresenter.class);
        bindPresenterWidget(StepLocationPresenter.class, StepLocationView.class, StepLocationViewImpl.class);
        bindPresenterWidget(StepControlPresenter.class, StepControlView.class, StepControlViewImpl.class);
        bindPresenterWidget(PipelineSettingsPresenter.class, PipelineSettingsView.class,
                PipelineSettingsViewImpl.class);

        bindPresenterWidget(PipelineTreePresenter.class, PipelineTreeView.class, PipelineTreeViewImpl.class);
        bindPresenterWidget(PipelineStructurePresenter.class, PipelineStructureView.class,
                PipelineStructureViewImpl.class);

        bindPresenterWidget(NewElementPresenter.class, NewElementView.class, NewElementViewImpl.class);
        bindPresenterWidget(NewPropertyPresenter.class, NewPropertyView.class, NewPropertyViewImpl.class);
        bindPresenterWidget(NewPipelineReferencePresenter.class, NewPipelineReferenceView.class,
                NewPipelineReferenceViewImpl.class);

        bindPresenterWidget(SteppingPresenter.class, SteppingView.class, SteppingViewImpl.class);
        bind(SteppingFilterSettingsProxy.class).asEagerSingleton();
        bindPresenterWidget(SteppingFilterPresenter.class, SteppingFilterSettingsView.class,
                SteppingFilterViewImpl.class);
        bindPresenterWidget(XPathFilterPresenter.class, XPathFilterView.class, XPathFilterViewImpl.class);

        bindPresenterWidget(EditorPresenter.class, EditorView.class, EditorViewImpl.class);

        // Add processor bindings.
        bindPresenterWidget(ProcessorPresenter.class, ProcessorView.class, ProcessorViewImpl.class);

        bind(XMLEditorMenuPresenter.class).in(Singleton.class);
    }
}
