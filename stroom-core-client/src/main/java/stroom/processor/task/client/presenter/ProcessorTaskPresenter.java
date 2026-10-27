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

package stroom.processor.task.client.presenter;

import stroom.content.client.presenter.ContentTabPresenter;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.task.client.component.ProcessorFilterTasksController;
import stroom.svg.shared.SvgImage;
import stroom.widget.datagrid.client.WrapperView;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// A processor filter's tasks ([ProcessorFilterTasksController]) in a main content tab of their
/// own, until content tabs can show controllers.
public class ProcessorTaskPresenter extends ContentTabPresenter<WrapperView> {

    private final ProcessorFilterTasksController tasks;

    /// Made by GIN.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     Shows the tasks.
    /// @param tasks    The tasks.
    @Inject
    public ProcessorTaskPresenter(final EventBus eventBus,
                                  final WrapperView view,
                                  final ProcessorFilterTasksController tasks) {
        super(eventBus, view);
        this.tasks = own(tasks);
        view.setView(tasks);
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
        return "Tasks";
    }
}
