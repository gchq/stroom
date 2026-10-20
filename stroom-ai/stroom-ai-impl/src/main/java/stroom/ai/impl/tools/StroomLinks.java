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

package stroom.ai.impl.tools;

import stroom.docref.DocRef;
import stroom.util.shared.NullSafe;

/// The one place the tools make Markdown links to Stroom objects (documents, streams and events) in what they
/// return. A call's links come from its context ([ToolCallContext#links()]), so the form can depend on who the
/// call is for.
///
/// There is one form today, [#CHAT]: the "Ask Stroom AI" chat's `?action=…` links, which the chat's UI opens in
/// Stroom when clicked, e.g. `[stream 12](?action=open-stream&id=12)`. Another form (e.g. absolute Stroom URLs
/// for external clients) would be another instance, chosen by the context from its caller.
public final class StroomLinks {

    /// The chat's links, which the chat's UI opens in Stroom.
    public static final StroomLinks CHAT = new StroomLinks("?");

    private static final String STREAM = "stream";

    // What a link's target starts with, before its query parameters.
    private final String base;

    private StroomLinks(final String base) {
        this.base = base;
    }

    /// @param docRef A document.
    /// @return A link that opens the document, named after it (or its UUID if it has no name), with any square
    /// brackets in the name made round so as not to break the link, e.g.
    /// `[ACME-EVENTS](?action=open-doc&docType=Feed&docUuid=…)`.
    public String document(final DocRef docRef) {
        final String name = NullSafe.nonBlankStringElse(docRef.getName(), docRef.getUuid())
                .replace("[", "(")
                .replace("]", ")");
        return "[" + name + "](" + base + "action=open-doc&docType=" + docRef.getType() + "&docUuid="
               + docRef.getUuid() + ")";
    }

    /// @param id A stream's id, or null.
    /// @return A link that opens the stream, e.g. `[stream 12](?action=open-stream&id=12)`, or `stream ?` if the
    /// id is null.
    public String stream(final Long id) {
        return stream(id, STREAM);
    }

    /// @param id    A stream's id, or null.
    /// @param label What to call the stream, e.g. `Error stream`.
    /// @return A link that opens the stream, e.g. `[Error stream 12](?action=open-stream&id=12)`, or the label
    /// followed by ` ?` if the id is null.
    public String stream(final Long id, final String label) {
        return id == null
                ? label + " ?"
                : "[" + label + " " + id + "](" + streamTarget(id) + ")";
    }

    /// @param streamId A stream's id.
    /// @param recordNo The number of one of its events (records), from 1.
    /// @return A link that opens the stream at the event, e.g. `[event 12:5](?action=open-stream&id=12&recordNo=5)`.
    public String event(final long streamId, final long recordNo) {
        return "[event " + streamId + ":" + recordNo + "](" + streamTarget(streamId) + "&recordNo=" + recordNo + ")";
    }

    private String streamTarget(final long id) {
        return base + "action=open-stream&id=" + id;
    }
}
