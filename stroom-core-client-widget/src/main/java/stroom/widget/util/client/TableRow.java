package stroom.widget.util.client;

import java.util.List;

public class TableRow {

    private final List<TableCell> cells;

    public TableRow(final List<TableCell> cells) {
        this.cells = cells;
    }

    public List<TableCell> getCells() {
        return cells;
    }
}