package stroom.dashboard.client.table;

import stroom.query.api.v2.Column;

import com.google.gwt.dom.client.NativeEvent;

public interface FilterCellManager {

    void setValueFilter(Column column, String valueFilter);

    void onSelectionPopup(Column column, NativeEvent event);
}
