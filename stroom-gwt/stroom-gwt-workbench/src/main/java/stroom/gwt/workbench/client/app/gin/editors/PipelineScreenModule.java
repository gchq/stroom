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

package stroom.gwt.workbench.client.app.gin.editors;

import stroom.pipeline.client.presenter.TextConverterPresenter;
import stroom.pipeline.client.presenter.TextConverterSettingsPresenter;
import stroom.pipeline.client.presenter.TextConverterSettingsPresenter.TextConverterSettingsView;
import stroom.pipeline.client.presenter.XsltPresenter;
import stroom.pipeline.client.view.TextConverterSettingsViewImpl;
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
import stroom.processor.client.presenter.ProcessorProfileEditPresenter;
import stroom.processor.client.presenter.ProcessorProfileEditPresenter.ProcessorProfileEditView;
import stroom.processor.client.presenter.ProfilePeriodEditPresenter;
import stroom.processor.client.presenter.ProfilePeriodEditPresenter.ProfilePeriodEditView;
import stroom.processor.client.view.BatchProcessorFilterEditViewImpl;
import stroom.processor.client.view.EditFeedDependencyViewImpl;
import stroom.processor.client.view.FeedDependencyViewImpl;
import stroom.processor.client.view.ProcessorEditViewImpl;
import stroom.processor.client.view.ProcessorProfileEditViewImpl;
import stroom.processor.client.view.ProcessorViewImpl;
import stroom.processor.client.view.ProfilePeriodEditViewImpl;

import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;

/// The presenter and view bindings of Stroom's `PipelineModule`
/// (`stroom/pipeline/client/gin/PipelineModule.java`) for the XSLT and Text Converter editors and
/// the processor screens, without its plugins, app services, pipeline editor and stepping.
/// Presenters that Stroom binds with a GWTP proxy are bound as presenter widgets with a null
/// proxy: a story shows one by registering it as the handler of its event, as the proxy would.
public class PipelineScreenModule extends AbstractPresenterModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
        bind(TextConverterPresenter.class);
        bindPresenterWidget(TextConverterSettingsPresenter.class,
                TextConverterSettingsView.class,
                TextConverterSettingsViewImpl.class);
        bind(XsltPresenter.class);
        bindPresenterWidget(ProcessorPresenter.class,
                ProcessorView.class,
                ProcessorViewImpl.class);
        bindPresenterWidget(ProcessorEditPresenter.class,
                ProcessorEditView.class,
                ProcessorEditViewImpl.class);
        bindPresenterWidget(BatchProcessorFilterEditPresenter.class,
                BatchProcessorFilterEditView.class,
                BatchProcessorFilterEditViewImpl.class);
        bindPresenterWidget(FeedDependencyPresenter.class,
                FeedDependencyView.class,
                FeedDependencyViewImpl.class);
        bindPresenterWidget(EditFeedDependencyPresenter.class,
                EditFeedDependencyView.class,
                EditFeedDependencyViewImpl.class);
        bindPresenterWidget(ProcessorProfileEditPresenter.class,
                ProcessorProfileEditView.class,
                ProcessorProfileEditViewImpl.class);
        bindPresenterWidget(ProfilePeriodEditPresenter.class,
                ProfilePeriodEditView.class,
                ProfilePeriodEditViewImpl.class);
    }
}

