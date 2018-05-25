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

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Builder;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.TinyBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.pipeline.state.StreamHolder;
import stroom.refdata.ReferenceData;
import stroom.refdata.ReferenceDataResult;
import stroom.refdata.offheapstore.RefDataValueProxy;
import stroom.refdata.offheapstore.RefDataValueProxyConsumer;
import stroom.util.date.DateUtil;
import stroom.util.shared.Severity;

import java.util.List;

abstract class AbstractLookup extends StroomExtensionFunctionCall {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractLookup.class);

    private final ReferenceData referenceData;
    private final StreamHolder streamHolder;
    private final RefDataValueProxyConsumer.Factory consumerFactory;

    private long defaultMs = -1;

    AbstractLookup(final ReferenceData referenceData,
                   final StreamHolder streamHolder,
                   final RefDataValueProxyConsumer.Factory consumerFactory) {
        this.referenceData = referenceData;
        this.streamHolder = streamHolder;
        this.consumerFactory = consumerFactory;
    }

    RefDataValueProxyConsumer.Factory getConsumerFactory() {
        return consumerFactory;
    }

    @Override
    protected Sequence call(final String functionName, final XPathContext context, final Sequence[] arguments) {
        Sequence result = EmptyAtomicSequence.getInstance();

        try {
            // Setup the defaultMs to be the received time of the stream.
            if (defaultMs == -1) {
                if (streamHolder.getStream() != null) {
                    defaultMs = streamHolder.getStream().getCreateMs();
                } else {
                    defaultMs = System.currentTimeMillis();
                }
            }

            // Get the map and key names.
            final String map = getSafeString(functionName, context, arguments, 0);
            final String key = getSafeString(functionName, context, arguments, 1);

            // Find out if we are going to ignore warnings.
            boolean ignoreWarnings = false;
            if (arguments.length > 3) {
                final Boolean ignore = getSafeBoolean(functionName, context, arguments, 3);
                if (ignore != null) {
                    ignoreWarnings = ignore;
                }
            }

            // Find out if we are going to trace the lookup.
            boolean traceLookup = false;
            if (arguments.length > 4) {
                final Boolean trace = getSafeBoolean(functionName, context, arguments, 4);
                if (trace != null) {
                    traceLookup = trace;
                }
            }

            // Make sure we can get the date ok.
            long ms = defaultMs;
            if (arguments.length > 2) {
                final String time = getSafeString(functionName, context, arguments, 2);
                try {
                    ms = DateUtil.parseNormalDateTimeString(time);
                } catch (final RuntimeException e) {
                    if (!ignoreWarnings) {
                        if (time == null) {
                            final StringBuilder sb = new StringBuilder();
                            sb.append("Lookup failed to parse empty date");
                            outputWarning(context, sb, null);
                        } else {
                            final StringBuilder sb = new StringBuilder();
                            sb.append("Lookup failed to parse date: ");
                            sb.append(time);
                            outputWarning(context, sb, e);
                        }
                    }

                    return result;
                }
            }

            // Create a lookup identifier if we are going to output debug.
            final LookupIdentifier lookupIdentifier = new LookupIdentifier(map, key, ms);

            // If we have got the date then continue to do the lookup.
            try {
                result = doLookup(context, map, key, ms, ignoreWarnings, traceLookup, lookupIdentifier);
            } catch (final RuntimeException e) {
                if (!ignoreWarnings) {
                    createLookupFailWarning(context, lookupIdentifier, e);
                }
            }
        } catch (final XPathException | RuntimeException e) {
            log(context, Severity.ERROR, e.getMessage(), e);
        }

        return result;
    }

    abstract Sequence doLookup(final XPathContext context,
                               final String map,
                               final String key,
                               final long eventTime,
                               final boolean ignoreWarnings,
                               final boolean trace,
                               final LookupIdentifier lookupIdentifier)
            throws XPathException;

    ReferenceDataResult getReferenceData(final String map,
                                         final String key,
                                         final long eventTime,
                                         final LookupIdentifier lookupIdentifier) {
        final ReferenceDataResult result = new ReferenceDataResult();

        result.log(Severity.INFO, () -> "Doing lookup " + lookupIdentifier);
        if (map == null) {
            result.log(Severity.ERROR, () -> "No map name has been specified");
        } else if (key == null) {
            result.log(Severity.ERROR, () -> "No key name has been specified");
        } else {
            final List<PipelineReference> pipelineReferences = getPipelineReferences();
            if (pipelineReferences == null || pipelineReferences.size() == 0) {
                result.log(Severity.ERROR, () -> "No pipeline references have been added to this XSLT step to perform a lookup");
            } else {
                referenceData.getValue(pipelineReferences, eventTime, map, key, result);
            }
        }

        return result;
    }

    private void createLookupFailWarning(final XPathContext context,
                                         final LookupIdentifier lookupIdentifier,
                                         final Throwable e) {
        // Create the message.
        final StringBuilder sb = new StringBuilder();
        sb.append("Lookup failed ");
        lookupIdentifier.append(sb);

        outputWarning(context, sb, e);
    }

    void outputInfo(final Severity severity,
                    final String msg,
                    final LookupIdentifier lookupIdentifier,
                    final boolean trace,
                    final ReferenceDataResult result,
                    final XPathContext context) {
        final StringBuilder sb = new StringBuilder();
        sb.append(msg);
        lookupIdentifier.append(sb);

        if (trace) {
            result.getMessages().forEach(message -> {
                sb.append("\n > ");
                sb.append(message.getSeverity().getDisplayValue());
                sb.append(": ");
                sb.append(message.getMessage().get());
            });
        }

        final String message = sb.toString();
        LOGGER.debug(message);
        log(context, severity, message, null);
    }

    static class SequenceMaker {
        private final XPathContext context;
        private final RefDataValueProxyConsumer.Factory consumerFactory;
        private Builder builder;
        private RefDataValueProxyConsumer consumer;

        SequenceMaker(final XPathContext context, final RefDataValueProxyConsumer.Factory consumerFactory) {
            this.context = context;
            this.consumerFactory = consumerFactory;
        }

        void open() throws XPathException {
            // Make sure we have made a consumer.
            ensureConsumer();

            // TODO : Possibly replace NPEventList with TinyTree to improve performance.

            consumer.startDocument();
        }

        void close() throws XPathException {
            consumer.endDocument();
        }

        void consume(final RefDataValueProxy refDataValueProxy) throws XPathException {
            // TODO : Possibly replace NPEventList with TinyTree to improve performance.
            consumer.consume(refDataValueProxy);
        }

        private void ensureConsumer() {
            if (consumer == null) {
                // We have some reference data so build a tiny tree.
                final Configuration configuration = context.getConfiguration();

                final PipelineConfiguration pipelineConfiguration = configuration.makePipelineConfiguration();

                builder = new TinyBuilder(pipelineConfiguration);
                consumer = consumerFactory.create(builder, pipelineConfiguration);
            }
        }

        Sequence toSequence() {
            if (builder == null) {
                return EmptyAtomicSequence.getInstance();
            }

            final Sequence sequence = builder.getCurrentRoot();

            // Reset the builder, detaching it from the constructed document.
            builder.reset();

            return sequence;
        }
    }

    static class LookupIdentifier {
        private final String map;
        private final String key;
        private final long eventTime;

        LookupIdentifier(final String map, final String key, final long eventTime) {
            this.map = map;
            this.key = key;
            this.eventTime = eventTime;
        }

        public void append(final StringBuilder sb) {
            sb.append("(map = ");
            sb.append(map);
            sb.append(", key = ");
            sb.append(key);
            sb.append(", eventTime = ");
            sb.append(DateUtil.createNormalDateTimeString(eventTime));
            sb.append(")");
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            append(sb);
            return sb.toString();
        }
    }
}
