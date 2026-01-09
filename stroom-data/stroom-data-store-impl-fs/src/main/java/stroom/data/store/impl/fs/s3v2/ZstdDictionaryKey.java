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


import stroom.util.shared.NullSafe;

public record ZstdDictionaryKey(String feedName,
                                String streamTypeName,
                                String childStreamType) {

    public ZstdDictionaryKey {
        NullSafe.requireNonBlankString(feedName);
        NullSafe.requireNonBlankString(streamTypeName);
    }

    public static ZstdDictionaryKey of(final String feedName,
                                       final String streamTypeName) {
        return new ZstdDictionaryKey(feedName, streamTypeName, null);
    }

    public static ZstdDictionaryKey of(final String feedName,
                                       final String streamTypeName,
                                       final String childStreamType) {
        return new ZstdDictionaryKey(feedName, streamTypeName, childStreamType);
    }

//    public FileKey createFileKey(final DataVolume dataVolume, final Meta meta) {
//        Objects.requireNonNull(dataVolume);
//        Objects.requireNonNull(meta);
//        return new FileKey(
//                Objects.requireNonNull(dataVolume).getVolumeId(),
//                Objects.requireNonNull(meta),
//                streamTypeName,
//        )
//    }
}
