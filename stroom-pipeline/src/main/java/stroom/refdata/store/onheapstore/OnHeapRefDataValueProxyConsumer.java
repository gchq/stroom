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

package stroom.refdata.store.onheapstore;

import com.google.inject.assistedinject.Assisted;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.trans.XPathException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.refdata.RefDataValueByteBufferConsumer;
import stroom.refdata.store.AbstractConsumer;
import stroom.refdata.store.FastInfosetValue;
import stroom.refdata.store.RefDataValueProxy;
import stroom.refdata.store.StringValue;
import stroom.refdata.store.offheapstore.FastInfosetByteBufferConsumer;
import stroom.refdata.store.offheapstore.RefDataValueProxyConsumer;
import stroom.refdata.util.ByteBufferUtils;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;

public class OnHeapRefDataValueProxyConsumer
        extends AbstractConsumer
        implements RefDataValueProxyConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(OnHeapRefDataValueProxyConsumer.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(OnHeapRefDataValueProxyConsumer.class);

    private final RefDataValueByteBufferConsumer fastInfosetByteBufferConsumer;
    private final Map<Integer, RefDataValueConsumer.Factory> typeToRefDataValueConsumerFactoryMap;

    @Inject
    public OnHeapRefDataValueProxyConsumer(
            @Assisted final Receiver receiver,
            @Assisted final PipelineConfiguration pipelineConfiguration,
            final FastInfosetByteBufferConsumer.Factory fastInfosetByteBufferConsumerFactory,
            final Map<Integer, RefDataValueConsumer.Factory> typeToRefDataValueConsumerFactoryMap) {

        super(pipelineConfiguration, receiver);
        this.fastInfosetByteBufferConsumer = fastInfosetByteBufferConsumerFactory.create(receiver, pipelineConfiguration);
        this.typeToRefDataValueConsumerFactoryMap = typeToRefDataValueConsumerFactoryMap;
    }

    @Override
    public boolean consume(final RefDataValueProxy refDataValueProxy) throws XPathException {

        return refDataValueProxy.supplyValue()
                .filter(refDataValue -> {
                    // abuse of filter() method, we just want to optionally consume the value

                    // find out what type of value we are dealing with
                    final int typeId = refDataValue.getTypeId();

                    // work out which byteBufferConsumer to use based on the typeId in the value byteBuffer
                    final RefDataValueConsumer.Factory consumerFactory = typeToRefDataValueConsumerFactoryMap.get(typeId);

                    Objects.requireNonNull(consumerFactory, () -> LambdaLogger.buildMessage("No factory found for typeId {}", typeId));
                    final RefDataValueConsumer consumer = consumerFactory.create(receiver, pipelineConfiguration);

                    consumer.consume(refDataValue);

                    if (refDataValue.getTypeId() == StringValue.TYPE_ID) {

                    } else if (refDataValue.getTypeId() == FastInfosetValue.TYPE_ID) {

                        ByteBuffer valueByteBuffer = ((FastInfosetValue) refDataValue).getByteBuffer();
                        LAMBDA_LOGGER.trace(() -> LambdaLogger.buildMessage(
                                "Consuming {}", ByteBufferUtils.byteBufferInfo(valueByteBuffer)));

                        fastInfosetByteBufferConsumer.consumeBytes(receiver, valueByteBuffer);
                    }
                    // always true because we are not really filtering
                    return true;
                })
                .isPresent();
    }

    public interface Factory {
        OnHeapRefDataValueProxyConsumer create(final Receiver receiver,
                                               final PipelineConfiguration pipelineConfiguration);
    }

}
