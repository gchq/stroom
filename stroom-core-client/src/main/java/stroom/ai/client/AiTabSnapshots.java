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

package stroom.ai.client;

import stroom.ai.shared.AiSteppingContext;
import stroom.ai.shared.AiSteppingElement;
import stroom.ai.shared.AiSteppingError;
import stroom.ai.shared.AiTabSearch;
import stroom.ai.shared.AiTabSnapshot;
import stroom.ai.shared.AiTabSnapshotPart;
import stroom.ai.shared.AskStroomAiContext;
import stroom.ai.shared.DashboardTableContext;
import stroom.ai.shared.QueryTableContext;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.Search;
import stroom.docref.DocRef;
import stroom.query.shared.QueryContext;
import stroom.query.shared.QuerySearchRequest;
import stroom.util.shared.NullSafe;

import java.util.List;

/// What the chat does with the attached tab's snapshot, without anything from the browser, so it can be tested: the
/// text of the tab's chip, the snapshot's hash, and whether to send it in full or only its hash.
public final class AiTabSnapshots {

    /// What a document edited with a form says, as its unsaved edits aren't shared.
    public static final String FORM_NOTE = "This document is edited with a form, so its unsaved edits aren't "
                                           + "shared; the saved settings can be read with document_info and "
                                           + "read_document.";

    /// What a folder's tab says: what is in it is listed with a tool, not shared.
    public static final String FOLDER_NOTE = "This is a folder: list what is in it with folder_contents and its "
                                             + "uuid (its Data tab is 'streams', its Processors tab "
                                             + "'processors', and its Active Tasks tab 'tasks').";

    // Between the fields hashed, so that moving text from one field to the next changes the hash.
    private static final char SEPARATOR = '\u0001';

    private AiTabSnapshots() {
        // Static methods only.
    }

    /// @param docRef The tab's document.
    /// @param dirty  True if the tab has unsaved changes.
    /// @return What the tab's chip says, e.g. "Tab: Event translation (unsaved)".
    public static String chipText(final DocRef docRef, final boolean dirty) {
        final String name = docRef == null
                ? "Document"
                : NullSafe.nonBlankStringElse(docRef.getName(), NullSafe.string(docRef.getUuid()));
        return "Tab: " + name + (dirty
                ? " (unsaved)"
                : "");
    }

    /// @param docRef The tab's document.
    /// @return The chip's tooltip.
    public static String chipTooltip(final DocRef docRef) {
        final String type = NullSafe.nonBlankStringElse(NullSafe.get(docRef, DocRef::getType), "Document");
        final String name = NullSafe.nonBlankStringElse(NullSafe.get(docRef, DocRef::getName), "?");
        return "Attached to the chat: " + type + " '" + name + "' is shared with each question, as you see it "
               + "then, whichever tab you are on. Click × to remove it.";
    }

    /// @param docRef The tab's document.
    /// @return What the button that attaches the tab says, e.g. "Event translation".
    public static String attachText(final DocRef docRef) {
        return docRef == null
                ? "Document"
                : NullSafe.nonBlankStringElse(docRef.getName(), NullSafe.string(docRef.getUuid()));
    }

    /// @param docRef   The tab's document.
    /// @param replaces True if another tab is attached, which this one would replace.
    /// @return The tooltip of the button that attaches the tab.
    public static String attachTooltip(final DocRef docRef, final boolean replaces) {
        final String type = NullSafe.nonBlankStringElse(NullSafe.get(docRef, DocRef::getType), "Document");
        final String name = NullSafe.nonBlankStringElse(NullSafe.get(docRef, DocRef::getName), "?");
        return "Attach " + type + " '" + name + "' to the chat, so it is shared with each question, as you see it"
               + (replaces
                ? ", in place of the tab attached now"
                : "");
    }

    /// Makes a snapshot with its hash.
    ///
    /// @return The snapshot.
    public static AiTabSnapshot create(final DocRef docRef,
                                       final String tabLabel,
                                       final String innerTab,
                                       final boolean dirty,
                                       final List<AiTabSnapshotPart> parts,
                                       final List<AiTabSearch> searches,
                                       final String note) {
        return create(docRef, tabLabel, innerTab, dirty, parts, searches, note, null);
    }

    /// Makes a snapshot with its hash, e.g. of a pipeline's stepping screen.
    ///
    /// @param stepping What a pipeline's stepping screen shows, or null if the tab isn't stepping.
    /// @return The snapshot.
    public static AiTabSnapshot create(final DocRef docRef,
                                       final String tabLabel,
                                       final String innerTab,
                                       final boolean dirty,
                                       final List<AiTabSnapshotPart> parts,
                                       final List<AiTabSearch> searches,
                                       final String note,
                                       final AiSteppingContext stepping) {
        final AiTabSnapshot snapshot = new AiTabSnapshot(docRef, tabLabel, innerTab, dirty, parts, searches, note,
                stepping, null);
        return snapshot.withHash(hash(snapshot));
    }

