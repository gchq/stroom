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

package stroom.planb.impl.serde.val;

import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValInteger;

import java.nio.ByteBuffer;

public class IntegerValSerde extends SimpleValSerde {

    @Override
    Val readVal(final ByteBuffer byteBuffer) {
        final int i = byteBuffer.getInt();
        return ValInteger.create(i);
    }

    @Override
    void writeVal(final ByteBuffer byteBuffer, final Val val) {
        ValSerdeUtil.writeInteger(val, byteBuffer);
    }

    @Override
    int size() {
        return Integer.BYTES;
    }
}
