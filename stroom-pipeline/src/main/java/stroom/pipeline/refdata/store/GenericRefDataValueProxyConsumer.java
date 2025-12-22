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

import stroom.pipeline.refdata.store.offheapstore.RefDataValueProxyConsumer;

import com.google.inject.assistedinject.Assisted;
import jakarta.inject.Inject;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.trans.XPathException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GenericRefDataValueProxyConsumer implements RefDataValueProxyConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenericRefDataValueProxyConsumer.class);

    private final Receiver receiver;
    private final PipelineConfiguration pipelineConfiguration;
    private final RefDataValueProxyConsumerFactory refDataValueProxyConsumerFactory;

    @Inject
    public GenericRefDataValueProxyConsumer(@Assisted final Receiver receiver,
                                            @Assisted final PipelineConfiguration pipelineConfiguration,
                                            final RefDataValueProxyConsumerFactory refDataValueProxyConsumerFactory) {
        this.receiver = receiver;
        this.pipelineConfiguration = pipelineConfiguration;
        this.refDataValueProxyConsumerFactory = refDataValueProxyConsumerFactory;
    }

    public void startDocument() throws XPathException {
        LOGGER.trace("startDocument");
        receiver.setPipelineConfiguration(pipelineConfiguration);
        receiver.open();
        receiver.startDocument(0);
    }

    public void endDocument() throws XPathException {
        LOGGER.trace("endDocument");
        receiver.endDocument();
        receiver.close();
    }

    public boolean consume(final RefDataValueProxy refDataValueProxy) throws XPathException {

        LOGGER.trace("consume called for {}", refDataValueProxy);
        return refDataValueProxy.consumeValue(refDataValueProxyConsumerFactory);
    }
}
