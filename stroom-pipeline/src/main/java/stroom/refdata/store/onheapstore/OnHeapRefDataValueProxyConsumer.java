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
import net.sf.saxon.event.ReceiverOptions;
import net.sf.saxon.trans.XPathException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.refdata.RefDataValueByteBufferConsumer;
import stroom.refdata.store.FastInfosetValue;
import stroom.refdata.store.RefDataValue;
import stroom.refdata.store.RefDataValueProxy;
import stroom.refdata.store.StringValue;
import stroom.refdata.store.offheapstore.AbstractByteBufferConsumer;
import stroom.refdata.store.offheapstore.AbstractRefDataValueProxyConsumer;
import stroom.refdata.store.offheapstore.FastInfosetByteBufferConsumer;
import stroom.refdata.store.offheapstore.RefDataValueProxyConsumer;
import stroom.refdata.util.ByteBufferUtils;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import java.nio.ByteBuffer;
import java.util.Map;

public class OnHeapRefDataValueProxyConsumer
        extends AbstractRefDataValueProxyConsumer
        implements RefDataValueProxyConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(OnHeapRefDataValueProxyConsumer.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(OnHeapRefDataValueProxyConsumer.class);

    private final RefDataValueByteBufferConsumer fastInfosetByteBufferConsumer;

    @Inject
    public OnHeapRefDataValueProxyConsumer(
            @Assisted final Receiver receiver,
            @Assisted final PipelineConfiguration pipelineConfiguration,
            final FastInfosetByteBufferConsumer.Factory fastInfosetByteBufferConsumerFactory,
            final Map<Integer, AbstractByteBufferConsumer.Factory> typeToByteBufferConsumerFactoryMap) {

        super(pipelineConfiguration, receiver);
        this.fastInfosetByteBufferConsumer = fastInfosetByteBufferConsumerFactory.create(receiver, pipelineConfiguration);
    }

    @Override
    public boolean consume(final RefDataValueProxy refDataValueProxy) throws XPathException {

        return refDataValueProxy.supplyValue()
                .filter(refDataValue -> {
                    // abuse of filter() method, we just want to optionally consume the value

                    if (refDataValue.getTypeId() == StringValue.TYPE_ID) {
                        String value = ((StringValue) refDataValue).getValue();
                        LOGGER.trace("consuming {}", value);

                        try {
                            receiver.characters(value, RefDataValueProxyConsumer.NULL_LOCATION, ReceiverOptions.WHOLE_TEXT_NODE);
                        } catch (XPathException e) {
                            throw new RuntimeException(LambdaLogger.buildMessage("Error passing string {} to receiver", value), e);
                        }

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

    public interface RefDataValueConsumer {
        boolean consume(final RefDataValue refDataValue);
    }

    public static class StringValueConsumer implements RefDataValueConsumer {

        @Override
        public boolean consume(final RefDataValue refDataValue) {
            return false;
        }
    }
}
