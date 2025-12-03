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

import stroom.docref.DocRef;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.refdata.LookupIdentifier;
import stroom.pipeline.refdata.ReferenceData;
import stroom.pipeline.refdata.ReferenceDataResult;
import stroom.pipeline.refdata.ReferenceDataResult.LazyMessage;
import stroom.pipeline.refdata.store.GenericRefDataValueProxyConsumer;
import stroom.pipeline.refdata.store.RefDataValueProxy;
import stroom.pipeline.refdata.store.RefDataValueProxyConsumerFactory;
import stroom.pipeline.refdata.store.RefDataValueProxyConsumerFactory.Factory;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.pipeline.state.MetaHolder;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskTerminatedException;
import stroom.util.date.DateUtil;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;
import stroom.util.shared.StoredError;
import stroom.util.shared.StringUtil;

import jakarta.inject.Inject;
import net.sf.saxon.Configuration;
import net.sf.saxon.event.Builder;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.TinyBuilder;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;


abstract class AbstractLookup extends StroomExtensionFunctionCall {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractLookup.class);

    private final ReferenceData referenceData;
    private final MetaHolder metaHolder;
    private final SequenceMakerFactory sequenceMakerFactory;
    private final TaskContextFactory taskContextFactory;

    private long defaultMs = -1;

    AbstractLookup(final ReferenceData referenceData,
                   final MetaHolder metaHolder,
                   final SequenceMakerFactory sequenceMakerFactory,
                   final TaskContextFactory taskContextFactory) {
        this.referenceData = referenceData;
        this.metaHolder = metaHolder;
        this.sequenceMakerFactory = sequenceMakerFactory;
        this.taskContextFactory = taskContextFactory;
    }

    protected SequenceMaker createSequenceMaker(final XPathContext context) {
        return sequenceMakerFactory.create(context);
    }

    @Override
    protected Sequence call(final String functionName, final XPathContext context, final Sequence[] arguments) {
        LOGGER.trace("call({}, {}, {}", functionName, context, arguments);
        Sequence result = EmptyAtomicSequence.getInstance();

        if (taskContextFactory.current().isTerminated() || Thread.currentThread().isInterrupted()) {
            LOGGER.debug(LogUtil.message("call({}, {}, {}), isTerminated: {}, isInterrupted: {}",
                    functionName,
                    context,
                    arguments,
                    taskContextFactory.current().isTerminated(),
                    Thread.currentThread().isInterrupted()));
            throw ProcessException.wrap(new TaskTerminatedException());
        }

        try {
            // Setup the defaultMs to be the received time of the stream.
            if (defaultMs == -1) {
                if (metaHolder.getMeta() != null) {
                    defaultMs = metaHolder.getMeta().getCreateMs();
                } else {
                    defaultMs = System.currentTimeMillis();
                }
            }

            // Get the map and key names.
            final String map = getSafeString(functionName, context, arguments, 0);
            final String key = getSafeString(functionName, context, arguments, 1);

            // Find out if we are going to ignore warnings.
            final boolean ignoreWarnings = arguments.length > 3
                                           && NullSafe.isTrue(getSafeBoolean(functionName, context, arguments, 3));
            // Find out if we are going to trace the lookup.
            final boolean traceLookup = arguments.length > 4
                                        && NullSafe.isTrue(getSafeBoolean(functionName, context, arguments, 4));

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
            final LookupIdentifier lookupIdentifier;
            try {
                lookupIdentifier = new LookupIdentifier(map, key, ms);

                if (NullSafe.hasItems(getPipelineReferences())) {
                    // If we have got the date then continue to do the lookup.
                    try {
                        if (LOGGER.isDebugEnabled()) {
                            final DurationTimer timer = DurationTimer.start();
                            result = doLookup(context, ignoreWarnings, traceLookup, lookupIdentifier);
                            LOGGER.debug("doLookup for {}, in {}", lookupIdentifier, timer);
                        } else {
                            LOGGER.debug("doLookup for {}", lookupIdentifier);
                            result = doLookup(context, ignoreWarnings, traceLookup, lookupIdentifier);
                        }
                    } catch (final RuntimeException e) {
                        // Don't want termination (which is a normal thing to happen
                        // to log an error
                        if (ProcessException.isTerminated(e)) {
                            LOGGER.debug(() -> "Terminated: " + LogUtil.exceptionMessage(e));
                        } else {
                            createLookupFailError(context, lookupIdentifier, e);
                        }
                    }
                } else {
                    outputInfo(Severity.ERROR,
                            () -> "No reference loaders have been added to this XSLT step to perform a lookup",
                            lookupIdentifier,
                            traceLookup,
                            ignoreWarnings,
                            null,
                            context);
                }
            } catch (final RuntimeException e) {
                final StringBuilder sb = new StringBuilder();
                sb.append("Identifier must have a map and a key (map: ");
                sb.append(map);
                sb.append(", key: ");
                sb.append(key);
                sb.append(", lookup time: ");
                sb.append(ms);
                log(context, Severity.ERROR, e.getMessage(), e);
            }
        } catch (final XPathException | RuntimeException e) {
            log(context, Severity.ERROR, e.getMessage(), e);
        }

        return result;
    }

    abstract Sequence doLookup(final XPathContext context,
                               final boolean ignoreWarnings,
                               final boolean trace,
                               final LookupIdentifier lookupIdentifier)
            throws XPathException;


    ReferenceDataResult getReferenceData(final LookupIdentifier lookupIdentifier,
                                         final boolean isTraceEnabled,
                                         final boolean isIgnoreWarnings) {
        LOGGER.trace("getReferenceData({})", lookupIdentifier);
        ReferenceDataResult result = new ReferenceDataResult(
                lookupIdentifier, isTraceEnabled, isIgnoreWarnings);

        result.logLazyTemplate(
                Severity.INFO,
                "Lookup - " +
                "key: '{}', map: '{}', lookup time: {} (primary map: '{}', secondary map: '{}', " +
                "nested lookup: {})",
                () -> Arrays.asList(lookupIdentifier.getKey(),
                        lookupIdentifier.getMap(),
                        Instant.ofEpochMilli(lookupIdentifier.getEventTime()),
                        lookupIdentifier.getPrimaryMapName(),
                        Objects.requireNonNullElse(lookupIdentifier.getSecondaryMapName(), ""),
                        lookupIdentifier.isMapNested()));

        final List<PipelineReference> pipelineReferences = getPipelineReferences();
        if (NullSafe.isEmptyCollection(pipelineReferences)) {
            result.logSimpleTemplate(
                    Severity.WARNING,
                    "No pipeline references have been added to this XSLT step to perform a lookup");
        } else {
            result = referenceData.ensureReferenceDataAvailability(pipelineReferences, lookupIdentifier, result);
        }
        return result;
    }

    private void createLookupFailError(final XPathContext context,
                                       final LookupIdentifier lookupIdentifier,
                                       final Throwable e) {
        // Create the message.
        final StringBuilder sb = new StringBuilder();
        sb.append("Error performing lookup ");
        lookupIdentifier.appendTo(sb);

        outputError(context, sb, e);
    }

    private void createLookupFailWarning(final XPathContext context,
                                         final LookupIdentifier lookupIdentifier,
                                         final Throwable e) {
        // Create the message.
        final StringBuilder sb = new StringBuilder();
        sb.append("Lookup failed ");
        lookupIdentifier.appendTo(sb);

        outputWarning(context, sb, e);
    }

    void outputInfo(final Severity severity,
                    final Supplier<String> msgSupplier,
                    final LookupIdentifier lookupIdentifier,
                    final boolean trace,
                    final boolean ignoreWarnings,
                    final ReferenceDataResult result,
                    final XPathContext context) {

        final TaskContext taskContext = taskContextFactory.current();
        if (shouldLog(taskContext, severity, trace, ignoreWarnings)) {

            final String msg = msgSupplier.get();
            final StringBuilder sb = new StringBuilder();
            if (msg != null && !msg.isBlank()) {
                sb.append(msg);
                if (!msg.endsWith(" ")) {
                    sb.append(" ");
                }
            }
            lookupIdentifier.appendTo(sb);

            // Log the stream we found it in, useful if a map is defined in >1 feeds

            if (result != null) {
                result.getRefDataValueProxy()
                        .flatMap(RefDataValueProxy::getSuccessfulMapDefinition)
                        .ifPresent(mapDefinition -> {
                            sb.append(" found in stream: ")
                                    .append(mapDefinition.getRefStreamDefinition().getStreamId());
                        });

                result.getMessages()
                        .stream()
                        .filter(lazyMessage ->
                                shouldLog(taskContext, lazyMessage.getSeverity(), trace, ignoreWarnings))
                        .forEach(lazyMessage -> {
                            sb.append("\n");
                            sb.append(StoredError.MESSAGE_CAUSE_DELIMITER);
                            sb.append(lazyMessage.getSeverity().getDisplayValue());
                            sb.append(": ");
                            sb.append(lazyMessage.getMessage());
                        });
            }

            final String message = sb.toString();
            LOGGER.debug(message);
            log(context, severity, message, null);
        } else {
            LOGGER.debug(() -> LogUtil.message(
                    "Ignoring log message, severity: {}, trace: {}, ignoreWarnings: {}, msg: {}",
                    severity, trace, ignoreWarnings, msgSupplier.get()));
        }
    }

    String getQualifiedEffectiveStreamIds(final ReferenceDataResult result) {
        Objects.requireNonNull(result);
        return result.getEffectiveStreams()
                .stream()
                .map(entry ->
                        NullSafe.get(entry.getKey(), PipelineReference::getFeed, DocRef::getName)
                        + ":"
                        + NullSafe.get(entry.getValue(), RefStreamDefinition::getStreamId))
                .collect(Collectors.joining(", "));
    }

    String getQualifyingStreamIds(final ReferenceDataResult result) {
        Objects.requireNonNull(result);
        return result.getQualifyingStreams()
                .stream()
                .map(RefStreamDefinition::getStreamId)
                .map(String::valueOf)
                .collect(Collectors.joining(", "));
    }

    void logMapLocations(final ReferenceDataResult result,
                         final RefDataValueProxy refDataValueProxy) {
        result.logLazyTemplate(Severity.INFO,
                "Executing lookup of key: '{}' in map '{}' in {} out of {} effective stream{}: [{}]",
                () -> {
                    final String streamsStr = getQualifyingStreamIds(result);
                    final int qualifyingStreamCount = result.getQualifyingStreams().size();
                    final int effectiveStreamCount = result.getEffectiveStreams().size();
                    final String pluralSuffix = StringUtil.pluralSuffix(effectiveStreamCount);

                    return Arrays.asList(
                            refDataValueProxy.getKey(),
                            refDataValueProxy.getMapName(),
                            qualifyingStreamCount,
                            effectiveStreamCount,
                            pluralSuffix,
                            streamsStr);
                });
    }

    /**
     * Log a lookup failing to return a value.  Whether it actually logs depends on things like
     * whether there were any errors, state of ignoreWarnings, state of trace, etc.
     */
    void logFailureReason(final ReferenceDataResult result,
                          final XPathContext context,
                          final boolean ignoreWarnings,
                          final boolean trace) {

        final List<PipelineReference> pipelineReferences = getPipelineReferences();
        final Severity maxSeverity = getMaxSeverity(result);

        if (pipelineReferences.isEmpty()) {
            outputInfo(
                    maxSeverity.atLeast(Severity.ERROR),
                    () -> "No reference loaders are configured so the lookup cannot be performed. ",
                    result.getCurrentLookupIdentifier(),
                    trace,
                    ignoreWarnings,
                    result,
                    context);

        } else if (!ignoreWarnings && result.getEffectiveStreams().isEmpty()) {
            // No effective streams were found to lookup from
            if (NullSafe.hasItems(pipelineReferences)) {
                final String feeds = pipelineReferences.stream()
                        .map(pipeRef -> NullSafe.get(pipeRef, PipelineReference::getFeed, DocRef::getName))
                        .filter(Objects::nonNull)
                        .map(name -> "'" + name + "'")
                        .collect(Collectors.joining(", "));

                outputInfo(
                        maxSeverity.atLeast(Severity.WARNING),
                        () -> LogUtil.message(
                                "No effective streams found in any of the reference loaders (feeds: [{}]). " +
                                "Do reference data streams exist for the lookup time? ",
                                feeds),
                        result.getCurrentLookupIdentifier(),
                        trace,
                        ignoreWarnings,
                        result,
                        context);
            }
        } else if (!ignoreWarnings && result.getQualifyingStreams().isEmpty()) {
            // None of the effective streams contains the map
            outputInfo(
                    maxSeverity.atLeast(Severity.WARNING),
                    () -> "Map not found in effective streams [" + getQualifiedEffectiveStreamIds(result) + "] ",
                    result.getCurrentLookupIdentifier(),
                    trace,
                    ignoreWarnings,
                    result,
                    context);
        } else if (maxSeverity.greaterThanOrEqual(Severity.ERROR)) {
            outputInfo(
                    maxSeverity.atLeast(Severity.ERROR),
                    () -> "Errors found during lookup ",
                    result.getCurrentLookupIdentifier(),
                    trace,
                    ignoreWarnings,
                    result,
                    context);
        }
    }

    private Severity getMaxSeverity(final ReferenceDataResult result) {
        return Severity.getMaxSeverity(
                NullSafe.list(result.getMessages())
                        .stream()
                        .map(LazyMessage::getSeverity)
                        .collect(Collectors.toList()),
                Severity.INFO);
    }

    // Pkg private for testing
    static boolean shouldLog(final TaskContext taskContext,
                             final Severity maxSeverity,
                             final boolean isTraceEnabled,
                             final boolean isIgnoreWarnings) {

        if (taskContext.isTerminated() || Thread.currentThread().isInterrupted()) {
            // If the task is terminated then lots of lookups will fail but this is expected behaviour
            // so no point logging
            return false;
        } else {
            return isTraceEnabled
                   || maxSeverity.greaterThanOrEqual(Severity.ERROR)
                   || (!isIgnoreWarnings && maxSeverity.greaterThanOrEqual(Severity.WARNING));
        }
    }

    /**
     * Log a lookup that ran without
     */
    void logLookupValue(final boolean wasValueFound,
                        final ReferenceDataResult result,
                        final XPathContext context,
                        final boolean ignoreWarnings,
                        final boolean trace) {

        // Establish the highest severity of all messages we have as we may have found a value for a key
        // but encountered warnings along the way
        final Severity maxSeverity = getMaxSeverity(result);

        if (maxSeverity.greaterThanOrEqual(Severity.WARNING)) {
            final String prefix = wasValueFound
                    ? "Key found "
                    : "Key not found";
            final String suffix = maxSeverity.greaterThanOrEqual(Severity.ERROR)
                    ? "with errors "
                    : "with warnings ";

            outputInfo(
                    maxSeverity,
                    () -> prefix + suffix,
                    result.getCurrentLookupIdentifier(),
                    trace,
                    ignoreWarnings,
                    result,
                    context);
        } else if (wasValueFound) {
            // Found our value but may still have warnings
            outputInfo(
                    Severity.INFO, // Anything higher should have been covered above
                    () -> "Key found ",
                    result.getCurrentLookupIdentifier(),
                    trace,
                    ignoreWarnings,
                    result,
                    context);
        } else {
            // Key not found so log at least a warning
            outputInfo(
                    maxSeverity.atLeast(Severity.WARNING),
                    () -> "Key not found ",
                    result.getCurrentLookupIdentifier(),
                    trace,
                    ignoreWarnings,
                    result,
                    context);
        }
    }

