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
import stroom.ai.shared.AiAttachmentType;
import stroom.ai.shared.AiChatAttachment;
import stroom.docref.DocRef;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;

import com.google.gwt.safehtml.shared.SafeHtmlUtils;

/// How a table or document attached to a chat is shown: as a chip saying what it is, e.g. "Events - Logons", first
/// in a list above the input box while it is downloaded and until the next message is sent (when it can be removed),
/// then in the bubble of the message it was sent with. A document attached from the Explorer tree has nothing to
/// download, so its chip is ready at once, with its type's icon (a button that opens it) and its name.
final class AiAttachments {

    /// The attribute of a chip's remove button: the attachment's message id.
    static final String REMOVE_ATTRIBUTE = "data-remove-attachment-message-id";
    /// The attribute of the button that removes all the attachments listed.
    static final String REMOVE_ALL_ATTRIBUTE = "data-remove-all-attachments";
    /// The attributes of a document chip's icon button, which opens the document: its type and uuid.
    static final String OPEN_DOC_TYPE_ATTRIBUTE = "data-open-doc-type";
    static final String OPEN_DOC_UUID_ATTRIBUTE = "data-open-doc-uuid";

    /// The attribute of the attached tab chip's remove button, which removes the tab from the chat.
    static final String REMOVE_TAB_ATTRIBUTE = "data-remove-attached-tab";
    /// The attribute of the button that attaches the tab the user is on to the chat.
    static final String ATTACH_TAB_ATTRIBUTE = "data-attach-tab";

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
    /// @return True if it is a document attached from the Explorer tree, as a reference.
    static boolean isDocument(final AiChatAttachment attachment) {
        return attachment != null
               && attachment.getAttachmentType() == AiAttachmentType.DOCUMENT
               && attachment.getDocRef() != null;
    }

    /// @param attachment  A document attached from the Explorer tree.
    /// @param description What it is, as it was attached, e.g. "Dictionary 'Malicious IPs'".
    /// @return What its chip says: the document's name.
    static String documentLabel(final AiChatAttachment attachment, final String description) {
        final DocRef docRef = attachment.getDocRef();
        return NullSafe.isNonBlankString(docRef.getName())
                ? docRef.getName()
                : NullSafe.nonBlankStringElse(description, docRef.getUuid());
    }

    /// @param displayType The document's type, as Stroom shows it, e.g. "Dictionary".
    /// @param path        The path of the folder holding it, e.g. "/Security/Lookups", or null if not known.
    /// @return The chip's tooltip, e.g. "Dictionary in /Security/Lookups".
    static String documentTooltip(final String displayType, final String path) {
        final String type = NullSafe.nonBlankStringElse(displayType, "Document");
        return NullSafe.isNonBlankString(path)
                ? type + " in " + path
                : type;
    }

    /// @param attachment The attachment's status, or null if it isn't known yet.
    /// @return True while it is being downloaded.
    static boolean isDownloading(final AiChatAttachment attachment) {
        return attachment == null || attachment.getStatus() == AiAttachmentStatus.PENDING
               || attachment.getStatus() == AiAttachmentStatus.DOWNLOADING;
    }

    /// Adds the button that removes all the attachments listed, after asking.
    ///
    /// @param hb      Where to add it.
    /// @param enabled False while any of them is still downloading, as each can only be removed once it has.
    static void removeAllButton(final HtmlBuilder hb, final boolean enabled) {
        hb.elem(button -> button.div(div -> div.appendTrustedString(SvgImage.CLEAR.getSvg()),
                        Attribute.className("svg-image " + SvgImage.CLEAR.getClassName() + " face")),
                SafeHtmlUtils.fromSafeConstant("button"),
                Attribute.className("ai-attachment-remove-all icon-button"),
                Attribute.title(enabled
                        ? "Delete all attachments"
                        : "Delete all attachments (once they have downloaded)"),
                new Attribute(REMOVE_ALL_ATTRIBUTE, "true"),
                enabled
                        ? new Attribute("type", "button")
                        : new Attribute("disabled", "disabled"));
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
        chip(hb, messageId, attachmentId, description, attachment, removable, null, null);
    }

