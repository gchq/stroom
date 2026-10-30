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

package stroom.document.client;

import stroom.docref.DocRef;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.util.shared.NullSafe;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

import java.util.List;

/// The markup of a delete confirmation's detail (delete-confirm.css), which the confirmation dialog
/// shows under its question: what else the delete deletes (a summary by type, then each item, with
/// its icon), and what depends on what is deleted. Its caller says what to show; this says how.
public final class DeleteConfirmationDetail {

    private static final Templates TEMPLATES = GWT.create(Templates.class);

    private final SafeHtmlBuilder builder = new SafeHtmlBuilder();

    /// Starts an empty detail.
    public DeleteConfirmationDetail() {
        // Filled by its add methods
    }

    /// Adds the heading of a list, e.g. "The following items depend on this:".
    ///
    /// @param text The heading.
    public void addHeader(final String text) {
        builder.append(TEMPLATES.summaryHeader(text));
    }

    /// Adds the number of items of each type a list holds, one line each, e.g. "Feed (3)", then a gap
    /// before the items themselves.
    ///
    /// @param lines The lines, in the order shown.
    public void addTypeCounts(final List<String> lines) {
        lines.forEach(line -> builder.append(TEMPLATES.typeCount(line)));
        builder.append(TEMPLATES.gap());
    }

    /// Adds a row for each document: its type's icon, then its name (or its UUID if it has none).
    ///
    /// @param docRefs The documents.
    public void addDocuments(final List<DocRef> docRefs) {
        for (final DocRef docRef : docRefs) {
            final DocumentType documentType = DocumentTypeRegistry.get(docRef.getType());
            final SafeHtml icon = documentType != null && documentType.getIcon() != null
                    ? SvgImageUtil.toSafeHtml(documentType.getDisplayType(), documentType.getIcon(), "icon-wrap")
                    : SafeHtmlUtils.EMPTY_SAFE_HTML;
            final String name = NullSafe.isBlankString(docRef.getName())
                    ? docRef.getUuid()
                    : docRef.getName();
            builder.append(TEMPLATES.document(icon, name));
        }
    }

    /// Adds a row saying there are more documents than were listed.
    public void addMore() {
        builder.append(TEMPLATES.document(SafeHtmlUtils.EMPTY_SAFE_HTML, "…and more"));
    }

    /// Adds text, e.g. what went wrong.
    ///
    /// @param text The text.
    public void addText(final String text) {
        builder.appendEscaped(text);
    }

    /// Adds a note on a line of its own, e.g. that there are items the user may not see.
    ///
    /// @param text The note.
    public void addNote(final String text) {
        builder.append(TEMPLATES.note(text));
    }

    /// Starts a new line, e.g. between the contained items and the dependants.
    public void addLineBreak() {
        builder.append(TEMPLATES.lineBreak());
    }

    /// @return The detail, as added.
    public SafeHtml toSafeHtml() {
        return builder.toSafeHtml();
    }


    // --------------------------------------------------------------------------------


    interface Templates extends SafeHtmlTemplates {

        @Template("<div class=\"delete-confirm__summary-header\">{0}</div>")
        SafeHtml summaryHeader(String text);

        @Template("<div class=\"delete-confirm__type-count\">{0}</div>")
        SafeHtml typeCount(String text);

        @Template("<div class=\"delete-confirm__gap\"></div>")
        SafeHtml gap();

        @Template("<div class=\"doc-ref-link\">{0}{1}</div>")
        SafeHtml document(SafeHtml icon, String name);

        @Template("<br>{0}")
        SafeHtml note(String text);

        @Template("<br>")
        SafeHtml lineBreak();
    }
}
