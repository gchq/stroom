/*
 * Copyright 2018 Crown Copyright
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
 *
 */

package stroom.refdata.offheapstore.serdes;

import stroom.refdata.offheapstore.FastInfosetValue;
import stroom.refdata.offheapstore.RefDataValue;
import stroom.refdata.offheapstore.StringValue;
import stroom.util.logging.LambdaLogger;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class StringValueSerde implements RefDataValueSubSerde {

    @Override
    public RefDataValue deserialize(final ByteBuffer subBuffer) {
        int referenceCount = getReferenceCount(subBuffer);
        String str = decodeString(subBuffer);
        subBuffer.flip();
        return new StringValue(referenceCount, str);
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer, final RefDataValue refDataValue) {
        try {
            putReferenceCount(refDataValue, byteBuffer);
            final StringValue stringValue = (StringValue) refDataValue;
            byteBuffer.put(stringValue.getValue().getBytes(StandardCharsets.UTF_8));
            byteBuffer.flip();
        } catch (ClassCastException e) {
            throw new RuntimeException(LambdaLogger.buildMessage("Unable to cast {} to {}",
                    refDataValue.getClass().getCanonicalName(), FastInfosetValue.class.getCanonicalName()), e);
        }
    }

    /**
     * Absolute method that extracts the string value from its place in the buffer.
     * Does not change the passed buffer.
     */
    public static String extractStringValue(final ByteBuffer subBuffer) {
        // advance a copy of the buffer to the value part
        subBuffer.position(VALUE_OFFSET);
        final ByteBuffer valueBuffer = subBuffer.slice();
        subBuffer.rewind();
        return StandardCharsets.UTF_8.decode(valueBuffer).toString();

    }

    /**
     * Reads a string from the passed buffer. Teh buffer is expected to have its position
     * set to the beginning of the string portion. The position will be changed by this
     * method.
     */
    public static String decodeString(final ByteBuffer byteBuffer) {
        return StandardCharsets.UTF_8.decode(byteBuffer).toString();
    }
}
