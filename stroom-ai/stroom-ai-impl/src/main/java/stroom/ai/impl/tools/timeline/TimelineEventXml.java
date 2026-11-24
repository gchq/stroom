/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.impl.tools.timeline;

import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.streams.EventSegments;
import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.Source;
import stroom.data.store.api.Store;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.task.api.TaskTerminatedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/// Reads the Events XML behind a timeline's events, as `fetch_events` reads an index's rows' events: by StreamId and
/// EventId (the event's segment of its stream), a stream at a time, its events in the order they are stored, as the
/// user (the stream store checks they may view each stream's feed; the View Data permission is checked here, as the
/// data viewer needs it). What was read is audited as streams viewed.
class TimelineEventXml {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TimelineEventXml.class);

    private final Provider<Store> storeProvider;
    private final Provider<SecurityContext> securityContextProvider;
    private final AiAuditLog auditLog;

    /// @param storeProvider           Reads the streams.
    /// @param securityContextProvider Checks the user may view data.
    /// @param auditLog                Audits the streams read.
    @Inject
    TimelineEventXml(final Provider<Store> storeProvider,
                     final Provider<SecurityContext> securityContextProvider,
                     final AiAuditLog auditLog) {
        this.storeProvider = storeProvider;
        this.securityContextProvider = securityContextProvider;
        this.auditLog = auditLog;
    }

    /// Reads events' XML.
    ///
    /// @param session  The session, for the audit log.
    /// @param ids      The events wanted: each stream's event ids.
    /// @param maxBytes The largest event read.
    /// @param stopped  True once the user has stopped the call.
    /// @param progress Told how many events have been read so far.
    /// @return Each event's XML (its stream's header and footer around it), by `streamId:eventId`, and how many
    ///         couldn't be read and why.
    Read read(final ToolSession session,
              final Map<Long, List<Long>> ids,
              final int maxBytes,
              final BooleanSupplier stopped,
              final IntConsumer progress) {
        final int wanted = ids.values().stream().mapToInt(List::size).sum();
        if (!canViewData()) {
            return new Read(Map.of(), 0, 0, wanted, false);
        }
        final Map<String, String> xml = new LinkedHashMap<>();
        final Set<Long> streams = new LinkedHashSet<>();
        int missing = 0;
        int unreadable = 0;
        for (final Map.Entry<Long, List<Long>> stream : ids.entrySet()) {
            if (stopped.getAsBoolean()) {
                throw new TaskTerminatedException();
            }
            try (final Source source = storeProvider.get().openSource(stream.getKey());
                    final InputStreamProvider provider = source.get(0)) {
                final long segments = EventSegments.count(provider);
                if (segments < 3) {
                    // Not a segmented Events stream.
                    missing += stream.getValue().size();
                    continue;
                }
                final String header = EventSegments.read(provider, 0, maxBytes);
                final String footer = EventSegments.read(provider, segments - 1, maxBytes);
                for (final long eventId : stream.getValue().stream().sorted().toList()) {
                    if (eventId < 1 || eventId > segments - 2) {
                        missing++;
                        continue;
                    }
                    final String event = EventSegments.read(provider, eventId, maxBytes);
                    if (event == null || header == null || footer == null) {
                        unreadable++;
                        continue;
                    }
                    xml.put(stream.getKey() + ":" + eventId, header + event + footer);
                    streams.add(stream.getKey());
                }
            } catch (final IOException | RuntimeException e) {
                if (e instanceof final TaskTerminatedException terminated) {
                    throw terminated;
                }
                // E.g. a stream deleted by retention, or of a feed the user may not see.
                LOGGER.debug(() -> "Unable to read stream " + stream.getKey() + ": " + e.getMessage(), e);
                missing += stream.getValue().size();
            }
            progress.accept(xml.size());
        }
        if (!streams.isEmpty()) {
            auditLog.streamsViewed(session, streams, "Read the Events XML of " + xml.size() + " event(s) from "
                                                     + streams.size() + " stream(s) for an activity timeline "
                                                     + "(activity_timeline)");
        }
        return new Read(xml, missing, unreadable, 0, true);
    }

    // True if the user may view data, as the Data screen needs; false if they may not, or if checking fails.
    private boolean canViewData() {
        try {
            return securityContextProvider.get().hasAppPermission(AppPermission.VIEW_DATA_PERMISSION);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to check the View Data permission", e);
            return false;
        }
    }


    // --------------------------------------------------------------------------------


    /// What was read.
    ///
    /// @param xml         Each event's XML, by `streamId:eventId`.
    /// @param missing     Events whose stream or segment is gone (retention, a deleted stream), or isn't Events.
    /// @param unreadable  Events larger than the limit.
    /// @param notAllowed  Events not read as the user may not view data.
    /// @param canViewData False if the user may not view data, so none was read.
    record Read(Map<String, String> xml, int missing, int unreadable, int notAllowed, boolean canViewData) {

        /// Copies the map.
        Read {
            xml = Map.copyOf(xml);
        }
    }
}
