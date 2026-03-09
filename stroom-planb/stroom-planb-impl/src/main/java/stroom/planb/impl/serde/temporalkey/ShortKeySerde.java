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

package stroom.planb.impl.serde.temporalkey;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.serde.time.TimeSerde;
import stroom.planb.impl.serde.val.ValSerdeUtil;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValShort;

import java.nio.ByteBuffer;

public class ShortKeySerde extends SimpleTemporalKeySerde {

    public ShortKeySerde(final ByteBuffers byteBuffers, final TimeSerde timeSerde) {
        super(byteBuffers, timeSerde, Short.BYTES);
    }

    @Override
    Val readPrefix(final ByteBuffer byteBuffer) {
        final short s = byteBuffer.getShort();
        return ValShort.create(s);
    }

    @Override
    void writePrefix(final TemporalKey key, final ByteBuffer byteBuffer) {
        ValSerdeUtil.writeShort(key.getPrefix().getVal(), byteBuffer);
    }
}
