package stroom.dashboard.client.table;

import stroom.query.api.v2.Column;

public interface HasValueFilter {

    void setValueFilter(Column column, String valueFilter);
}
