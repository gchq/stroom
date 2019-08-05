package stroom.db.util;

import org.jooq.Field;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValNull;
import stroom.datasource.api.v2.AbstractField;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class ValueMapper {
    private final Map<AbstractField, Field<?>> fieldMap = new HashMap<>();
    private final Map<AbstractField, ValHandler> valHandlers = new HashMap<>();

    public <T> void map(final AbstractField dataSourceField, final Field<T> field, final ValHandler handler) {
        fieldMap.put(dataSourceField, field);
        valHandlers.put(dataSourceField, handler);
    }

    public Field<?>[] getFields(final AbstractField[] fields) {
        final Field<?>[] arr = new Field<?>[fields.length];
        for (int i = 0; i < fields.length; i++) {
            arr[i] = fieldMap.get(fields[i]);
        }
        return arr;
    }

    public ValHandler[] getHandlers(final AbstractField[] fields) {
        final ValHandler[] handlers = new ValHandler[fields.length];
        for (int i = 0; i < fields.length; i++) {
            handlers[i] = Optional.ofNullable(valHandlers.get(fields[i]))
                    .orElse(v -> ValNull.INSTANCE);
        }
        return handlers;
    }

    public interface ValHandler extends Function<Object, Val> {
    }
}
