package stroom.db.util;

import stroom.query.api.datasource.QueryField;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValNull;

import org.jooq.Field;
import org.jooq.Record;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ValueMapper {

    private final Map<String, Field<?>> fieldNameMap = new HashMap<>();
    private final Map<String, Mapper<?>> mappers = new HashMap<>();

    public <T> void map(final QueryField dataSourceField, final Field<T> field, final Function<T, Val> handler) {
        fieldNameMap.put(dataSourceField.getFldName(), field);
        mappers.put(dataSourceField.getFldName(), new Mapper<>(field, handler));
    }

    public List<Field<?>> getDbFieldsByName(final String[] fieldNames) {
        return Arrays
                .stream(fieldNames)
                .map(fieldNameMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public Mapper<?>[] getMappersForFieldNames(final String[] fieldNames) {
        final Mapper<?>[] handlers = new Mapper[fieldNames.length];
        for (int i = 0; i < fieldNames.length; i++) {
            handlers[i] = mappers.get(fieldNames[i]);
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
