/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.util;

import com.codahale.metrics.health.HealthCheck;

import java.util.NavigableMap;
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
    static <T, K, U> Collector<T, ?, NavigableMap<K, U>> buildTreeMapCollector(
            final Function<? super T, ? extends K> keyMapper,
            final Function<? super T, ? extends U> valueMapper) {

        return Collectors.toMap(
                keyMapper,
                valueMapper,
                (v1, v2) -> {
                    throw new RuntimeException(String.format("Duplicate key for values %s and %s", v1, v2));
                },
                TreeMap::new);
    }
}
