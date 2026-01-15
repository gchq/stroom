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

package stroom.dashboard.client.table;

import stroom.dashboard.shared.ColumnValues;
import stroom.dispatch.client.RestErrorHandler;
import stroom.query.api.Column;
import stroom.query.api.ColumnValueSelection;
import stroom.query.api.ConditionalFormattingRule;
import stroom.task.client.TaskMonitorFactory;

import com.google.gwt.view.client.Range;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public abstract class ColumnValuesDataSupplier {

    private final stroom.query.api.Column column;
    private final List<ConditionalFormattingRule> conditionalFormattingRules;
    private TaskMonitorFactory taskMonitorFactory;
    private String nameFilter;

    public ColumnValuesDataSupplier(final stroom.query.api.Column column,
                                    final List<ConditionalFormattingRule> conditionalFormattingRules) {
        this.column = column;
        this.conditionalFormattingRules = conditionalFormattingRules;
    }

    protected abstract void exec(Range range,
                                 Consumer<ColumnValues> dataConsumer,
                                 RestErrorHandler errorHandler,
                                 Map<String, ColumnValueSelection> selections);

    public Column getColumn() {
        return column;
    }

    public List<ConditionalFormattingRule> getConditionalFormattingRules() {
        return conditionalFormattingRules;
    }

    public TaskMonitorFactory getTaskMonitorFactory() {
        return taskMonitorFactory;
    }

    public void setTaskMonitorFactory(final TaskMonitorFactory taskMonitorFactory) {
        this.taskMonitorFactory = taskMonitorFactory;
    }

    public String getNameFilter() {
        return nameFilter;
    }

    public void setNameFilter(final String text) {
        nameFilter = text;
        if (nameFilter != null) {
            nameFilter = nameFilter.trim();
            if (nameFilter.isEmpty()) {
                nameFilter = null;
            }
        }
    }
}