// --------------------------------------------------------------------------------


    /**
     * To aid mocking in testing
     */
    static class SequenceMakerFactory {

        private final RefDataValueProxyConsumerFactory.Factory consumerFactoryFactory;

        @Inject
        SequenceMakerFactory(final Factory consumerFactoryFactory) {
            this.consumerFactoryFactory = consumerFactoryFactory;
        }

        SequenceMaker create(final XPathContext context) {
            return new SequenceMaker(context, consumerFactoryFactory);
        }
    }


// --------------------------------------------------------------------------------


    static class SequenceMaker {

        private final XPathContext context;
        private final RefDataValueProxyConsumerFactory.Factory consumerFactoryFactory;
        private Builder builder;
        private GenericRefDataValueProxyConsumer consumer;

        SequenceMaker(final XPathContext context,
                      final RefDataValueProxyConsumerFactory.Factory consumerFactoryFactory) {
            this.context = context;
            this.consumerFactoryFactory = consumerFactoryFactory;
        }

        void open() throws XPathException {
            LOGGER.trace("open()");
            // Make sure we have made a consumer.
            ensureConsumer();
            consumer.startDocument();
        }

        void close() throws XPathException {
            LOGGER.trace("close()");
            consumer.endDocument();
        }

        boolean consume(final RefDataValueProxy refDataValueProxy) throws XPathException {
            return consumer.consume(refDataValueProxy);
        }

        private void ensureConsumer() {
            LOGGER.trace("ensureConsumer()");
            if (consumer == null) {
                LOGGER.trace("ensureConsumer() - Creating consumer");
                // We have some reference data so build a tiny tree.
                final Configuration configuration = context.getConfiguration();

                final PipelineConfiguration pipelineConfiguration = configuration.makePipelineConfiguration();

                builder = new TinyBuilder(pipelineConfiguration);

                // At this point we don't know if we are dealing with heap object values or off-heap bytebuffer values.
                // We also don't know if the value is a string or a fastinfoset.
                consumer = new GenericRefDataValueProxyConsumer(
                        builder,
                        pipelineConfiguration,
                        consumerFactoryFactory.create(builder, pipelineConfiguration));
            }
        }

        Sequence toSequence() {
            LOGGER.trace("toSequence()");
            if (builder == null) {
                return EmptyAtomicSequence.getInstance();
            }

            final Sequence sequence = builder.getCurrentRoot();

            // Reset the builder, detaching it from the constructed document.
            builder.reset();

            return sequence;
        }
    }
}
