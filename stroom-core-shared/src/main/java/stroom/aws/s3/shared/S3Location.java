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

package stroom.aws.s3.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

// TODO In theory we don't need region name as bucketName is globally unique in AWS.
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class S3Location {

    @JsonProperty
    private final String regionName;
    @JsonProperty
    private final String bucketName;
    @JsonProperty
    private final String key;

    @JsonCreator
    public S3Location(@JsonProperty("regionName") final String regionName,
                      @JsonProperty("bucketName") final String bucketName,
                      @JsonProperty("key") final String key) {
        this.regionName = Objects.requireNonNull(regionName);
        this.bucketName = Objects.requireNonNull(bucketName);
        this.key = Objects.requireNonNull(key);
    }

    public String regionName() {
        return regionName;
    }

    public String bucketName() {
        return bucketName;
    }

    public String key() {
        return key;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final S3Location that = (S3Location) o;
        return Objects.equals(regionName, that.regionName)
               && Objects.equals(bucketName, that.bucketName)
               && Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(regionName, bucketName, key);
    }

    @Override
    public String toString() {
        return "S3Location[" +
               "regionName=" + regionName + ", " +
               "bucketName=" + bucketName + ", " +
               "key=" + key + ']';
    }
}
