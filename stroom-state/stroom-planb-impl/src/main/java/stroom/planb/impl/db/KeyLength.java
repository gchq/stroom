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

package stroom.planb.impl.db;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.nio.ByteBuffer;

public class KeyLength {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(KeyLength.class);

    private KeyLength() {
        // Util
    }

    public static void check(final ByteBuffer byteBuffer, final int max) {
        try {
            check(byteBuffer.remaining(), max);
        } catch (final KeyLengthException e) {
            LOGGER.debug(e::getMessage, e);
            LOGGER.trace(() -> e.getMessage() +
                               "\n\n" +
                               ByteBufferUtils.byteBufferToHexAll(byteBuffer.duplicate()), e);
            throw e;
        }
    }

    public static void check(final int length, final int max) {
        if (length > max) {
            throw new KeyLengthException(max);
        }
    }
}
