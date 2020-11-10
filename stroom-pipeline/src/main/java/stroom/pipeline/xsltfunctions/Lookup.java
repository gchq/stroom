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

import stroom.pipeline.refdata.LookupIdentifier;
import stroom.pipeline.refdata.ReferenceData;
import stroom.pipeline.refdata.ReferenceDataResult;
import stroom.pipeline.refdata.store.RefDataValueProxyConsumerFactory;
import stroom.pipeline.state.MetaHolder;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Severity;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;

import javax.inject.Inject;
import java.time.Instant;

class Lookup extends AbstractLookup {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(Lookup.class);

    @Inject
    Lookup(final ReferenceData referenceData,
           final MetaHolder metaHolder,
           final RefDataValueProxyConsumerFactory.Factory consumerFactoryFactory) {
        super(referenceData, metaHolder, consumerFactoryFactory);
    }

    @Override
    protected Sequence doLookup(final XPathContext context,
                                final boolean ignoreWarnings,
                                final boolean trace,
                                final LookupIdentifier lookupIdentifier) throws XPathException {

        LOGGER.debug(() -> LogUtil.message("Looking up {}, {}",
                lookupIdentifier, Instant.ofEpochMilli(lookupIdentifier.getEventTime())));

        // TODO rather than putting the proxy in the result we could just put the refStreamDefinition
        // in there and then do the actual lookup in the sequenceMaker by passing an injected RefDataStore
        // into it.
        final ReferenceDataResult result = getReferenceData(lookupIdentifier);

        final SequenceMaker sequenceMaker = new SequenceMaker(context, getRefDataValueProxyConsumerFactoryFactory());

        // Output any warnings/errors found so far
        if (!ignoreWarnings) {
            if (result.getMessages() != null) {
                result.getMessages()
                        .stream()
                        .filter(lazyMessage -> lazyMessage.getSeverity().greaterThanOrEqual(Severity.WARNING))
                        .forEach(lazyMessage -> {
                            log(context, lazyMessage.getSeverity(), lazyMessage.getMessage().get(), null);
                        });
            }
        }

        boolean wasFound = false;
        try {
            if (result.getRefDataValueProxy().isPresent()) {
                sequenceMaker.open();

                wasFound = sequenceMaker.consume(result.getRefDataValueProxy().get());

                sequenceMaker.close();

                if (wasFound && trace) {
                    outputInfo(Severity.INFO, "Success ", lookupIdentifier, trace, result, context);
                } else if (!wasFound && !ignoreWarnings) {
                    outputInfo(Severity.WARNING, "Key not found ", lookupIdentifier, trace, result, context);
                }
            } else if (!ignoreWarnings && !result.getEffectiveStreams().isEmpty()) {
                // We have effective streams so if there is no proxy present then the map was not found
                outputInfo(
                        Severity.WARNING,
                        "Map not found in streams [" + getEffectiveStreamIds(result) + "] ",
                        lookupIdentifier,
                        trace,
                        result,
                        context);
            }
        } catch (XPathException e) {
            outputInfo(Severity.ERROR, "Lookup errored: " + e.getMessage(), lookupIdentifier, trace, result, context);
        }

        return sequenceMaker.toSequence();
    }
}
