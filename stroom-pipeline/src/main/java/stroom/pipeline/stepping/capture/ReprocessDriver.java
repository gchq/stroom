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

package stroom.pipeline.stepping.capture;

import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.Source;
import stroom.data.store.api.Store;
import stroom.docstore.shared.DocRefUtil;
import stroom.meta.shared.Meta;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.StreamLocationFactory;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.LoggingErrorReceiver;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.factory.Element;
import stroom.pipeline.filter.SAXRecordDetector;
import stroom.pipeline.factory.PipelineDataHolder;
import stroom.pipeline.factory.PipelineDataHolderFactory;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.factory.PipelineFactory.MidPipeline;
import stroom.pipeline.factory.PipelineFactory.MidPipelineScope;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.state.CurrentUserHolder;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.LocationHolder;
import stroom.pipeline.state.MetaDataHolder;
import stroom.pipeline.state.MetaHolder;
import stroom.pipeline.state.PipelineContext;
import stroom.pipeline.state.PipelineHolder;
import stroom.pipeline.stepping.fingerprint.ElementFingerprints;
import stroom.pipeline.stepping.store.CapturedData;
import stroom.pipeline.stepping.store.CapturedElementData;
import stroom.pipeline.stepping.store.RecordScopeState;
import stroom.pipeline.stepping.store.StepDataStore;
import stroom.pipeline.task.StreamMetaDataProvider;
import stroom.pipeline.xml.event.SaxEventReader;
import stroom.pipeline.xsltfunctions.TaskScopeMap;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.task.api.TaskContext;
import stroom.util.shared.ElementId;

import jakarta.inject.Inject;

