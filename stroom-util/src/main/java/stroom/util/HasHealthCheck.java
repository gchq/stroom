package stroom.util;

import com.codahale.metrics.health.HealthCheck;

import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public interface HasHealthCheck {
    HealthCheck.Result getHealth();

    default HealthCheck getHealthCheck() {
        return new HealthCheck() {
            @Override
            protected Result check() {
                return getHealth();
            }
        };
    }

    /**
     * Return a Collector that collects to a TreeMap using the supplied key and value mappers. Duplicate keys will
     * result in a {@link RuntimeException}. Useful for creating sorted maps to go into HealthCheck detail values
     */
    static <T, K, U> Collector<T, ?, TreeMap<K, U>> buildTreeMapCollector(
            Function<? super T, ? extends K> keyMapper,
            Function<? super T, ? extends U> valueMapper) {

        return Collectors.toMap(
                keyMapper,
                valueMapper,
                (v1, v2) -> {
                    throw new RuntimeException(String.format("Duplicate key for values %s and %s", v1, v2));
                },
                TreeMap::new);
    }
}