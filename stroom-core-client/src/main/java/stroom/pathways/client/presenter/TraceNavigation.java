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

package stroom.pathways.client.presenter;

import stroom.docref.DocRef;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.pathways.shared.TracesDoc;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.Item;

import com.google.gwt.event.shared.HasHandlers;
import com.google.inject.Singleton;

import java.util.List;

/**
 * Opens a traces store on one trace.
 *
 * <p>The paths and changes tables both name the trace that taught the model something, and the next
 * question is what that trace did. This opens the store that trace came from, with the time range
 * set around when it ran and the quick filter set to its id.
 *
 * <p>Opening a document takes a {@link DocRef} and nothing else, so the trace to show is held here
 * until the store that opens asks for it.
 */
@Singleton
public class TraceNavigation {

    // Either side of the moment the trace ran. The model records that alongside the moment it learnt
    // from the trace, which are not the same: a trace waits in a queue to be applied, and that wait is
    // minutes when the consumer keeps up and as long as processing is stopped for when it does not.
    // A minute is enough for a trace whose own clock disagrees slightly with the store's.
    private static final long WINDOW_MS = 60_000L;

    // The trace to show, held until the store it is in opens and shows it. Only ever one, because
    // it is set and read within a single open.
    private TraceToShow traceToShow;

    /**
     * The menu for a trace id. Empty where the row names no trace store, no trace id or no time to
     * open it at, because there is then nowhere to send the reader.
     *
     * @param ranMs when the trace ran, which the model holds alongside when it learnt from the trace.
     */
    public List<Item> getMenuItems(final HasHandlers handlers,
                                   final String tracesDocUuid,
                                   final String traceId,
                                   final Long ranMs) {
        if (NullSafe.isBlankString(tracesDocUuid) || NullSafe.isBlankString(traceId) || ranMs == null) {
            return List.of();
        }
        // Opening a document needs its type and uuid; the name is only what a tab is labelled with,
        // and the model records the uuid because that is what does not change.
        final DocRef tracesDocRef = DocRef.builder()
                .type(TracesDoc.TYPE)
                .uuid(tracesDocUuid)
                .build();
        return List.of(new IconMenuItem.Builder()
                .priority(1)
                .icon(SvgImage.DOCUMENT_TRACES)
                .text("Show this trace")
                .command(() -> show(handlers, tracesDocRef, traceId, ranMs))
                .build());
    }

    public TraceToShow getAndClearTraceToShow(final DocRef tracesDocRef) {
        final TraceToShow trace = traceToShow;
        if (trace == null || !trace.tracesDocRef().equals(tracesDocRef)) {
            return null;
        }
        traceToShow = null;
        return trace;
    }

    private void show(final HasHandlers handlers,
                      final DocRef tracesDocRef,
                      final String traceId,
                      final long ranMs) {
        traceToShow = new TraceToShow(tracesDocRef, traceId, ranMs - WINDOW_MS, ranMs + WINDOW_MS);
        OpenDocumentEvent.builder(handlers, tracesDocRef)
                // Runs once the tab is open. A store that was already open is not read again, so
                // its onRead never runs and this is what shows the trace; a store that was closed
                // has already shown it in onRead, and this finds nothing.
                .callbackOnOpen(presenter -> {
                    final TraceToShow trace = getAndClearTraceToShow(tracesDocRef);
                    if (trace != null && presenter instanceof final TracesPresenter traces) {
                        traces.showTrace(trace.traceId(), trace.fromMs(), trace.toMs());
                    }
                })
                .fire();
    }

    /**
     * One trace, the store it is in and the window to show it in.
     */
    public record TraceToShow(DocRef tracesDocRef, String traceId, long fromMs, long toMs) {

    }
}
