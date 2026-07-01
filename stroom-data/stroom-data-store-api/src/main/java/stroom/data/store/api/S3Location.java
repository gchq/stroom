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

package stroom.data.store.api;

import org.jspecify.annotations.NullMarked;

import java.util.Objects;

// TODO In theory we don't need region name as bucketName is globally unique in AWS.
@NullMarked
public record S3Location(String regionName,
                         String bucketName,
                         String key) {

    public S3Location {
        Objects.requireNonNull(regionName);
        Objects.requireNonNull(bucketName);
        Objects.requireNonNull(key);
    }
}
