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

package stroom.pipeline.refdata.store.offheapstore;

import stroom.pipeline.refdata.RefDataValueByteBufferConsumer;
import stroom.pipeline.refdata.store.AbstractConsumer;
import stroom.pipeline.refdata.store.ByteBufferConsumerId;
import stroom.pipeline.refdata.store.RefDataValueProxy;
import stroom.util.logging.LogUtil;

import com.google.inject.assistedinject.Assisted;
import jakarta.inject.Inject;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;


public class OffHeapRefDataValueProxyConsumer
        extends AbstractConsumer
        implements RefDataValueProxyConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(OffHeapRefDataValueProxyConsumer.class);

    // injected map of typeId to the appropriate bytebuffer consumer factory
    private final Map<ByteBufferConsumerId, RefDataValueByteBufferConsumer.Factory> typeToByteBufferConsumerFactoryMap;

    @SuppressWarnings("checkstyle:LineLength")
    @Inject
    public OffHeapRefDataValueProxyConsumer(
            @Assisted final Receiver receiver,
            @Assisted final PipelineConfiguration pipelineConfiguration,
            final Map<ByteBufferConsumerId, RefDataValueByteBufferConsumer.Factory> typeToByteBufferConsumerFactoryMap) {

        super(pipelineConfiguration, receiver);
        this.typeToByteBufferConsumerFactoryMap = typeToByteBufferConsumerFactoryMap;
    }

    @Override
    public boolean consume(final RefDataValueProxy refDataValueProxy) {
        LOGGER.trace("consume({})", refDataValueProxy);

        //
        return refDataValueProxy.consumeBytes(typedByteBuffer -> {

            // find out what type of value we are dealing with
            final byte typeId = typedByteBuffer.getTypeId();

            // work out which byteBufferConsumer to use based on the typeId in the value byteBuffer
            final RefDataValueByteBufferConsumer.Factory consumerFactory = typeToByteBufferConsumerFactoryMap.get(
                    new ByteBufferConsumerId(typeId));

            Objects.requireNonNull(consumerFactory, () -> LogUtil.message("No factory found for typeId {}", typeId));
            final RefDataValueByteBufferConsumer consumer = consumerFactory.create(receiver, pipelineConfiguration);

            Objects.requireNonNull(consumer, () -> LogUtil.message("No consumer for typeId {}", typeId));

            // now we have the appropriate consumer for the value type, consume the value
            consumer.consumeBytes(typedByteBuffer.getByteBuffer());
        });
    }


    // --------------------------------------------------------------------------------


    public interface Factory {

        OffHeapRefDataValueProxyConsumer create(final Receiver receiver,
                                                final PipelineConfiguration pipelineConfiguration);
    }
}
