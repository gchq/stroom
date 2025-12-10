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

package stroom.processor.task.client;

import stroom.core.client.ContentManager;
import stroom.data.client.AbstractTabPresenterPlugin;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.task.client.event.OpenProcessorTaskEvent;
import stroom.processor.task.client.presenter.ProcessorTaskPresenter;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

@Singleton
public class ProcessorTaskPlugin extends AbstractTabPresenterPlugin<ProcessorFilter, ProcessorTaskPresenter> {

    @Inject
    public ProcessorTaskPlugin(final EventBus eventBus,
                               final ContentManager contentManager,
                               final Provider<ProcessorTaskPresenter> processorTaskPresenterProvider) {
        super(eventBus, contentManager, processorTaskPresenterProvider);

        registerHandler(getEventBus().addHandler(OpenProcessorTaskEvent.getType(), event -> {
            open(event.getProcessorFilter(), true);
        }));
    }

    public void open(final ProcessorFilter processorFilter, final boolean forceOpen) {
        if (processorFilter != null) {
            super.openTabPresenter(
                    forceOpen,
                    processorFilter,
                    processorTaskPresenter ->
                            processorTaskPresenter.setProcessorFilter(processorFilter));
        }
    }

    @Override
    protected String getName() {
        return "Tasks";
    }
}
