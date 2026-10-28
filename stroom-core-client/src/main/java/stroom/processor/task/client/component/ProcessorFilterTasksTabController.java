/*
 * Copyright 2025 Crown Copyright
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

package stroom.processor.task.client.component;

import stroom.content.client.component.ContentTabController;
import stroom.processor.shared.ProcessorFilter;
import stroom.svg.shared.SvgImage;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// A processor filter's tasks ([ProcessorFilterTasksController]) in a main content tab of their own.
public class ProcessorFilterTasksTabController extends ContentTabController {

    /// The tab's type.
    public static final String TAB_TYPE = "Tasks";

    private final ProcessorFilterTasksController tasks;

    /// Made by GIN.
    ///
    /// @param eventBus The application's event bus.
    /// @param tasks    The tasks.
    @Inject
    public ProcessorFilterTasksTabController(final EventBus eventBus,
                                             final ProcessorFilterTasksController tasks) {
        super(eventBus);
        this.tasks = own(tasks);
        initWidget(tasks.asWidget());
    }

    /// Fetches the filter's tasks again.
    public void refresh() {
        tasks.refresh();
    }

    /// Shows a processor filter's tasks.
    ///
    /// @param processorFilter The filter.
    public void setProcessorFilter(final ProcessorFilter processorFilter) {
        tasks.setProcessorFilter(processorFilter);
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.JOBS;
    }

    @Override
    public String getLabel() {
        return tasks.getLabel();
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }
}