    /// Adds an attachment's chip: for a table, its icon (a button to view its data), what it is, and while it is
    /// downloaded a spinning circle, which is replaced, if it can be removed, by a remove button; for a document,
    /// its type's icon (a button to open it), its name and, if it can be removed, a remove button.
    ///
    /// @param hb           Where to add it.
    /// @param messageId    The id of the attachment's message.
    /// @param attachmentId The attachment.
    /// @param description  What it is, as it was attached.
    /// @param attachment   Its status, or null if it isn't known yet.
    /// @param removable    True if it can be removed, i.e. it hasn't been sent yet.
    /// @param docIcon      For a document, its type's icon, or null if not known.
    /// @param docType      For a document, its type as Stroom shows it, or null if not known.
    static void chip(final HtmlBuilder hb,
                     final int messageId,
                     final int attachmentId,
                     final String description,
                     final AiChatAttachment attachment,
                     final boolean removable,
                     final SvgImage docIcon,
                     final String docType) {
        if (isDocument(attachment)) {
            documentChip(hb, messageId, attachmentId, description, attachment, removable, docIcon, docType);
            return;
        }
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

    // A document's chip: ready at once, as there is nothing to download.
    private static void documentChip(final HtmlBuilder hb,
                                     final int messageId,
                                     final int attachmentId,
                                     final String description,
                                     final AiChatAttachment attachment,
                                     final boolean removable,
                                     final SvgImage docIcon,
                                     final String docType) {
        final DocRef docRef = attachment.getDocRef();
        final SvgImage icon = docIcon != null
                ? docIcon
                : SvgImage.FILE;
        hb.div(chip -> {
            chip.elem(button -> button.div(div -> div.appendTrustedString(icon.getSvg()),
                            Attribute.className("svg-image " + icon.getClassName() + " face")),
                    SafeHtmlUtils.fromSafeConstant("button"),
                    Attribute.className("ai-attachment-chip-icon icon-button"),
                    Attribute.title("Open"),
                    new Attribute(OPEN_DOC_TYPE_ATTRIBUTE, NullSafe.string(docRef.getType())),
                    new Attribute(OPEN_DOC_UUID_ATTRIBUTE, NullSafe.string(docRef.getUuid())));
            chip.span(documentLabel(attachment, description), Attribute.className("ai-attachment-chip-label"));
            if (removable) {
                chip.elem(button -> button.div(div -> div.appendTrustedString(SvgImage.CLOSE.getSvg()),
                                Attribute.className("svg-image " + SvgImage.CLOSE.getClassName() + " face")),
                        SafeHtmlUtils.fromSafeConstant("button"),
                        Attribute.className("ai-attachment-chip-remove icon-button"),
                        Attribute.title("Remove"),
                        new Attribute(REMOVE_ATTRIBUTE, String.valueOf(messageId)));
            }
        }, Attribute.className("ai-attachment-chip"),
                new Attribute("id", chipId(attachmentId)),
                Attribute.title(documentTooltip(NullSafe.nonBlankStringElse(docType, docRef.getType()),
                        attachment.getDocPath())));
    }

    /// Adds the chip of the tab attached to the chat, which is shared with each question: its icon, what it says
    /// (e.g. "Tab: Event translation (unsaved)") and a button that removes it from the chat.
    ///
    /// @param hb      Where to add it.
    /// @param icon    The tab's icon, or null.
    /// @param text    What the chip says.
    /// @param tooltip The chip's tooltip.
    static void tabChip(final HtmlBuilder hb, final SvgImage icon, final String text, final String tooltip) {
        final SvgImage image = icon != null
                ? icon
                : SvgImage.FILE;
        hb.div(chip -> {
            // Its icon in a box, as a document chip's is, so the icon is sized the same; it isn't a button.
            chip.div(box -> box.div(div -> div.appendTrustedString(image.getSvg()),
                            Attribute.className("svg-image " + image.getClassName() + " face")),
                    Attribute.className("ai-attachment-chip-icon"));
            chip.span(text, Attribute.className("ai-attachment-chip-label"));
            chip.elem(button -> button.div(div -> div.appendTrustedString(SvgImage.CLOSE.getSvg()),
                            Attribute.className("svg-image " + SvgImage.CLOSE.getClassName() + " face")),
                    SafeHtmlUtils.fromSafeConstant("button"),
                    Attribute.className("ai-attachment-chip-remove icon-button"),
                    Attribute.title("Remove this tab from the chat"),
                    new Attribute(REMOVE_TAB_ATTRIBUTE, "true"));
        }, Attribute.className("ai-attachment-chip ai-attachment-chip--tab"), Attribute.title(tooltip));
    }

    /// Adds a button that attaches the tab the user is on to the chat: a plus, the tab's icon and what it says
    /// (e.g. "Event translation").
    ///
    /// @param hb      Where to add it.
    /// @param icon    The tab's icon, or null.
    /// @param text    What the button says.
    /// @param tooltip The button's tooltip.
    static void attachTabChip(final HtmlBuilder hb, final SvgImage icon, final String text, final String tooltip) {
        final SvgImage image = icon != null
                ? icon
                : SvgImage.FILE;
        hb.elem(button -> {
            button.div(div -> div.appendTrustedString(SvgImage.ADD.getSvg()),
                    Attribute.className("svg-image " + SvgImage.ADD.getClassName() + " face ai-attach-tab-plus"));
            button.div(box -> box.div(div -> div.appendTrustedString(image.getSvg()),
                            Attribute.className("svg-image " + image.getClassName() + " face")),
                    Attribute.className("ai-attachment-chip-icon"));
            button.span(text, Attribute.className("ai-attachment-chip-label"));
        }, SafeHtmlUtils.fromSafeConstant("button"),
                Attribute.className("allow-focus ai-attachment-chip ai-attachment-chip--attach-tab"),
                Attribute.title(tooltip),
                new Attribute(ATTACH_TAB_ATTRIBUTE, "true"));
    }
}