import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Re-runs an edited element and its downstream from <b>stored upstream output</b> (SAX events) rather than
 * from the raw source, so an edit no longer pays the cost of re-running the pipeline above the edit. This is
 * the "split" half of stepping: the producer of the still-designed async model.
 * <p>
 * It builds a pipeline rooted at the start element via {@link PipelineFactory#createFrom}, then, for every
 * record the source store holds, fires that record's stored input events straight into the mid-pipeline
 * entry - exactly as {@code PersistedXPathFilterMatcher} fires events into a recorder - so the start element
 * and its downstream reprocess it and their new IO is captured into the target store. Upstream elements never
 * run.
 * <p>
 * Shares {@link StreamCaptureDriver}'s pipeline-scoped collaborators (holders, security, error receiver) and
 * is invoked the same way, so it runs in the same working pipeline scope.
 */
public class ReprocessDriver {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReprocessDriver.class);

    private final Store streamStore;
    private final FeedHolder feedHolder;
    private final MetaDataHolder metaDataHolder;
    private final PipelineHolder pipelineHolder;
    private final MetaHolder metaHolder;
    private final LocationHolder locationHolder;
    private final LocationFactoryProxy locationFactory;
    private final CurrentUserHolder currentUserHolder;
    private final SteppingController controller;
    private final PipelineStore pipelineStore;
    private final PipelineFactory pipelineFactory;
    private final ErrorReceiverProxy errorReceiverProxy;
    private final PipelineDataHolderFactory pipelineDataHolderFactory;
    private final PipelineContext pipelineContext;
    private final SecurityContext securityContext;
    private final TaskScopeMap taskScopeMap;

    private TaskContext taskContext;
    // See SteppingService.abandonSweep: a superseded reprocess is stopped by a flag, not an interrupt.
    private BooleanSupplier terminateCheck = () -> false;
    private LoggingErrorReceiver loggingErrorReceiver;

    @Inject
    ReprocessDriver(final Store streamStore,
                    final FeedHolder feedHolder,
                    final MetaDataHolder metaDataHolder,
                    final PipelineHolder pipelineHolder,
                    final MetaHolder metaHolder,
                    final LocationHolder locationHolder,
                    final LocationFactoryProxy locationFactory,
                    final CurrentUserHolder currentUserHolder,
                    final SteppingController controller,
                    final PipelineStore pipelineStore,
                    final PipelineFactory pipelineFactory,
                    final ErrorReceiverProxy errorReceiverProxy,
                    final PipelineDataHolderFactory pipelineDataHolderFactory,
                    final PipelineContext pipelineContext,
                    final SecurityContext securityContext,
                    final TaskScopeMap taskScopeMap) {
        this.streamStore = streamStore;
        this.feedHolder = feedHolder;
        this.metaDataHolder = metaDataHolder;
        this.pipelineHolder = pipelineHolder;
        this.metaHolder = metaHolder;
        this.locationHolder = locationHolder;
        this.locationFactory = locationFactory;
        this.currentUserHolder = currentUserHolder;
        this.controller = controller;
        this.pipelineStore = pipelineStore;
        this.pipelineFactory = pipelineFactory;
        this.errorReceiverProxy = errorReceiverProxy;
        this.pipelineDataHolderFactory = pipelineDataHolderFactory;
        this.pipelineContext = pipelineContext;
        this.securityContext = securityContext;
        this.taskScopeMap = taskScopeMap;
    }

    /**
     * Reprocess {@code startElementId} and its downstream for one stream, feeding it each record's stored
     * output of {@code feedElementId} (the reusable upstream element immediately above the start element) and
     * capturing the reprocessed IO into {@code targetSweep}'s store. The feed's output is the start element's
     * input, and it is read under the feed's own (unchanged) fingerprint - which is why an edit that re-keys
     * the start element does not have to re-run the pipeline above the feed. Every exit path signals the
     * sweep, as readers block on it.
     */
    public void reprocess(final TaskContext taskContext,
                          final PipelineStepRequest request,
                          final long metaId,
                          final String startElementId,
                          final String feedElementId,
                          final StepDataStore sourceStore,
                          final StreamSweep targetSweep,
                          final ElementFingerprints fingerprints,
                          final MidPipelineScope scope) {
        reprocess(taskContext, request, metaId, startElementId, feedElementId, sourceStore, targetSweep,
                fingerprints, scope, null);
    }

    /**
     * Reprocess a <b>single record</b> rather than the whole stream: read that record's stored upstream
     * output, fire it through the edited element, capture the result, stop.
     * <p>
     * This is what makes an edit cheap wherever the user happens to be. Re-running the element over the whole
     * stream costs time proportional to how deep the record is - measured at roughly half a second at record
     * 1,000 and around a minute at record 100,000 - to answer a question about one record whose input is
     * already sitting in the store. See {@code stepping-design.md} §11.
     *
     * @param onDemandRange the records to materialise, or null to reprocess the whole stream.
     */
    public void reprocess(final TaskContext taskContext,
                          final PipelineStepRequest request,
                          final long metaId,
                          final String startElementId,
                          final String feedElementId,
                          final StepDataStore sourceStore,
                          final StreamSweep targetSweep,
                          final ElementFingerprints fingerprints,
                          final MidPipelineScope scope,
                          final RecordRange onDemandRange) {
        this.taskContext = taskContext;
        targetSweep.setTaskContext(taskContext);
        if (targetSweep.isTerminateRequested()) {
            targetSweep.markError(new RuntimeException(
                    "Stepping reprocess of stream " + metaId + " was terminated before it started"));
            return;
        }

        try {
            securityContext.secure(AppPermission.STEPPING_PERMISSION, () ->
                    securityContext.useAsRead(() -> {
                        currentUserHolder.setCurrentUser(securityContext.getUserIdentity());
                        loggingErrorReceiver = new LoggingErrorReceiver();
                        errorReceiverProxy.setErrorReceiver(loggingErrorReceiver);

                        controller.setRequest(request);
                        controller.setTaskContext(taskContext);
                        // Abandoned (superseded) reprocesses are stopped by flag, not interrupt.
                        this.terminateCheck = targetSweep::isTerminateRequested;
                        controller.setTerminateCheck(targetSweep::isTerminateRequested);
                        controller.setCaptureTarget(
                                targetSweep.getStore(), fingerprints, targetSweep::recordCaptured);

                        try {
                            reprocessStream(request, metaId, startElementId, feedElementId, sourceStore,
                                    fingerprints, scope, onDemandRange);
                        } catch (final RuntimeException e) {
                            LOGGER.debug(e.getMessage(), e);
                            targetSweep.markError(e);
                            return;
                        }
                        // Only a reprocess that ran to the end may be reported complete. A terminated one
                        // (session closed, task cancelled) reprocessed only part of the stream, so mark it
                        // errored - otherwise a reader treats a truncated store as the whole stream and
                        // navigates silently past the un-reprocessed records. Same guard as StreamCaptureDriver.
                        if (taskContext.isTerminated() || targetSweep.isTerminateRequested()) {
                            targetSweep.markError(new RuntimeException(
                                    "Stepping reprocess of stream " + metaId + " was terminated"));
                        } else {
                            targetSweep.markFullyCaptured();
                        }
                    }));
        } catch (final Throwable t) {
            targetSweep.markError(t);
            throw t;
        }
    }

    private void reprocessStream(final PipelineStepRequest request,
                                 final long metaId,
                                 final String startElementId,
                                 final String feedElementId,
                                 final StepDataStore sourceStore,
                                 final ElementFingerprints fingerprints,
                                 final MidPipelineScope scope,
                                 final RecordRange onDemandRange) {
        final Source source;
        try {
            source = streamStore.openSource(metaId);
        } catch (final Exception e) {
            throw ProcessException.wrap(e);
        }
        if (source == null) {
            throw ProcessException.create("Stream " + metaId + " is no longer available");
        }

        // The source stays open for the whole reprocess. Record data comes from the store, but the pipeline
        // still reads the stream itself for everything that is not per-record, all of it reached through
        // metaHolder.getInputStreamProvider(): stream metadata (stroom:meta, stroom:meta-keys, via
        // StreamMetaDataProvider) and context reference data (a stroom:lookup against the stream's own
        // context child stream, via ReferenceData). Both degrade SILENTLY when the provider is absent - an
        // empty attribute map, or context data that is simply never loaded - so a reprocess without this
        // would serve quietly wrong output after an edit, which is exactly what a step is trusted to show.
        try (source) {
            final Meta meta = source.getMeta();
            final String feedName = meta.getFeedName();

            controller.setStreamInfo("id=" + metaId + ", feed=" + feedName);
            metaHolder.setMeta(meta);
            metaHolder.setChildDataType(request.getChildStreamType());

            final MidPipeline midPipeline = buildMidPipeline(request, feedName, startElementId, scope);
            final Element entryElement = midPipeline.entry();
            final ContentHandler entryHandler = (ContentHandler) entryElement;
            final ElementId feedId = new ElementId(feedElementId);
            // Read the feed's OUTPUT (= the start element's input) under the feed's own, unchanged fingerprint.
            final String feedFingerprint = fingerprints.getCumulativeFingerprint(feedElementId);

            final StreamLocationFactory streamLocationFactory = new StreamLocationFactory();
            locationFactory.setLocationFactory(streamLocationFactory);

            final long maxPartIndex = source.count() - 1;
            if (onDemandRange != null) {
                // Records written under the indices they actually have - so the store must not expect them
                // to continue a sequence, and the detector must not number them from zero.
                controller.setRecordOrder(StepDataStore.RecordOrder.ON_DEMAND);
                setDetectorBase(onDemandRange.firstRecord());
            }
            entryElement.startProcessing();
            try {
                for (final long partIndex : onDemandRange == null
                        ? sourceStore.getPartIndices()
                        : List.of(onDemandRange.partIndex())) {
                    if (taskContext.isTerminated() || terminateCheck.getAsBoolean()) {
                        break;
                    }
                    metaHolder.setPartIndex(partIndex);
                    streamLocationFactory.setPartIndex(partIndex);
                    // The store's parts came from this same stream, so this holds; guarded rather than
                    // asserted because a missing part must not cost the user the whole reprocess.
                    if (partIndex <= maxPartIndex) {
                        try (final InputStreamProvider inputStreamProvider = source.get(partIndex)) {
                            metaHolder.setInputStreamProvider(inputStreamProvider);
                            fireRecords(entryElement, entryHandler, sourceStore, metaId, partIndex, feedId,
                                    feedFingerprint, onDemandRange);
                        }
                    } else {
                        fireRecords(entryElement, entryHandler, sourceStore, metaId, partIndex, feedId,
                                feedFingerprint, onDemandRange);
                    }
                }
            } finally {
                entryElement.endProcessing();
            }
        } catch (final IOException e) {
            throw ProcessException.wrap(e);
        }
    }

    private void fireRecords(final Element entryElement,
                             final ContentHandler entryHandler,
                             final StepDataStore sourceStore,
                             final long metaId,
                             final long partIndex,
                             final ElementId feedId,
                             final String feedFingerprint,
                             final RecordRange onDemandRange) {
        final long first = onDemandRange != null
                ? onDemandRange.firstRecord()
                : sourceStore.getFirstRecordIndex(partIndex);
        final long last = onDemandRange != null
                ? onDemandRange.lastRecord()
                : sourceStore.getLastRecordIndex(partIndex);
        if (first < 0 || last < 0 || feedFingerprint == null) {
            return;
        }

        // startStream resets the record detector's index to -1; firing records in order then reproduces the
        // same per-part record indices the original sweep captured under.
        entryElement.startStream();
        try {
            for (long recordIndex = first; recordIndex <= last; recordIndex++) {
                final StepLocation loc = new StepLocation(metaId, partIndex, recordIndex);
                final byte[] inputEvents = sourceStore.getElementData(loc, feedId, feedFingerprint)
                        .map(CapturedElementData::output)
                        .filter(data -> data != null && data.isSaxEvents())
                        .map(CapturedData::data)
                        .orElse(null);
                // Every record in the captured range has SAX feed output: the feed is a parser or mutator
                // (its output is events), and putRecord commits all elements of a record atomically. A gap
                // would misalign the record detector's index (which only advances on an actual replay) from
                // the source record index and silently mis-key every later record - so fail loudly. It also
                // catches a mis-planned reprocess whose feed produces text rather than events.
                if (inputEvents == null) {
                    throw ProcessException.create("Reprocess of stream " + metaId + " has no replayable SAX "
                            + "output for feed " + feedId + " at " + loc);
                }
                // Feed the per-record source location the original sweep captured back into the holder, so
                // downstream location functions (stroom:record-no/source/line-from...) report the
                // source-parse location rather than defaults - this reprocess runs below the SplitFilter
                // that normally populates it. Record-level; per stepping-design.md §11.
                locationHolder.setReplayLocation(sourceStore.getSourceLocation(loc).orElse(null));
                // Restore the stroom:put map the original sweep captured for this record. The elements above
                // the edit are deliberately not re-run, so their stroom:put calls never happen here; without
                // this a stroom:get below the edit would silently return nothing where the full sweep gave it
                // a value. Anything the re-run elements put themselves overwrites this as they run.
                taskScopeMap.restore(sourceStore.getScopeMap(loc));
                restoreCounts(sourceStore, loc);
                try {
                    SaxEventReader.replay(inputEvents, entryHandler);
                } catch (final Exception e) {
                    throw ProcessException.wrap(e);
                }
            }
        } finally {
            entryElement.endStream();
        }
    }

    /**
     * A contiguous run of records within one part, to be materialised on demand. One record is the
     * edit-then-refresh case; a longer run is a window scanned for a filter match.
     */
    public record RecordRange(long partIndex, long firstRecord, long lastRecord) {

        public static RecordRange of(final StepLocation location) {
            return new RecordRange(location.getPartIndex(), location.getRecordIndex(),
                    location.getRecordIndex());
        }
    }

    /**
     * Resume every counting element from where the sweep left it at the end of the <b>previous</b> record.
     * <p>
     * The previous record's snapshot is the right one by definition: a count stored at the end of record
     * {@code N-1} is exactly "what this element had counted before record N". Record 0 has no predecessor and
     * needs no restore - a fresh element already starts at zero, which is correct there.
     * <p>
     * An element the snapshot says nothing about is left alone rather than zeroed. "Was not counting" and
     * "had counted nothing" are different: the first happens when a pipeline gains a counting element after
     * the sweep, and forcing it to zero would be no better than the bug this fixes.
     */
    private void restoreCounts(final StepDataStore sourceStore, final StepLocation loc) {
        final long recordIndex = loc.getRecordIndex();
        if (recordIndex <= 0) {
            return;
        }
        final Optional<RecordScopeState> previous = sourceStore.getRecordScopeState(
                new StepLocation(loc.getMetaId(), loc.getPartIndex(), recordIndex - 1));
        if (previous.isEmpty()) {
            return;
        }
        for (final ElementMonitor monitor : controller.getMonitors()) {
            if (monitor.getElement() instanceof final SteppingCounter counter) {
                previous.get().countFor(monitor.getElementId().getId())
                        .ifPresent(counter::setSteppingCount);
            }
        }
    }

    /**
     * Tell the entry record detector what index to report the record it is about to be given. Without this a
     * single replayed record is numbered 0 and would be captured under the wrong index entirely.
     */
    private void setDetectorBase(final long baseRecordIndex) {
        final RecordDetector recordDetector = controller.getRecordDetector();
        if (recordDetector instanceof final SAXRecordDetector saxRecordDetector) {
            saxRecordDetector.setBaseRecordIndex(baseRecordIndex);
        } else {
            // createFrom always forces a SAXRecordDetector at the entry, so this cannot happen - but
            // capturing the record under index 0 would be silently wrong, so say so rather than carry on.
            throw ProcessException.create("Cannot replay a single record: the entry record detector is "
                                          + (recordDetector == null ? "absent" : recordDetector.getClass()
                                                  .getSimpleName()));
        }
    }

    private MidPipeline buildMidPipeline(final PipelineStepRequest request,
                                         final String feedName,
                                         final String startElementId,
                                         final MidPipelineScope scope) {
        final PipelineDoc pipelineDoc = request.getPipelineDoc();
        feedHolder.setFeedName(feedName);
        metaDataHolder.setMetaDataProvider(new StreamMetaDataProvider(metaHolder, pipelineStore));
        pipelineHolder.setPipeline(DocRefUtil.create(pipelineDoc));
        pipelineContext.setStepping(true);

        final PipelineDataHolder pipelineDataHolder = pipelineDataHolderFactory.create(pipelineDoc);
        final PipelineData pipelineData = pipelineDataHolder.getMergedPipelineData();

        final MidPipeline midPipeline =
                pipelineFactory.createFrom(pipelineData, taskContext, controller, startElementId, scope);
        if (controller.getRecordDetector() == null
                || controller.getMonitors() == null
                || controller.getMonitors().isEmpty()) {
            throw ProcessException.create("Unable to build a reprocess pipeline from " + startElementId);
        }
        return midPipeline;
    }
}
