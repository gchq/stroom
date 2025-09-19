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
