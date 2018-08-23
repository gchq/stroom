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

package stroom.refdata.store.offheapstore.serdes;

import stroom.refdata.store.offheapstore.lmdb.serde.Serde;
import stroom.refdata.util.ByteBufferUtils;
import stroom.util.logging.LambdaLogger;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class StringSerde implements Serde<String> {

    @Override
    public String deserialize(final ByteBuffer byteBuffer) {
        try {
            String str = StandardCharsets.UTF_8.decode(byteBuffer).toString();
            byteBuffer.flip();
            return str;
        } catch (Exception e) {
            throw new RuntimeException(LambdaLogger.buildMessage("Unable to decode string from byteBuffer {}",
                    ByteBufferUtils.byteBufferInfo(byteBuffer)), e);
        }
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer, final String str) {
        byteBuffer.put(str.getBytes(StandardCharsets.UTF_8));
        byteBuffer.flip();
    }
}
