/*
 * Copyright 2016 Crown Copyright
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

package stroom.pipeline.xsltfunctions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import stroom.pipeline.state.StreamHolder;
import stroom.refdata.ReferenceData;
import stroom.refdata.ReferenceDataResult;
import stroom.refdata.offheapstore.RefDataValueProxy;
import stroom.refdata.offheapstore.RefDataValueProxyConsumer;
import stroom.util.shared.Severity;

import javax.inject.Inject;

class Lookup extends AbstractLookup {
    @Inject
    Lookup(final ReferenceData referenceData,
           final StreamHolder streamHolder,
           final RefDataValueProxyConsumer.Factory consumerFactory) {
        super(referenceData, streamHolder, consumerFactory);
    }

    @Override
    protected Sequence doLookup(final XPathContext context,
                                final String map,
                                final String key,
                                final long eventTime,
                                final boolean ignoreWarnings,
                                final boolean trace,
                                final LookupIdentifier lookupIdentifier) throws XPathException {
        final ReferenceDataResult result = getReferenceData(map, key, eventTime, lookupIdentifier);

        final RefDataValueProxy refDataValueProxy = result.getRefDataValueProxy();

        final SequenceMaker sequenceMaker = new SequenceMaker(context, getConsumerFactory());
//                EventListProxyConsumerFactory.getConsumerSupplier(eventListProxy));
//        final EventListProxyConsumer eventListConsumer = EventListProxyConsumerFactory.getConsumer(
//                eventListProxy,
//                context);
//
//        final Sequence sequence = eventListConsumer.map(eventListProxy);

        if (refDataValueProxy != null) {
            sequenceMaker.open();

            // TODO need to change the ReferenceDataResult to hold the value proxy
            // then here we can pass our consume method (changed to accept a ByteBuffer (or maybe InputStream))
            // to the OffHeapPool to consume inside a txn
            sequenceMaker.consume(refDataValueProxy);

            sequenceMaker.close();

            if (trace) {
                outputInfo(Severity.INFO, "Lookup success ", lookupIdentifier, trace, result, context);
            }
        } else if (!ignoreWarnings) {
            outputInfo(Severity.WARNING, "Lookup failed ", lookupIdentifier, trace, result, context);
        }

        return sequenceMaker.toSequence();
    }
}