    /// @param snapshot A snapshot, in full.
    /// @return A hash of what it holds, as hex: the same for the same content, and different (almost always)
    /// when any of it changes.
    public static String hash(final AiTabSnapshot snapshot) {
        final StringBuilder sb = new StringBuilder();
        add(sb, NullSafe.get(snapshot.getDocRef(), DocRef::getType));
        add(sb, NullSafe.get(snapshot.getDocRef(), DocRef::getUuid));
        add(sb, NullSafe.get(snapshot.getDocRef(), DocRef::getName));
        add(sb, snapshot.getTabLabel());
        add(sb, snapshot.getInnerTab());
        add(sb, String.valueOf(snapshot.isDirty()));
        add(sb, snapshot.getNote());
        for (final AiTabSnapshotPart part : NullSafe.list(snapshot.getParts())) {
            add(sb, "part");
            add(sb, part.getName());
            add(sb, part.getMediaType());
            add(sb, part.getText());
        }
        for (final AiTabSearch search : NullSafe.list(snapshot.getSearches())) {
            add(sb, "search");
            add(sb, search.getName());
            add(sb, search.getComponentId());
            add(sb, String.join(",", NullSafe.list(search.getColumns())));
            add(sb, String.valueOf(search.getVisibleRows()));
            add(sb, String.valueOf(search.isSearching()));
            add(sb, fingerprint(search.getResults()));
            add(sb, fingerprint(search.getLive()));
        }
        if (snapshot.getStepping() != null) {
            add(sb, "stepping");
            add(sb, fingerprint(snapshot.getStepping()));
        }
        return fnv1a(sb.toString());
    }

    /// @param stepping What a pipeline's stepping screen shows, or null.
    /// @return All of it, so that any change to where the step is, the elements or their errors changes the hash.
    static String fingerprint(final AiSteppingContext stepping) {
        if (stepping == null) {
            return null;
        }
        final StringBuilder sb = new StringBuilder();
        add(sb, stepping.getFeedName());
        add(sb, String.valueOf(stepping.getMetaId()));
        add(sb, stepping.getStreamType());
        add(sb, stepping.getChildStreamType());
        add(sb, String.valueOf(stepping.getPartNo()));
        add(sb, String.valueOf(stepping.getRecordNo()));
        add(sb, stepping.getSelectedElementId());
        for (final AiSteppingElement element : stepping.getElements()) {
            add(sb, "element");
            add(sb, element.getId());
            add(sb, element.getType());
            add(sb, NullSafe.get(element.getDocRef(), DocRef::getUuid));
            add(sb, element.isHasCode() + "," + element.isDirty() + "," + element.getErrorCount() + ","
                    + element.getWarningCount());
            for (final AiSteppingError error : element.getErrors()) {
                add(sb, error.getSeverity());
                add(sb, error.getPane());
                add(sb, error.getLineNo() + ":" + error.getColNo());
                add(sb, error.getMessage());
            }
        }
        for (final String error : stepping.getGeneralErrors()) {
            add(sb, error);
        }
        return sb.toString();
    }

    /// @param context A search request behind a table, or null.
    /// @return What of it decides the rows: its query or expression, parameters, time range, data source and
    /// query key.
    static String fingerprint(final AskStroomAiContext context) {
        final StringBuilder sb = new StringBuilder();
        if (context instanceof final QueryTableContext query) {
            final QuerySearchRequest request = query.getSearchRequest();
            add(sb, "query");
            add(sb, NullSafe.get(request, QuerySearchRequest::getQuery));
            add(sb, NullSafe.get(request, QuerySearchRequest::getQueryKey, Object::toString));
            add(sb, NullSafe.get(request, QuerySearchRequest::getQueryContext, QueryContext::getTimeRange,
                    Object::toString));
            add(sb, NullSafe.get(request, QuerySearchRequest::getQueryContext, QueryContext::getParams,
                    Object::toString));
        } else if (context instanceof final DashboardTableContext dashboard) {
            final DashboardSearchRequest request = dashboard.getSearchRequest();
            final Search search = NullSafe.get(request, DashboardSearchRequest::getSearch);
            add(sb, "dashboard");
            add(sb, NullSafe.get(request, DashboardSearchRequest::getQueryKey, Object::toString));
            add(sb, NullSafe.get(search, Search::getDataSourceRef, DocRef::getUuid));
            add(sb, NullSafe.get(search, Search::getExpression, Object::toString));
            add(sb, NullSafe.get(search, Search::getParams, Object::toString));
            add(sb, NullSafe.get(search, Search::getTimeRange, Object::toString));
        } else if (context != null) {
            add(sb, context.getDescription());
        }
        return sb.toString();
    }

    /// @param snapshot     The snapshot to send, in full, or null.
    /// @param lastSentHash The hash of the snapshot last sent to the chat, or null.
    /// @return What to send: nothing for no snapshot, only its hash if it is the one last sent, or else in full.
    public static AiTabSnapshot toSend(final AiTabSnapshot snapshot, final String lastSentHash) {
        if (snapshot == null) {
            return null;
        }
        return snapshot.getHash() != null && snapshot.getHash().equals(lastSentHash)
                ? snapshot.hashOnly()
                : snapshot;
    }

    private static void add(final StringBuilder sb, final String value) {
        // The length first, so no value can be mistaken for others.
        final String text = NullSafe.string(value);
        sb.append(value == null
                ? -1
                : text.length()).append(SEPARATOR).append(text).append(SEPARATOR);
    }

    /// @return The 64 bit FNV-1a hash of the text's characters, as hex.
    static String fnv1a(final String text) {
        return AiTabSnapshotPart.hashText(text);
    }
}
