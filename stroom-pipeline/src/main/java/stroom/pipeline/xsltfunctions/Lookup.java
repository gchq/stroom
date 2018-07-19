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
import stroom.refdata.LookupIdentifier;
import stroom.refdata.ReferenceData;
import stroom.refdata.ReferenceDataResult;
import stroom.refdata.offheapstore.RefDataStoreProvider;
import stroom.refdata.offheapstore.RefDataValueProxy;
import stroom.refdata.offheapstore.RefDataValueProxyConsumer;
import stroom.util.shared.Severity;

import javax.inject.Inject;

class Lookup extends AbstractLookup {

    @Inject
    Lookup(final ReferenceData referenceData,
           final RefDataStoreProvider refDataStoreProvider,
           final StreamHolder streamHolder,
           final RefDataValueProxyConsumer.Factory consumerFactory) {
        super(referenceData, refDataStoreProvider.get(), streamHolder, consumerFactory);
    }

    @Override
    protected Sequence doLookup(final XPathContext context,
                                final boolean ignoreWarnings,
                                final boolean trace,
                                final LookupIdentifier lookupIdentifier) throws XPathException {
        // TODO rather than putting the proxy in the result we could just put the refStreamDefinition
        // in there and then do the actual lookup in the sequenceMaker by passing an injected RefDataStore
        // into it.
        final ReferenceDataResult result = getReferenceData(lookupIdentifier);

        final RefDataValueProxy refDataValueProxy = result.getRefDataValueProxy();

        final SequenceMaker sequenceMaker = new SequenceMaker(context, getRefDataStore(), getConsumerFactory());

        boolean wasFound = false;
        try {
            if (refDataValueProxy != null) {
                sequenceMaker.open();

                wasFound = sequenceMaker.consume(refDataValueProxy);

                sequenceMaker.close();

                if (wasFound && trace) {
                    outputInfo(Severity.INFO, "Lookup success ", lookupIdentifier, trace, result, context);
                }
            }
        } catch (XPathException e) {
            outputInfo(Severity.ERROR, "Lookup errored: " + e.getMessage(), lookupIdentifier, trace, result, context);
        }

        if (!wasFound && !ignoreWarnings) {
            outputInfo(Severity.WARNING, "Lookup failed ", lookupIdentifier, trace, result, context);
        }

        return sequenceMaker.toSequence();
    }
}
