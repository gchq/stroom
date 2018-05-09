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

package stroom.refdata.saxevents;

import stroom.util.logging.LambdaLogger;

import java.nio.ByteBuffer;

/**
 * An {@link EventListValue} is represented in byte form as
 * [typeId][value], where typeid is the byte[] value of the typeId short and
 * value is the byte[] of the underlying value
 */
public abstract class EventListValue extends AbstractPoolValue {

    /**
     * @return A code to represent the class of this, unique within all sub-classes of {@link EventListValue}
     */
    public abstract short getTypeId();

    public static short getTypeId(final ByteBuffer byteBuffer){
        return byteBuffer.getShort();
    }
    static EventListValue fromByteBuffer(final ByteBuffer byteBuffer) {

        final short typeId = getTypeId(byteBuffer);

        final EventListValue value;
        if (typeId == FastInfosetValue.TYPE_ID) {
            value = FastInfosetValue.fromByteBuffer(byteBuffer);
        } else if (typeId == StringValue.TYPE_ID){
            value = StringValue.fromByteBuffer(byteBuffer);
        } else {
            throw new RuntimeException(LambdaLogger.buildMessage("Unexpected typeId value {}", typeId));
        }

        return value;
    }

    static ByteBuffer extractValueBytes(final ByteBuffer byteBuffer) {

        // Advance the original buffer paste the typeId portion then slice it to get a new view
        byteBuffer.mark();
        getTypeId(byteBuffer);
        ByteBuffer newByteBuffer = byteBuffer.slice();
        byteBuffer.reset(); //return to the mark
        return newByteBuffer;
    }

}
