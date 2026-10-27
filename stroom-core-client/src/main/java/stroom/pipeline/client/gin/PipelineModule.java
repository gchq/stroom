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
import stroom.processor.task.client.ProcessorTaskPlugin;
import stroom.processor.task.client.presenter.ProcessorTaskPresenter;

public class PipelineModule extends PluginModule {

    @Override
    protected void configure() {
        bindPlugin(TextConverterPlugin.class);
        bind(TextConverterPresenter.class);

        bindPlugin(XsltPlugin.class);
        bind(XsltPresenter.class);

        bindPlugin(PipelinePlugin.class);
        bind(PipelinePresenter.class);


        // Add processor bindings.

        bindPlugin(ProcessorTaskPlugin.class);
        bind(ProcessorTaskPresenter.class);
    }
}
