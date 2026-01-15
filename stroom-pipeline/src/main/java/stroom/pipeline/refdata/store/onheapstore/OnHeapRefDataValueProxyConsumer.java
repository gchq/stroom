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

import stroom.pipeline.refdata.store.AbstractConsumer;
import stroom.pipeline.refdata.store.RefDataValueProxy;
import stroom.pipeline.refdata.store.ValueConsumerId;
import stroom.pipeline.refdata.store.offheapstore.RefDataValueProxyConsumer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.google.inject.assistedinject.Assisted;
import jakarta.inject.Inject;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;

import java.util.Map;
import java.util.Objects;

public class OnHeapRefDataValueProxyConsumer
        extends AbstractConsumer
        implements RefDataValueProxyConsumer {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(OnHeapRefDataValueProxyConsumer.class);

    private final Map<ValueConsumerId, RefDataValueConsumer.Factory> typeToRefDataValueConsumerFactoryMap;

    @Inject
    public OnHeapRefDataValueProxyConsumer(
            @Assisted final Receiver receiver,
            @Assisted final PipelineConfiguration pipelineConfiguration,
            final Map<ValueConsumerId, RefDataValueConsumer.Factory> typeToRefDataValueConsumerFactoryMap) {

        super(pipelineConfiguration, receiver);
        this.typeToRefDataValueConsumerFactoryMap = typeToRefDataValueConsumerFactoryMap;
    }

    @Override
    public boolean consume(final RefDataValueProxy refDataValueProxy) {

        return refDataValueProxy.supplyValue()
                .filter(refDataValue -> {
                    // abuse of filter() method, we just want to optionally consume the value

                    // find out what type of value we are dealing with
                    final byte typeId = refDataValue.getTypeId();

                    // work out which byteBufferConsumer to use based on the typeId in the value byteBuffer
                    final RefDataValueConsumer.Factory consumerFactory = typeToRefDataValueConsumerFactoryMap
                            .get(new ValueConsumerId(typeId));

                    Objects.requireNonNull(consumerFactory, () ->
                            LogUtil.message("No factory found for typeId {}", typeId));
                    final RefDataValueConsumer consumer = consumerFactory.create(receiver, pipelineConfiguration);
                    consumer.consume(refDataValue);
                    // always true because we are not really filtering
                    return true;
                })
                .isPresent();
    }


    // --------------------------------------------------------------------------------


    public interface Factory {

        OnHeapRefDataValueProxyConsumer create(final Receiver receiver,
                                               final PipelineConfiguration pipelineConfiguration);
    }
}
