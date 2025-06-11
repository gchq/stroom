package stroom.dashboard.client.table;

import stroom.dashboard.shared.ColumnValues;
import stroom.dispatch.client.RestErrorHandler;
import stroom.query.api.Column;
import stroom.task.client.TaskMonitorFactory;

import com.google.gwt.view.client.Range;

import java.util.function.Consumer;

public abstract class ColumnValuesDataSupplier {

    private final stroom.query.api.Column column;
    private TaskMonitorFactory taskMonitorFactory;
    private String nameFilter;

    public ColumnValuesDataSupplier(final stroom.query.api.Column column) {
        this.column = column;
    }

    protected abstract void exec(Range range,
                       Consumer<ColumnValues> dataConsumer,
                       RestErrorHandler errorHandler);

    public Column getColumn() {
        return column;
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
