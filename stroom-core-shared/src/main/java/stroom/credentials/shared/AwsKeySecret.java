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

package stroom.credentials.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/// A static AWS access key: an access key ID and its secret access key.
@JsonPropertyOrder({
        "accessKeyId",
        "secretAccessKey"
})
@JsonInclude(Include.NON_NULL)
public final class AwsKeySecret implements Secret {

    @JsonProperty
    private final String accessKeyId;
    @JsonProperty
    private final String secretAccessKey;

    /// @param accessKeyId     The access key ID, such as `AKIA...`.
    /// @param secretAccessKey The secret access key.
    @JsonCreator
    public AwsKeySecret(@JsonProperty("accessKeyId") final String accessKeyId,
                        @JsonProperty("secretAccessKey") final String secretAccessKey) {
        this.accessKeyId = accessKeyId;
        this.secretAccessKey = secretAccessKey;
    }

    /// @return The access key ID.
    public String getAccessKeyId() {
        return accessKeyId;
    }

    /// @return The secret access key.
    public String getSecretAccessKey() {
        return secretAccessKey;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AwsKeySecret that = (AwsKeySecret) o;
        return Objects.equals(accessKeyId, that.accessKeyId)
               && Objects.equals(secretAccessKey, that.secretAccessKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accessKeyId, secretAccessKey);
    }

    // The secret access key is left out.
    @Override
    public String toString() {
        return "AwsKeySecret{accessKeyId='" + accessKeyId + "'}";
    }
}
