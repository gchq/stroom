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

package stroom.data.store.impl.fs.s3v2;


import stroom.data.store.impl.fs.DataVolumeDao.DataVolume;

import java.util.Optional;

public interface ZstdDictionaryService {

    Optional<ZstdDictionary> getZstdDictionary(final String dictionaryUuid,
                                               final DataVolume dataVolume);

    Optional<ZstdDictionary> getZstdDictionary(final ZstdDictionaryKey zstdDictionaryKey,
                                               final DataVolume dataVolume);

    /**
     * Record the details of a file to be re-compressed.
     */
    void createReCompressTask(final ZstdDictionaryKey zstdDictionaryKey,
                              final FileKey fileKey);

}
