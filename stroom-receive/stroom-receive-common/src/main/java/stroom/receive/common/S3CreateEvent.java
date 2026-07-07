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


import stroom.data.store.api.S3Location;
import stroom.meta.api.AttributeMap;
import stroom.receive.common.S3EventResource.S3EventRequest;
import stroom.util.shared.NullSafe;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

@NullMarked
public record S3CreateEvent(S3Location s3Location, AttributeMap attributeMap) {

    public S3CreateEvent(final S3Location s3Location,
                         @Nullable final AttributeMap attributeMap) {
        Objects.requireNonNull(s3Location);
        this.s3Location = s3Location;
        this.attributeMap = Objects.requireNonNullElseGet(attributeMap, AttributeMap::new);
    }

    public static S3CreateEvent create(final S3EventRequest request) {
        Objects.requireNonNull(request);
        return new S3CreateEvent(
                request.getS3Location(),
                NullSafe.get(request.getMetaData(), AttributeMap::new));
    }
}
