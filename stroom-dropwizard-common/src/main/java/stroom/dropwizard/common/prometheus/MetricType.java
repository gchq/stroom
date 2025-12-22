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

/*
 * This file was copied from https://github.com/dhatim/dropwizard-prometheus
 * at commit a674a1696a67186823a464383484809738665282 (v4.0.1-2)
 * and modified to work within the Stroom code base. All subsequent
 * modifications from the original are also made under the Apache 2.0 licence
 * and are subject to Crown Copyright.
 */

/*
 * Copyright 2025 github.com/dhatim
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

package stroom.dropwizard.common.prometheus;

/**
 * Dropwizard metric type with mapping to {@link PrometheusMetricType}
 */
enum MetricType {
    COUNTER("counter", PrometheusMetricType.GAUGE), // gauge
    GAUGE("gauge", PrometheusMetricType.GAUGE), // gauge
    HISTOGRAM("histogram", PrometheusMetricType.SUMMARY), // summary
    METER("meter", PrometheusMetricType.COUNTER), // counter
    TIMER("timer", PrometheusMetricType.SUMMARY), // summary
    ;

    private final String text;
    private final PrometheusMetricType prometheusMetricType;

    MetricType(final String text, final PrometheusMetricType prometheusMetricType) {
        this.text = text;
        this.prometheusMetricType = prometheusMetricType;
    }

    public String getText() {
        return text;
    }

    public PrometheusMetricType getPrometheusMetricType() {
        return prometheusMetricType;
    }

    @Override
    public String toString() {
        return name() + " ("
               + PrometheusMetricType.class.getSimpleName() + "." + getPrometheusMetricType()
               + ")";
    }
}
