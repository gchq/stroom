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

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;

import java.util.Map;

/**
 * Implementations provide one or more named {@link Metric}
 */
public interface HasMetrics {

    /**
     * Called ONCE on system boot after the guice bindings have been completed.
     * <p>
     * If your metrics are NOT know at boot time, e.g. because you have dynamically created
     * queues that each need a metric, then instead use
     * {@link SharedMetricRegistries#getDefault()} to get the {@link MetricRegistry}
     * then call {@link MetricRegistry#register(String, Metric)}.
     * </p>
     * <p>
     * The metric name should be unique across the whole system. Suggest
     * prefixing with the java package name it lives in.
     * </p>
     *
     * @return A map of {@link Metric} keyed by the {@link Metric}'s name.
     */
    Map<String, Metric> getMetrics();
}
