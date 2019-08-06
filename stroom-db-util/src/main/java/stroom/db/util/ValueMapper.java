package stroom.db.util;

import org.jooq.Field;
import stroom.dashboard.expression.v1.Val;
import stroom.datasource.api.v2.AbstractField;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ValueMapper {
    private final Map<AbstractField, Field<?>> fieldMap = new HashMap<>();
    private final Map<AbstractField, Mapper> mappers = new HashMap<>();

    public <T> void map(final AbstractField dataSourceField, final Field<T> field, final ValHandler handler) {
        fieldMap.put(dataSourceField, field);
        mappers.put(dataSourceField, new Mapper(field, handler));
    }

    public List<Field<?>> getFields(final List<AbstractField> fields) {
        return fields.stream().map(fieldMap::get).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public Mapper[] getMappers(final AbstractField[] fields) {
        final Mapper[] handlers = new Mapper[fields.length];
        for (int i = 0; i < fields.length; i++) {
            handlers[i] = mappers.get(fields[i]);
        }
        return handlers;
    }

    public static class Mapper {
        private final Field<?> field;
        private final ValHandler handler;

        Mapper(final Field<?> field, final ValHandler handler) {
            this.field = field;
            this.handler = handler;
        }

        public Field<?> getField() {
            return field;
        }

        public ValHandler getHandler() {
            return handler;
        }
    }

    public interface ValHandler extends Function<Object, Val> {
    }
}
