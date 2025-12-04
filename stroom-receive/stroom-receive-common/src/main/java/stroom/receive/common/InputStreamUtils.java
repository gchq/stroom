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

package stroom.receive.common;

import stroom.util.io.ByteSize;

import org.apache.commons.io.input.BoundedInputStream;

import java.io.IOException;
import java.io.InputStream;

public class InputStreamUtils {

    private InputStreamUtils() {}

    public static BoundedInputStream getBoundedInputStream(final InputStream inputStream, final ByteSize maxSize)
            throws IOException {
        return BoundedInputStream.builder()
                .setInputStream(inputStream)
                .setMaxCount(maxSize == null ? -1 : maxSize.getBytes())
                .setOnMaxCount((max, count) -> {
                    throw new ContentTooLargeException("Maximum request size exceeded (" + ByteSize.ofBytes(max) + ")");
                })
                .get();
    }

}
