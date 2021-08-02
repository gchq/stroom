package stroom.data.grid.client;

import com.google.gwt.user.cellview.client.DataGrid.Resources;

public interface DefaultResources extends Resources {

    String DEFAULT_CSS = "stroom/data/grid/client/DataGrid.css";

    /**
     * The styles used in this widget.
     */
    @Override
    @Source(DEFAULT_CSS)
    DefaultStyle dataGridStyle();
}