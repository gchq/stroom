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

package stroom.planb.impl.serde.keyprefix;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.serde.val.ValSerdeUtil;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDouble;

import java.nio.ByteBuffer;

public class DoubleKeySerde extends SimpleKeyPrefixSerde {

    public DoubleKeySerde(final ByteBuffers byteBuffers) {
        super(byteBuffers, Double.BYTES);
    }

    @Override
    Val readPrefix(final ByteBuffer byteBuffer) {
        final double d = byteBuffer.getDouble();
        return ValDouble.create(d);
    }

    @Override
    void writePrefix(final Val val, final ByteBuffer byteBuffer) {
        ValSerdeUtil.writeDouble(val, byteBuffer);
    }
}
