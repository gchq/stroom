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

package stroom.util.io;

import java.util.Set;

public final class CompressionUtil {

    public static final String SUPPORTED_COMPRESSORS = "" +
            "bzip2, " +
            "deflate, " +
            "gz, " +
            "lz4-block, " +
            "lz4-framed, " +
            "lzma, " +
            "pack200, " +
            "snappy-framed, " +
            "xz, " +
            "zip, " +
            "zstd";

    public static final Set<String> COMPRESSORS = Set.of(SUPPORTED_COMPRESSORS.split(", "));

    public static boolean isSupportedCompressor(final String compressorName) {
        return COMPRESSORS.contains(compressorName.toLowerCase());
    }
}
