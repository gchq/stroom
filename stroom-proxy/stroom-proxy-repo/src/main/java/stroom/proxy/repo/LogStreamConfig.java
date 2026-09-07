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

package stroom.proxy.repo;

import stroom.meta.api.StandardHeaderArguments;
import stroom.util.collections.CollectionUtil;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;


@JsonPropertyOrder(alphabetic = true)
public class LogStreamConfig extends AbstractConfig implements IsProxyConfig {

    private static final boolean USE_MDC_DEFAULT = false;

    // Is a list, so they get logged in the desired order
    private final List<String> metaKeys;
    private final boolean useMappedDiagnosticContext;

    public LogStreamConfig() {
        this(List.of(
                        StandardHeaderArguments.GUID,
                        StandardHeaderArguments.RECEIPT_ID,
                        StandardHeaderArguments.FEED,
                        StandardHeaderArguments.TYPE,
                        StandardHeaderArguments.SYSTEM,
                        StandardHeaderArguments.ENVIRONMENT,
                        StandardHeaderArguments.REMOTE_HOST,
                        StandardHeaderArguments.REMOTE_ADDRESS,
                        StandardHeaderArguments.REMOTE_DN,
                        StandardHeaderArguments.REMOTE_CERT_EXPIRY,
                        StandardHeaderArguments.DATA_RECEIPT_RULE),
                USE_MDC_DEFAULT);
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public LogStreamConfig(@JsonProperty("metaKeys") final List<String> metaKeys,
                           @JsonProperty("useMappedDiagnosticContext") final Boolean useMappedDiagnosticContext) {
        this.metaKeys = CollectionUtil.cleanItems(metaKeys, String::trim, true);
        this.useMappedDiagnosticContext = Objects.requireNonNullElse(useMappedDiagnosticContext, USE_MDC_DEFAULT);
    }

    @JsonProperty
    @JsonPropertyDescription("Optional log line with header attributes output as defined by this property." +
                             "The headers attributes that will be output in log lines." +
                             "They will be output in the order that they appear in this list." +
                             "Duplicates will be ignored, case does not matter.")
    public List<String> getMetaKeys() {
        return metaKeys;
    }

    @JsonProperty
    @JsonPropertyDescription("Set this to true when you want to use JSON format logging. MDC enables structured " +
                             "logging of the send/receive meta data. If not using JSON logging, this can be set " +
                             "to false (the default).")
    public boolean isUseMappedDiagnosticContext() {
        return useMappedDiagnosticContext;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final LogStreamConfig that = (LogStreamConfig) o;
        return useMappedDiagnosticContext == that.useMappedDiagnosticContext
               && Objects.equals(metaKeys, that.metaKeys);
    }

    @Override
    public int hashCode() {
        return Objects.hash(metaKeys, useMappedDiagnosticContext);
    }

    @Override
    public String toString() {
        return "LogStreamConfig{" +
               "metaKeys=" + metaKeys +
               ", useMappedDiagnosticContext=" + useMappedDiagnosticContext +
               '}';
    }
}
