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

package stroom.refdata.saxevents;

import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.ReceiverOptions;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.TinyBuilder;
import stroom.util.logging.LambdaLogger;

import java.nio.ByteBuffer;
import java.util.Optional;

public class StringValueConsumer extends EventListProxyConsumer {

//    public StringValueConsumer(final Receiver receiver, final PipelineConfiguration pipe) {
//        super(receiver, pipe);
//    }


    StringValueConsumer(final XPathContext context) {
        super(context);
    }

    private Sequence convertByteBufferToSequence(final ByteBuffer byteBuffer) {
        // Initialise objects for de-serialising the bytebuffer
        final PipelineConfiguration pipelineConfiguration = buildPipelineConfguration();
        final TinyBuilder receiver = new TinyBuilder(pipelineConfiguration);
        try {
            startDocument(receiver, pipelineConfiguration);
        } catch (XPathException e) {
            throw new RuntimeException(LambdaLogger.buildMessage("Error starting document"), e);
        }

        StringValue stringValue = StringValue.fromByteBuffer(byteBuffer);

        try {
            receiver.characters(stringValue.getValue(), NULL_LOCATION, ReceiverOptions.WHOLE_TEXT_NODE);
        } catch (XPathException e) {
            throw new RuntimeException(LambdaLogger.buildMessage("Error passing string {} to receiver", stringValue), e);
        }

        try {
            endDocument(receiver);
        } catch (XPathException e) {
            throw new RuntimeException(LambdaLogger.buildMessage("Error ending document"), e);
        }
        final Sequence sequence = receiver.getCurrentRoot();
        // Reset the builder, detaching it from the constructed document.
        receiver.reset();
        return sequence;
    }

    @Override
    public Sequence map(final ValueProxy<EventListValue> eventListProxy) {
        if (eventListProxy == null) {
            return EmptyAtomicSequence.getInstance();
        }

        Class valueClazz = eventListProxy.getValueClazz();
        if (valueClazz != StringValue.class) {
            throw new RuntimeException(LambdaLogger.buildMessage("Unexpected type {}", valueClazz.getCanonicalName()));
        }

        // Get the value of the proxy and if found map it
        Optional<Sequence> optSequence = eventListProxy.mapValue(this::convertByteBufferToSequence);

        return optSequence.orElseGet(EmptyAtomicSequence::getInstance);
    }

//    public void consume(final ValueProxy<EventListValue> eventListProxy) {
//
//        Class valueClazz = eventListProxy.getValueClazz();
//        if (valueClazz != StringValue.class) {
//            throw new RuntimeException(LambdaLogger.buildMessage("Unexpected type {}", valueClazz.getCanonicalName()));
//        }
//        // get the value the proxy is proxying for and use it inside the transaction
//        eventListProxy.mapValue(StringValue::fromByteBuffer)
//                .map(StringValue::getValue)
//                .ifPresent(str -> {
//                    try {
//                        receiver.characters(str, NULL_LOCATION, ReceiverOptions.WHOLE_TEXT_NODE);
//                    } catch (XPathException e) {
//                        throw new RuntimeException(LambdaLogger.buildMessage("Error passing string {} to receiver", str), e);
//                    }
//                });
//    }
}
