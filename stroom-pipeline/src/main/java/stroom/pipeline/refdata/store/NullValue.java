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

package stroom.pipeline.refdata.store;

import java.nio.ByteBuffer;

/**
 * Represents an entry with a null/empty/blank value
 */
public class NullValue implements RefDataValue {

    /**
     * MUST not change this else it is stored in the ref store. MUST be unique over all
     * {@link RefDataValue} impls.
     */
    public static final byte TYPE_ID = 3;
    private static final ByteBuffer ZERO_LENGTH_BTYE_BUFFER = ByteBuffer.wrap(new byte[0]);
    private static final NullValue INSTANCE = new NullValue();

    private NullValue() {
    }

    public static NullValue getInstance() {
        return INSTANCE;
    }

    @Override
    public long getValueHashCode(final ValueStoreHashAlgorithm valueStoreHashAlgorithm) {
        return valueStoreHashAlgorithm.hash(ZERO_LENGTH_BTYE_BUFFER);
    }

    @Override
    public byte getTypeId() {
        return TYPE_ID;
    }

    @Override
    public boolean isNullValue() {
        return true;
    }
}
