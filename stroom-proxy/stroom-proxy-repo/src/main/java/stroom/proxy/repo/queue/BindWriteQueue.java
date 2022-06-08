package stroom.proxy.repo.queue;

import org.jooq.BatchBindStep;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;

import java.util.ArrayList;
import java.util.List;

public class BindWriteQueue implements WriteQueue {

    private final Table<?> table;
    private final Field<?>[] columns;
    private final Object[] values;
    private final List<Object[]> queue = new ArrayList<>();

    public BindWriteQueue(final Table<?> table,
                          final Field<?>[] columns) {
        this.table = table;
        this.columns = columns;
        values = new Object[columns.length];
    }

    @Override
    public void flush(final DSLContext context) {
        if (queue.size() > 0) {
            BatchBindStep batchBindStep = context.batch(context
                    .insertInto(table)
                    .columns(columns)
                    .values(values));
            for (final Object[] row : queue) {
                batchBindStep = batchBindStep.bind(row);
            }
            batchBindStep.execute();
        }
    }

    public void add(final Object[] row) {
        queue.add(row);
    }

    @Override
    public int size() {
        return queue.size();
    }

    @Override
    public void clear() {
        queue.clear();
    }
}
