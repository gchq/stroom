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

package stroom.util.metrics;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;

/**
 * Interface for creating and registering {@link com.codahale.metrics.Metric}s.
 * <p>
 * Do not use {@link SharedMetricRegistries#getDefault()} as this relies on a static
 * {@link MetricRegistry} that causes problems in tests.
 * </p>
 */
public interface Metrics {

    LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(HasMetrics.class);

    String AGE_MS = "ageMs";
    String COUNT = "count";
    String DELTA = "delta";
    String FILE_COUNT = "fileCount";
    String HANDLE = "handle";
    String POSITION = "position";
    String READ = "read";
    String RECEIVE = "receive";
    String SEND = "send";
    String SIZE = "size";
    String SIZE_IN_BYTES = "sizeInBytes";
    String STREAM = "stream";
    String WRITE = "write";

    /**
     * A builder for registering and in some cases also creating a {@link com.codahale.metrics.Metric}
     *
     * @param clazz The class that owns the metric. The fully qualified class name
     *              will be used as the prefix in the metric name.
     */
    default MetricRegistrationBuilder registrationBuilder(final Class<?> clazz) {
        return new MetricRegistrationBuilder(getRegistry(), clazz);
    }

    /**
     * Mostly for use in tests. Metrics should be registered using
     * {@link Metrics#registrationBuilder(Class)}
     */
    MetricRegistry getRegistry();
}
