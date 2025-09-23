package stroom.dashboard.client.input;

import stroom.dashboard.client.table.ColumnValuesDataSupplier;
import stroom.dashboard.client.table.FilterCellManager;
import stroom.dashboard.client.table.TableUpdateEvent;
import stroom.query.api.Column;
import stroom.query.api.ConditionalFormattingRule;

import com.google.gwt.dom.client.Element;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.List;

public interface FilterableTable {

    List<Column> getColumns();

    Element getFilterButton(Column column);

    ColumnValuesDataSupplier getDataSupplier(Column column,
                                             List<ConditionalFormattingRule> conditionalFormattingRules);

    FilterCellManager getFilterCellManager();

    HandlerRegistration addUpdateHandler(TableUpdateEvent.Handler handler);
}
