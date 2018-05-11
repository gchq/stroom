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

package stroom.refdata.offheapstore;

import java.nio.ByteBuffer;

public abstract class RefDataValue {

    public abstract boolean equals(Object obj);

    public abstract int hashCode();

    /**
     * @return A code to represent the class of this, unique within all sub-classes of {@link RefDataValue}
     */
    public abstract byte getTypeId();

    /**
     * Puts the objects value in byte serialized form into the passed {@link ByteBuffer}. The position of the
     * buffer after putValue will be after the put value.
     */
    abstract void putValue(final ByteBuffer byteBuffer);

}
