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

package stroom.data.store.impl.fs.s3v2;


import stroom.meta.shared.Meta;

import java.util.Optional;

public interface ZstdSeekTableCache {

    Optional<ZstdSeekTable> getSeekTable(final Meta meta,
                                         final String childStreamType,
                                         final int segmentCount,
                                         final long fileSize);

    Optional<ZstdSeekTable> getSeekTable(final Meta meta,
                                         final String childStreamType);

    void evict(final Meta meta, final String childStreamType);
}
