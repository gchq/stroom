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
import stroom.util.logging.LambdaLogger;

import java.nio.ByteBuffer;

public class FastInfoSetValueSerde implements RefDatValueSubSerde {

    @Override
    public RefDataValue deserialize(final ByteBuffer byteBuffer) {
        int referenceCount = extractReferenceCount(byteBuffer);
        byte[] bytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(bytes);
        byteBuffer.flip();
        return new FastInfosetValue(referenceCount, bytes);
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer, final RefDataValue refDataValue) {
        try {
            putReferenceCount(refDataValue, byteBuffer);
            byteBuffer.put(((FastInfosetValue) refDataValue).getValueBytes());
        } catch (ClassCastException e) {
            throw new RuntimeException(LambdaLogger.buildMessage("Unable to cast {} to {}",
                    refDataValue.getClass().getCanonicalName(), FastInfosetValue.class.getCanonicalName()), e);
        }
        byteBuffer.flip();
    }
}
