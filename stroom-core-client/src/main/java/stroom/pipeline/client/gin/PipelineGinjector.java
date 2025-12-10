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

import stroom.pipeline.client.PipelinePlugin;
import stroom.pipeline.client.TextConverterPlugin;
import stroom.pipeline.client.XsltPlugin;
import stroom.pipeline.client.presenter.PipelinePresenter;
import stroom.pipeline.client.presenter.TextConverterPresenter;
import stroom.pipeline.client.presenter.XsltPresenter;
import stroom.pipeline.stepping.client.presenter.SteppingFilterPresenter;
import stroom.pipeline.structure.client.presenter.PipelineStructurePresenter;
import stroom.processor.client.presenter.ProcessorListPresenter;
import stroom.processor.client.presenter.ProcessorPresenter;

import com.google.gwt.inject.client.AsyncProvider;

public interface PipelineGinjector {

    AsyncProvider<XsltPlugin> getXSLTPlugin();

    AsyncProvider<XsltPresenter> getXSLTPresenter();

    AsyncProvider<TextConverterPlugin> getTextConverterPlugin();

    AsyncProvider<TextConverterPresenter> getTextConverterPresenter();

    AsyncProvider<PipelinePlugin> getPipelinePlugin();

    AsyncProvider<PipelinePresenter> getPipelinePresenter();

    AsyncProvider<PipelineStructurePresenter> getPipelineStructurePresenter();

    AsyncProvider<SteppingFilterPresenter> getSteppingFilterSettingsPresenter();

    // Add processor bindings.
    AsyncProvider<ProcessorPresenter> getProcessorPresenter();

    AsyncProvider<ProcessorListPresenter> getProcessorListPresenter();
}
