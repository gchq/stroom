package stroom.data.grid.client;

import com.google.gwt.cell.client.Cell;

import java.util.function.Function;
import java.util.function.Supplier;

public class ColumnBuilder<T_ROW, T_RAW_VAL, T_CELL_VAL, T_CELL extends Cell<T_CELL_VAL>>
        extends AbstractColumnBuilder<T_ROW, T_RAW_VAL, T_CELL_VAL, T_CELL,
        ColumnBuilder<T_ROW, T_RAW_VAL, T_CELL_VAL, T_CELL>> {

    public ColumnBuilder() {

    }

    public ColumnBuilder(final Function<T_ROW, T_RAW_VAL> valueExtractor,
                         final Function<T_RAW_VAL, T_CELL_VAL> formatter,
                         final Supplier<T_CELL> cellSupplier) {
        valueExtractor(valueExtractor);
        formatter(formatter);
        cellSupplier(cellSupplier);
    }

    @Override
    protected ColumnBuilder<T_ROW, T_RAW_VAL, T_CELL_VAL, T_CELL> self() {
        return this;
    }
}
