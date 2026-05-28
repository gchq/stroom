/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.receive.common;


import stroom.aws.sqs.SqsConfig;
import stroom.util.config.annotations.RequiresProxyRestart;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.config.annotations.RequiresRestart.RestartScope;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
public class S3EventNotificationConfig
        extends AbstractConfig
        implements IsStroomConfig, IsProxyConfig {

    @JsonProperty
    private final List<SqsConfig> sqsConnectors;

    public S3EventNotificationConfig(@JsonProperty("sqsConnectors") final List<SqsConfig> sqsConnectors) {
        this.sqsConnectors = NullSafe.list(sqsConnectors);
    }

    public S3EventNotificationConfig() {
        this.sqsConnectors = Collections.emptyList();
    }

    @RequiresProxyRestart
    @RequiresRestart(RestartScope.SYSTEM)
    public List<SqsConfig> getSqsConnectors() {
        return sqsConnectors;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final S3EventNotificationConfig that = (S3EventNotificationConfig) o;
        return Objects.equals(sqsConnectors, that.sqsConnectors);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(sqsConnectors);
    }

    @Override
    public String toString() {
        return "S3EventNotificationConfig{" +
               "sqsConnectors=" + sqsConnectors +
               '}';
    }
}
