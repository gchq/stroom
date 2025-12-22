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

import stroom.pipeline.refdata.store.RefDataValue;
import stroom.pipeline.refdata.store.StringValue;
import stroom.pipeline.refdata.store.offheapstore.RefDataValueProxyConsumer;
import stroom.util.logging.LogUtil;

import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.ReceiverOptions;
import net.sf.saxon.trans.XPathException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StringValueConsumer implements RefDataValueConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(StringValueConsumer.class);

    private final Receiver receiver;

    public StringValueConsumer(final Receiver receiver) {
        this.receiver = receiver;
    }

    @Override
    public void consume(final RefDataValue refDataValue) {
        final String value = ((StringValue) refDataValue).getValue();
        LOGGER.trace("consuming {}", value);

        try {
            receiver.characters(value, RefDataValueProxyConsumer.NULL_LOCATION, ReceiverOptions.WHOLE_TEXT_NODE);
        } catch (final XPathException e) {
            throw new RuntimeException(LogUtil.message("Error passing string {} to receiver", value), e);
        }
    }


    // --------------------------------------------------------------------------------


    public static class Factory implements RefDataValueConsumer.Factory {

        @Override
        public RefDataValueConsumer create(final Receiver receiver,
                                           final PipelineConfiguration pipelineConfiguration) {
            return new StringValueConsumer(receiver);
        }
    }
}
