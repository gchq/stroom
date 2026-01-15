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

package stroom.pipeline.xsltfunctions;

import stroom.pipeline.refdata.LookupIdentifier;
import stroom.pipeline.refdata.ReferenceData;
import stroom.pipeline.refdata.ReferenceDataResult;
import stroom.pipeline.refdata.store.RefDataValueProxy;
import stroom.pipeline.state.MetaHolder;
import stroom.task.api.TaskContextFactory;
import stroom.util.date.DateUtil;
import stroom.util.exception.ThrowingFunction;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

class BitmapLookup extends AbstractLookup {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(BitmapLookup.class);

    public static final String FUNCTION_NAME = "bitmap-lookup";

    @Inject
    BitmapLookup(final ReferenceData referenceData,
                 final MetaHolder metaHolder,
                 final SequenceMakerFactory sequenceMakerFactory,
                 final TaskContextFactory taskContextFactory) {
        super(referenceData, metaHolder, sequenceMakerFactory, taskContextFactory);
    }

    private SequenceMaker getOrCreateSequenceMaker(final AtomicReference<SequenceMaker> sequenceMakerRef,
                                                   final XPathContext xPathContext) throws XPathException {
        Objects.requireNonNull(sequenceMakerRef);
        Objects.requireNonNull(xPathContext);
        if (sequenceMakerRef.get() == null) {
            final SequenceMaker sequenceMaker = createSequenceMaker(xPathContext);
            sequenceMaker.open();
            sequenceMakerRef.set(sequenceMaker);
        }
        return sequenceMakerRef.get();
    }

    private Sequence generateSequence(final AtomicReference<SequenceMaker> sequenceMakerRef,
                                      final XPathContext xPathContext) {
        Objects.requireNonNull(sequenceMakerRef);
        Objects.requireNonNull(xPathContext);

        return Optional.ofNullable(sequenceMakerRef.get())
                .map(ThrowingFunction.unchecked(sequenceMaker -> {
                    sequenceMaker.close();
                    return sequenceMaker.toSequence();
                }))
                .orElseGet(EmptyAtomicSequence::getInstance);
    }

    @Override
    protected Sequence doLookup(final XPathContext xPathContext,
                                final boolean ignoreWarnings,
                                final boolean trace,
                                final LookupIdentifier lookupIdentifier) {

        try {
            final int[] bits = convertToBits(lookupIdentifier);
            final AtomicReference<SequenceMaker> sequenceMakerRef = new AtomicReference<>(null);

            if (bits.length > 0) {
                final List<String> failedKeys = new ArrayList<>();
                // Now treat each bit position as a key and perform a lookup for each.
                for (final int bit : bits) {
                    lookupBit(xPathContext,
                            ignoreWarnings,
                            trace,
                            lookupIdentifier,
                            sequenceMakerRef,
                            failedKeys,
                            bit);
                }

                if (!failedKeys.isEmpty()) {
                    // Create the message.
                    final StringBuilder sb = new StringBuilder();
                    sb.append("Lookup failed ");
                    sb.append("(map = ");
                    sb.append(lookupIdentifier.getPrimaryMapName());
                    sb.append(", keys = {");
                    sb.append(String.join(",", failedKeys.toString()));
                    sb.append("}, eventTime = ");
                    sb.append(DateUtil.createNormalDateTimeString(lookupIdentifier.getEventTime()));
                    sb.append(")");
                    outputWarning(xPathContext, sb, null);
                }
            }

            return generateSequence(sequenceMakerRef, xPathContext);
        } catch (final Exception e) {
            log(xPathContext,
                    Severity.ERROR,
                    "Error during lookup: " + e.getMessage(),
                    e);
            return EmptyAtomicSequence.getInstance();
        }
    }

    private void lookupBit(final XPathContext xPathContext,
                           final boolean ignoreWarnings,
                           final boolean trace,
                           final LookupIdentifier lookupIdentifier,
                           final AtomicReference<SequenceMaker> sequenceMakerRef,
                           final List<String> failedKeys,
                           final int bit) {
        final String key = String.valueOf(bit);
        LOGGER.trace("Looking up bit '{}', key '{}'", bit, key);
        final LookupIdentifier bitIdentifier = lookupIdentifier.cloneWithNewKey(key);
        final ReferenceDataResult result = getReferenceData(bitIdentifier, trace, ignoreWarnings);

        try {
            // Rather than doing individual lookups for each key (bit position) we could pass all the keys
            // (bit positions) to the store and get it to open a cursor on the first key then scan over the
            // keys concatenating the values of the matched keys.  Debatable if this is much quicker given
            // the bitmap could be quite large so there would be a lot of keys to skip over.
            // In fact this would only work if the data was stored in a store that was keyed by integer rather
            // than string as the ordering would be wrong for a string keyed store.
            if (result.getRefDataValueProxy().isPresent()) {
                final SequenceMaker sequenceMaker = getOrCreateSequenceMaker(sequenceMakerRef, xPathContext);

                // When multiple values are consumed they appear to be separated with a ' '.
                // Not really sure why as it is not something we are doing explicitly.  May be something to
                // do with how we call characters() on the TinyBuilder deeper down.
                // receiver.characters(
                // str, RefDataValueProxyConsumer.NULL_LOCATION, ReceiverOptions.WHOLE_TEXT_NODE);

                final RefDataValueProxy refDataValueProxy = result.getRefDataValueProxy().get();

                logMapLocations(result, refDataValueProxy);

                final boolean wasFound = sequenceMaker.consume(refDataValueProxy);

                logLookupValue(wasFound, result, xPathContext, ignoreWarnings, trace);
            } else {
                // No value proxy so log the reason
                logFailureReason(result, xPathContext, ignoreWarnings, trace);
            }
        } catch (final XPathException e) {
            outputInfo(
                    Severity.ERROR,
                    () -> "Error during lookup: " + e.getMessage(),
                    lookupIdentifier,
                    trace,
                    ignoreWarnings,
                    result,
                    xPathContext);
        }
    }

    @NotNull
    private int[] convertToBits(final LookupIdentifier lookupIdentifier) {
        final String key = lookupIdentifier.getKey();
        final int val;
        try {
            if (key.startsWith("0x")) {
                // Hex input
                val = Integer.valueOf(key.substring(2), 16);
            } else {
                // Decimal input
                val = Integer.parseInt(key);
            }
        } catch (final NumberFormatException e) {
            throw new NumberFormatException("unable to parse number '" + key + "'");
        }

        // Convert the (decimal/hex) input value into a bitmap then into an array of the bit positions
        // that are set to 1.
        return Bitmap.getBits(val);
    }
}
