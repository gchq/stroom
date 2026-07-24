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
import stroom.pipeline.factory.PipelineDataHolder;
import stroom.pipeline.factory.PipelineDataHolderFactory;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.factory.PipelineFactory.MidPipeline;
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
import stroom.pipeline.stepping.store.StepDataStore;
import stroom.pipeline.task.StreamMetaDataProvider;
import stroom.pipeline.xml.event.SaxEventReader;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.task.api.TaskContext;
import stroom.util.shared.ElementId;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;

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

    private TaskContext taskContext;
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
                    final SecurityContext securityContext) {
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
                          final ElementFingerprints fingerprints) {
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
                        controller.setCaptureTarget(
                                targetSweep.getStore(), fingerprints, targetSweep::recordCaptured);

                        try {
                            reprocessStream(request, metaId, startElementId, feedElementId, sourceStore,
                                    fingerprints);
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
                                 final ElementFingerprints fingerprints) {
        // Open the source only for its metadata (feed name) - the record data is read from the store.
        final Meta meta;
        final String feedName;
        try (final Source source = streamStore.openSource(metaId)) {
            if (source == null) {
                throw ProcessException.create("Stream " + metaId + " is no longer available");
            }
            meta = source.getMeta();
            feedName = meta.getFeedName();
        } catch (final Exception e) {
            throw ProcessException.wrap(e);
        }

        controller.setStreamInfo("id=" + metaId + ", feed=" + feedName);
        metaHolder.setMeta(meta);
        metaHolder.setChildDataType(request.getChildStreamType());

        final MidPipeline midPipeline = buildMidPipeline(request, feedName, startElementId);
        final Element entryElement = midPipeline.entry();
        final ContentHandler entryHandler = (ContentHandler) entryElement;
        final ElementId feedId = new ElementId(feedElementId);
        // Read the feed's OUTPUT (= the start element's input) under the feed's own, unchanged fingerprint.
        final String feedFingerprint = fingerprints.getCumulativeFingerprint(feedElementId);

        final StreamLocationFactory streamLocationFactory = new StreamLocationFactory();
        locationFactory.setLocationFactory(streamLocationFactory);

        entryElement.startProcessing();
        try {
            for (final long partIndex : sourceStore.getPartIndices()) {
                if (taskContext.isTerminated()) {
                    break;
                }
                metaHolder.setPartIndex(partIndex);
                streamLocationFactory.setPartIndex(partIndex);
                fireRecords(entryElement, entryHandler, sourceStore, metaId, partIndex, feedId, feedFingerprint);
            }
        } finally {
            entryElement.endProcessing();
        }
    }

    private void fireRecords(final Element entryElement,
                             final ContentHandler entryHandler,
                             final StepDataStore sourceStore,
                             final long metaId,
                             final long partIndex,
                             final ElementId feedId,
                             final String feedFingerprint) {
        final long first = sourceStore.getFirstRecordIndex(partIndex);
        final long last = sourceStore.getLastRecordIndex(partIndex);
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

    private MidPipeline buildMidPipeline(final PipelineStepRequest request,
                                         final String feedName,
                                         final String startElementId) {
        final PipelineDoc pipelineDoc = request.getPipelineDoc();
        feedHolder.setFeedName(feedName);
        metaDataHolder.setMetaDataProvider(new StreamMetaDataProvider(metaHolder, pipelineStore));
        pipelineHolder.setPipeline(DocRefUtil.create(pipelineDoc));
        pipelineContext.setStepping(true);

        final PipelineDataHolder pipelineDataHolder = pipelineDataHolderFactory.create(pipelineDoc);
        final PipelineData pipelineData = pipelineDataHolder.getMergedPipelineData();

        final MidPipeline midPipeline =
                pipelineFactory.createFrom(pipelineData, taskContext, controller, startElementId);
        if (controller.getRecordDetector() == null
                || controller.getMonitors() == null
                || controller.getMonitors().isEmpty()) {
            throw ProcessException.create("Unable to build a reprocess pipeline from " + startElementId);
        }
        return midPipeline;
    }
}
