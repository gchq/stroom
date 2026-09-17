/*
 * Copyright 2024 Crown Copyright
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

package stroom.datagen.impl;

import stroom.analytics.impl.ScheduledExecutable;
import stroom.analytics.impl.ScheduledExecutorService.ExecutionResult;
import stroom.analytics.shared.ExecutionSchedule;
import stroom.analytics.shared.ExecutionTracker;
import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.OutputStreamProvider;
import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.datagen.shared.DataGenDoc;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentNotFoundException;
import stroom.feed.api.FeedStore;
import stroom.feed.shared.FeedDoc;
import stroom.meta.api.MetaProperties;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.scheduler.Trigger;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs a {@link DataGenDoc}: writes its template into its destination feed as a Raw Events stream,
 * once per firing of the doc's schedule.
 * <p>
 * This plugs into the scheduling framework shared with analytics, so the schedule, run-as user,
 * execution history and enabled/disabled state all belong to the {@code ExecutionSchedule} rather
 * than to the doc. {@link stroom.analytics.impl.ScheduledExecutorService} owns that machinery and
 * calls the methods here; this class only supplies the docs to consider and the work to do for one
 * of them.
 * </p>
 */
public class ScheduledDataGenExecutable implements ScheduledExecutable<DataGenDoc> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ScheduledDataGenExecutable.class);

    private final DataGenStore dataGenStore;
    private final Store streamStore;
    private final FeedStore feedStore;

    @Inject
    ScheduledDataGenExecutable(final DataGenStore dataGenStore,
                               final Store streamStore,
                               final FeedStore feedStore) {
        this.dataGenStore = dataGenStore;
        this.streamStore = streamStore;
        this.feedStore = feedStore;
    }

    /**
     * Writes one stream of generated data.
     * <p>
     * Returns {@link ExecutionResult#error} rather than throwing when the doc is not usable. That
     * distinction matters: the caller turns any exception into an error <em>and disables the
     * schedule</em>, whereas a returned error is recorded against the execution but leaves the
     * generator enabled to try again once the problem is fixed.
     * </p>
     * <p>
     * All the checks happen before a target is opened. Opening one and then failing would leave an
     * empty stream behind in the destination feed.
     * </p>
     */
    @Override
    public ExecutionResult run(final DataGenDoc doc,
                               final Trigger trigger,
                               final Instant executionTime,
                               final Instant effectiveExecutionTime,
                               final ExecutionSchedule executionSchedule,
                               final ExecutionTracker currentTracker,
                               final ExecutionResult executionResult) {

        // Check the doc is fully configured before opening a target, else a partially configured doc would leave an
        // empty stream behind in the destination feed.
        final DocRef outputFeed = doc.getFeed();
        if (outputFeed == null) {
            return ExecutionResult.error("No destination feed has been set");
        }
        final String template = doc.getTemplate();
        if (template == null) {
            return ExecutionResult.error("No template has been set");
        }

        // Resolve the name from the feed itself rather than trusting the one cached in the doc's DocRef. That cached
        // name is whatever the feed was called when the user picked it, and MetaProperties resolves the destination by
        // name, creating a feed for any name it does not know - so a stale name would silently divert the data to a
        // new, empty feed.
        final String feedName;
        try {
            feedName = NullSafe.get(feedStore.readDocument(outputFeed), FeedDoc::getName);
        } catch (final DocumentNotFoundException e) {
            return ExecutionResult.error(LogUtil.message("Destination feed {} no longer exists", outputFeed));
        }
        if (NullSafe.isBlankString(feedName)) {
            return ExecutionResult.error(LogUtil.message("Destination feed {} has no name", outputFeed));
        }

        try {
            final MetaProperties metaProperties = MetaProperties.builder()
                    .feedName(feedName)
                    .typeName(StreamTypeNames.RAW_EVENTS)
                    .effectiveMs(effectiveExecutionTime.toEpochMilli())
                    .build();

            try (final Target streamTarget = streamStore.openTarget(metaProperties)) {
                try (final OutputStreamProvider outputStreamProvider = streamTarget.next()) {
                    outputStreamProvider.get().write(template.getBytes(StandardCharsets.UTF_8));
                }
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        return executionResult;
    }

    @Override
    public DocRef getDocRef(final DataGenDoc doc) {
        return doc.asDocRef();
    }

    @Override
    public DataGenDoc load(final DocRef docRef) {
        return dataGenStore.readDocument(docRef);
    }

    @Override
    public DataGenDoc reload(final DataGenDoc doc) {
        return dataGenStore.readDocument(doc.asDocRef());
    }

    /**
     * @return Every data generator, for {@link stroom.analytics.impl.ScheduledExecutorService} to match against the
     * configured schedules. One unreadable doc is logged and skipped rather than stopping the rest
     * from running.
     */
    @Override
    public List<DataGenDoc> getDocs() {
        // TODO this is not very efficient. It fetches all the docrefs from the DB,
        //  then loops over them to fetch+deser the associated doc for each one (one by one)
        //  It would be better if we had a json type col in the doc table, so that the
        //  we can pass some kind of json path query to the persistence layer that the DBPersistence
        //  can translate to a MySQL json path query.
        final List<DataGenDoc> currentDataGenerators = new ArrayList<>();
        final List<DocRef> docRefs = dataGenStore.list();
        for (final DocRef docRef : docRefs) {
            try {
                final DataGenDoc dataGenDoc = dataGenStore.readDocument(docRef);
                if (dataGenDoc != null) {
                    currentDataGenerators.add(dataGenDoc);
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            }
        }
        return currentDataGenerators;
    }

    /**
     * @return A short name for this kind of work, used in progress and log messages.
     */
    @Override
    public String getProcessType() {
        return "data gen";
    }

    /**
     * @return A human-readable identifier for the doc, used in log and execution history messages.
     */
    @Override
    public String getIdentity(final DataGenDoc doc) {
        return NullSafe.get(doc, d -> d.getName() + " (" + d.getUuid() + ")");
    }
}
