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

package stroom.pipeline.refdata.store.onheapstore;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.pipeline.refdata.store.FastInfosetValue;
import stroom.pipeline.refdata.store.RefDataValue;
import stroom.pipeline.refdata.store.offheapstore.FastInfosetByteBufferConsumer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;

import java.nio.ByteBuffer;

public class FastInfosetValueConsumer implements RefDataValueConsumer {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FastInfosetValueConsumer.class);

    private final FastInfosetByteBufferConsumer fastInfosetByteBufferConsumer;

    public FastInfosetValueConsumer(final Receiver receiver,
                                    final PipelineConfiguration pipelineConfiguration) {
        this.fastInfosetByteBufferConsumer = new FastInfosetByteBufferConsumer(receiver, pipelineConfiguration);
    }

    @Override
    public void consume(final RefDataValue refDataValue) {
        if (refDataValue instanceof final FastInfosetValue fastInfosetValue) {
            // Duplicate to save altering the limit/pos for other users of this buffer
            final ByteBuffer valueByteBuffer = fastInfosetValue.getByteBuffer().duplicate();
            LOGGER.trace(() -> LogUtil.message(
                    "Consuming {}", ByteBufferUtils.byteBufferInfo(valueByteBuffer)));

            fastInfosetByteBufferConsumer.consumeBytes(valueByteBuffer);
        } else {
            throw new RuntimeException(LogUtil.message("Expecting refDataValue of type {} to be {}",
                    NullSafe.get(refDataValue, Object::getClass, Class::getName),
                    FastInfosetValue.class.getName()));
        }
    }


    // --------------------------------------------------------------------------------


    public static class Factory implements RefDataValueConsumer.Factory {

        @Override
        public RefDataValueConsumer create(final Receiver receiver,
                                           final PipelineConfiguration pipelineConfiguration) {
            return new FastInfosetValueConsumer(receiver, pipelineConfiguration);
        }
    }
}
