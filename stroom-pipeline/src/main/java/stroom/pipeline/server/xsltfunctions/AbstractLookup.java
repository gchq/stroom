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

package stroom.pipeline.server.xsltfunctions;

import java.util.List;

import javax.annotation.Resource;

import stroom.pipeline.shared.data.PipelineReference;
import stroom.pipeline.state.StreamHolder;
import stroom.refdata.ReferenceData;
import stroom.util.date.DateUtil;
import stroom.xml.event.EventList;
import stroom.xml.event.np.EventListConsumer;
import stroom.xml.event.np.NPEventList;
import net.sf.saxon.Configuration;
import net.sf.saxon.event.Builder;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.TinyBuilder;

public abstract class AbstractLookup extends StroomExtensionFunctionCall {
    public static class SequenceMaker {
        private final XPathContext context;
        private Builder builder;
        private EventListConsumer consumer;

        public SequenceMaker(final XPathContext context) {
            this.context = context;
        }

        public void open() throws XPathException {
            // Make sure we have made a consumer.
            ensureConsumer();

            // TODO : Possibly replace NPEventList with TinyTree to improve
            // performance.
            consumer.startDocument();
        }

        public void close() throws XPathException {
            consumer.endDocument();
        }

        public void consume(final NPEventList eventList) throws XPathException {
            // TODO : Possibly replace NPEventList with TinyTree to improve
            // performance.
            consumer.consume(eventList);
        }

        private void ensureConsumer() {
            if (consumer == null) {
                // We have some reference data so build a tiny tree.
                final Configuration configuration = context.getConfiguration();

                final PipelineConfiguration pipe = configuration.makePipelineConfiguration();

                builder = new TinyBuilder(pipe);
                consumer = new EventListConsumer(builder, pipe);
            }
        }

        public Sequence toSequence() {
            if (builder == null) {
                return EmptyAtomicSequence.getInstance();
            }

            final Sequence sequence = builder.getCurrentRoot();

            // Reset the builder, detaching it from the constructed document.
            builder.reset();

            return sequence;
        }
    }

    private static final StroomLogger LOGGER = StroomLogger.getLogger(AbstractLookup.class);

    @Resource
    private ReferenceData referenceData;
    @Resource
    private StreamHolder streamHolder;

    private long defaultMs = -1;

    @Override
    protected Sequence call(final String functionName, final XPathContext context, final Sequence[] arguments)
            throws XPathException {
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

        // Make sure we can get the date ok.
        long ms = defaultMs;
        if (arguments.length > 2) {
            final String time = getSafeString(functionName, context, arguments, 2);
            try {
                ms = DateUtil.parseNormalDateTimeString(time);
            } catch (final Throwable e) {
                if (!ignoreWarnings) {
                    final StringBuilder sb = new StringBuilder("Lookup failed to parse date: " + time);
                    outputWarning(context, sb, e);
                }

                return EmptyAtomicSequence.getInstance();
            }
        }

        // Create a lookup identifier if we are going to output debug.
        StringBuilder lookupIdentifier = null;
        if (LOGGER.isDebugEnabled()) {
            lookupIdentifier = new StringBuilder();
            getLookupIdentifier(lookupIdentifier, map, key, ms);
        }

        // If we have got the date then continue to do the lookup.
        Sequence sequence = EmptyAtomicSequence.getInstance();
        try {
            sequence = doLookup(context, map, key, ms, ignoreWarnings, lookupIdentifier);
        } catch (final Throwable t) {
            if (!ignoreWarnings) {
                createLookupFailWarning(context, map, key, ms, t);
            }
        }

        return sequence;
    }

    protected abstract Sequence doLookup(final XPathContext context, final String map, final String key,
            final long eventTime, final boolean ignoreWarnings, final StringBuilder lookupIdentifier)
                    throws XPathException;

    EventList getReferenceData(final String map, final String key, final long eventTime,
            final StringBuilder lookupIdentifier) {
        EventList result = null;

        final List<PipelineReference> pipelineReferences = getPipelineReferences();
        if (key != null && pipelineReferences != null && pipelineReferences.size() > 0) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Doing lookup " + lookupIdentifier);
            }
            result = referenceData.getValue(pipelineReferences, getErrorReceiver(), eventTime, map, key);
            if (LOGGER.isDebugEnabled()) {
                if (result != null) {
                    LOGGER.debug("Found lookup " + lookupIdentifier);
                } else {
                    LOGGER.debug("Lookup not found " + lookupIdentifier);
                }
            }
        }

        return result;
    }

    void createLookupFailWarning(final XPathContext context, final String map, final String key, final long eventTime,
            final Throwable e) {
        // Create the message.
        final StringBuilder sb = new StringBuilder();
        sb.append("Lookup failed ");
        getLookupIdentifier(sb, map, key, eventTime);

        outputWarning(context, sb, e);
    }

    private void getLookupIdentifier(final StringBuilder sb, final String map, final String key, final long eventTime) {
        sb.append("(map = ");
        sb.append(map);
        sb.append(", key = ");
        sb.append(key);
        sb.append(", eventTime = ");
        sb.append(DateUtil.createNormalDateTimeString(eventTime));
        sb.append(")");
    }
}
