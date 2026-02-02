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


@XsltFunctionDef(
        name = BitmapLookup.FUNCTION_NAME,
        commonCategory = XsltFunctionCategory.PIPELINE,
        commonDescription = """
                The bitmap-lookup() function looks up a bitmap key from reference or context data a value \
                (which can be an XML node set) for each set bit position and adds it to the resultant XML.

                If the look up fails no result will be returned.

                The key is a bitmap expressed as either a decimal integer or a hexidecimal value, \
                e.g. `14`/`0xE` is `1110` as a binary bitmap.
                For each bit position that is set, (i.e. has a binary value of `1`) a lookup will be performed \
                using that bit position as the key.
                In this example, positions `1`, `2` & `3` are set so a lookup would be performed for these \
                bit positions.
                The result of each lookup for the bitmap are concatenated together in bit position order, \
                separated by a space.

                If `ignoreWarnings` is true then any lookup failures will be ignored and it will return the \
                value(s) for the bit positions it was able to lookup.

                This function can be useful when you have a set of values that can be represented as a bitmap \
                and you need them to be converted back to individual values.
                For example if you have a set of additive account permissions (e.g Admin, ManageUsers, \
                PerformExport, etc.), each of which is associated with a bit position, then a user's \
                permissions could be defined as a single decimal/hex bitmap value.
                Thus a bitmap lookup with this value would return all the permissions held by the user.

                For example the reference data store may contain:

                | Key (Bit position) | Value          |
                |--------------------|----------------|
                | 0                  | Administrator  |
                | 1                  | Manage_Users   |
                | 2                  | Perform_Export |
                | 3                  | View_Data      |
                | 4                  | Manage_Jobs    |
                | 5                  | Delete_Data    |
                | 6                  | Manage_Volumes |

                The following are example lookups using the above reference data:

                | Lookup Key (decimal) | Lookup Key (Hex) | Bitmap    | Result                                  |
                |----------------------|------------------|-----------|-----------------------------------------|
                | `0`                  | `0x0`            | `0000000` | -                                       |
                | `1`                  | `0x1`            | `0000001` | `Administrator`                         |
                | `74`                 | `0x4A`           | `1001010` | `Manage_Users View_Data Manage_Volumes` |
                | `2`                  | `0x2`            | `0000010` | `Manage_Users`                          |
                | `96`                 | `0x60`           | `1100000` | `Delete_Data Manage_Volumes`            |
                """,
        commonReturnType = XsltDataType.SEQUENCE,
        commonReturnDescription = "The hash of the value",
        signatures = {
                @XsltFunctionSignature(
                        args = {
                                @XsltFunctionArg(
                                        name = "map",
                                        description = "The name of the map to perform the lookup in.",
                                        argType = XsltDataType.STRING),
                                @XsltFunctionArg(
                                        name = "key",
                                        description = "The key to lookup in the named map.",
                                        argType = XsltDataType.STRING),
                                @XsltFunctionArg(
                                        name = "time",
                                        description = """
                                                Determines which set of reference data was effective at the \
                                                requested time.
                                                If no reference data exists with an effective time before the \
                                                requested time then the lookup will fail.
                                                Time is in the format `yyyy-MM-dd'T'HH:mm:ss.SSSXX`, e.g. \
                                                `2010-01-01T00:00:00.000Z`.""",
                                        isOptional = true,
                                        argType = XsltDataType.STRING),
                                @XsltFunctionArg(
                                        name = "ignoreWarnings",
                                        description = "If true, any lookup failures will be ignored, else they " +
                                                      "will be reported as warnings.",
                                        argType = XsltDataType.BOOLEAN,
                                        isOptional = true),
                                @XsltFunctionArg(
                                        name = "trace",
                                        description = "If true, additional trace information is output as " +
                                                      "INFO messages.",
                                        argType = XsltDataType.BOOLEAN,
                                        isOptional = true),
                        })
        })
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
