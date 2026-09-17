/*
 * Copyright 2026 Crown Copyright
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
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
public class S3EventConfig extends AbstractConfig implements IsStroomConfig, IsProxyConfig {

    public static final StroomDuration DEFAULT_RE_POLL_DURATION = StroomDuration.ofMinutes(5);

    @JsonProperty
    private final StroomDuration rePollDuration;
    @JsonProperty
    private final SqsConfig sqs;

    public S3EventConfig() {
        this.rePollDuration = DEFAULT_RE_POLL_DURATION;
        this.sqs = null;
    }

    @JsonCreator
    public S3EventConfig(@JsonProperty("rePollDuration") final StroomDuration rePollDuration,
                         @JsonProperty("sqs") final SqsConfig sqs) {
        this.rePollDuration = Objects.requireNonNullElse(rePollDuration, DEFAULT_RE_POLL_DURATION);
        this.sqs = sqs;
    }

    @JsonPropertyDescription(
            "This is the time to keep polling for. Once reached polling will stop and will begin again " +
            "at the next scheduled execution time for the job.")
    @JsonProperty
    public StroomDuration getRePollDuration() {
        return rePollDuration;
    }

    @JsonPropertyDescription("The configuration for the AWS SQS queue that will be used to receive S3 events.")
    @JsonProperty
    public SqsConfig getSqs() {
        return sqs;
    }

    @Override
    public String toString() {
        return "S3EventConfig{" +
               "sqs=" + sqs +
               '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final S3EventConfig that = (S3EventConfig) o;
        return Objects.equals(rePollDuration, that.rePollDuration) && Objects.equals(sqs, that.sqs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rePollDuration, sqs);
    }
}
