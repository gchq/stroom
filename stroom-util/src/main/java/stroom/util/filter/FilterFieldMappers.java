package stroom.util.filter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A wrapper for a collection of {@link FilterFieldMapper<T_ROW>}
 */
public class FilterFieldMappers<T_ROW> {

    private final Map<String, FilterFieldMapper<T_ROW>> map;

    private FilterFieldMappers(final Map<String, FilterFieldMapper<T_ROW>> map) {
        this.map = map;
    }

    @SafeVarargs
    public static <T_ROW> FilterFieldMappers<T_ROW> of(final FilterFieldMapper<T_ROW>... fieldMappers) {
        return of(Arrays.asList(fieldMappers));
    }

    public static <T_ROW> FilterFieldMappers<T_ROW> of(final Collection<FilterFieldMapper<T_ROW>> fieldMappers) {

        return new FilterFieldMappers<>(Optional.ofNullable(fieldMappers)
                .map(fieldMappers2 -> fieldMappers2.stream()
                        .collect(Collectors.toMap(
                                fieldMapper -> fieldMapper.getFieldDefinition().getFilterQualifier().toLowerCase(),
                                Function.identity())))
                .orElseGet(Collections::emptyMap));
    }

    public int size() {
        return map.size();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public FilterFieldMapper<T_ROW> get(final String fieldQualifier) {
        return map.get(fieldQualifier);
    }

    public Set<String> getFieldQualifiers() {
        return map.keySet();
    }

    public Collection<FilterFieldMapper<T_ROW>> getFieldMappers() {
        return map.values();
    }

    @Override
    public String toString() {
        return map.values().toString();
    }
}
