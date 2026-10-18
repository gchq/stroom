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

import stroom.ai.shared.AiAttachmentStatus;
import stroom.ai.shared.AiChatAttachment;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;

import com.google.gwt.safehtml.shared.SafeHtmlUtils;

/// How a table attached to a chat is shown: as a chip saying what it is, e.g. "Events - Logons", first in a list
/// above the input box while it is downloaded and until the next message is sent (when it can be removed), then in
/// the bubble of the message it was sent with.
final class AiAttachments {

    /// The attribute of a chip's remove button: the attachment's message id.
    static final String REMOVE_ATTRIBUTE = "data-remove-attachment-message-id";

    private static final String CHIP_ID_PREFIX = "ai-attachment-";
    private static final String DASHBOARD_PREFIX = "Dashboard '";
    private static final String TABLE_SEPARATOR = "' -> Table '";
    private static final String QUERY_PREFIX = "Query '";

    private AiAttachments() {
        // Static methods only.
    }

    /// @param description What the table is, as it was attached, e.g. "Dashboard 'Events' -> Table 'Logons'".
    /// @return What the chip says, e.g. "Events - Logons", or the description as it is if it isn't of a dashboard
    /// or query.
    static String label(final String description) {
        final String text = NullSafe.string(description).strip();
        if (text.startsWith(DASHBOARD_PREFIX)) {
            final int separator = text.indexOf(TABLE_SEPARATOR);
            if (separator > 0 && text.endsWith("'")) {
                return text.substring(DASHBOARD_PREFIX.length(), separator) + " - "
                       + text.substring(separator + TABLE_SEPARATOR.length(), text.length() - 1);
            }
            if (text.endsWith("'")) {
                return text.substring(DASHBOARD_PREFIX.length(), text.length() - 1);
            }
        }
        if (text.startsWith(QUERY_PREFIX) && text.endsWith("'")) {
            return text.substring(QUERY_PREFIX.length(), text.length() - 1);
        }
        return text.isEmpty()
                ? "Table"
                : text;
    }

    /// @param description What the table is, as it was attached.
    /// @return The icon of where it came from: a dashboard, a query, or else a table.
    static SvgImage icon(final String description) {
        final String text = NullSafe.string(description).strip();
        if (text.startsWith(DASHBOARD_PREFIX)) {
            return SvgImage.DOCUMENT_DASHBOARD;
        }
        if (text.startsWith(QUERY_PREFIX)) {
            return SvgImage.DOCUMENT_QUERY;
        }
        return SvgImage.TABLE;
    }

    /// @param attachment The attachment's status, or null if it isn't known yet.
    /// @return The chip's tooltip: its row count once it is downloaded, or else how it is getting on.
    static String tooltip(final AiChatAttachment attachment) {
        if (attachment == null || attachment.getStatus() == AiAttachmentStatus.PENDING
            || attachment.getStatus() == AiAttachmentStatus.DOWNLOADING) {
            return "Downloading...";
        }
        if (attachment.getStatus() == AiAttachmentStatus.ERROR) {
            return "Couldn't be downloaded: " + NullSafe.nonBlankStringElse(attachment.getErrorMessage(),
                    "unknown error");
        }
        final Integer rows = attachment.getRowCount();
        final String count = rows == null
                ? "Downloaded"
                : rows + (rows == 1
                        ? " row"
                        : " rows");
        return attachment.isTruncated()
                ? count + " (cut to the row limit)"
                : count;
    }

    /// @param attachment The attachment's status, or null if it isn't known yet.
    /// @return True while it is being downloaded.
    static boolean isDownloading(final AiChatAttachment attachment) {
        return attachment == null || attachment.getStatus() == AiAttachmentStatus.PENDING
               || attachment.getStatus() == AiAttachmentStatus.DOWNLOADING;
    }

    /// @param attachmentId The attachment.
    /// @return The id of its chip's element.
    static String chipId(final int attachmentId) {
        return CHIP_ID_PREFIX + attachmentId;
    }

    /// Adds an attachment's chip: its icon (a button to view its data), what it is, and while it is downloaded a
    /// spinning circle, which is replaced, if it can be removed, by a remove button.
    ///
    /// @param hb           Where to add it.
    /// @param messageId    The id of the attachment's message.
    /// @param attachmentId The attachment.
    /// @param description  What the table is, as it was attached.
    /// @param attachment   Its status, or null if it isn't known yet.
    /// @param removable    True if it can be removed, i.e. it hasn't been sent yet.
    static void chip(final HtmlBuilder hb,
                     final int messageId,
                     final int attachmentId,
                     final String description,
                     final AiChatAttachment attachment,
                     final boolean removable) {
        final boolean downloading = isDownloading(attachment);
        final boolean failed = attachment != null && attachment.getStatus() == AiAttachmentStatus.ERROR;
        final String state = downloading
                ? " ai-attachment-chip--downloading"
                : (failed
                        ? " ai-attachment-chip--error"
                        : "");
        final SvgImage icon = icon(description);
        hb.div(chip -> {
            chip.elem(button -> button.div(div -> div.appendTrustedString(icon.getSvg()),
                            Attribute.className("svg-image " + icon.getClassName() + " face")),
                    SafeHtmlUtils.fromSafeConstant("button"),
                    Attribute.className("ai-attachment-chip-icon icon-button"),
                    Attribute.title("View data"),
                    new Attribute("data-attachment-id", String.valueOf(attachmentId)),
                    new Attribute("data-attachment-desc", description));
            chip.span(label(description), Attribute.className("ai-attachment-chip-label"));
            if (downloading) {
                chip.span("", Attribute.className("ai-attachment-chip-spinner"));
            } else if (removable) {
                chip.elem(button -> button.div(div -> div.appendTrustedString(SvgImage.CLOSE.getSvg()),
                                Attribute.className("svg-image " + SvgImage.CLOSE.getClassName() + " face")),
                        SafeHtmlUtils.fromSafeConstant("button"),
                        Attribute.className("ai-attachment-chip-remove icon-button"),
                        Attribute.title("Remove"),
                        new Attribute(REMOVE_ATTRIBUTE, String.valueOf(messageId)));
            }
        }, Attribute.className("ai-attachment-chip" + state),
                new Attribute("id", chipId(attachmentId)),
                Attribute.title(tooltip(attachment)));
    }
}
