package stroom.db.util;

import org.jooq.Field;
import org.jooq.Record;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValNull;
import stroom.datasource.api.v2.DataSourceField;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ValueMapper {
    private final Map<DataSourceField, Field<?>> fieldMap = new HashMap<>();
    private final Map<DataSourceField, Mapper<?>> mappers = new HashMap<>();

    public <T> void map(final DataSourceField dataSourceField, final Field<T> field, final Function<T, Val> handler) {
        fieldMap.put(dataSourceField, field);
        mappers.put(dataSourceField, new Mapper<>(field, handler));
    }

    public List<Field<?>> getFields(final List<DataSourceField> fields) {
        return fields.stream().map(fieldMap::get).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public Mapper<?>[] getMappers(final DataSourceField[] fields) {
        final Mapper[] handlers = new Mapper[fields.length];
        for (int i = 0; i < fields.length; i++) {
            handlers[i] = mappers.get(fields[i]);
        }
        return handlers;
    }

    public static class Mapper<T> {
        private final Field<T> field;
        private final Function<T, Val> handler;

        Mapper(final Field<T> field, final Function<T, Val> handler) {
            this.field = field;
            this.handler = handler;
        }

        public Val map(Record record) {
            final T o = record.get(field);
            if (o != null) {
                return handler.apply(o);
            }
            return ValNull.INSTANCE;
        }
    }
}
