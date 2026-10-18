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
import stroom.widget.util.client.HtmlBuilder;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestAiAttachments {

    @Test
    void aChipSaysWhichDashboardAndTableItIs() {
        assertThat(AiAttachments.label("Dashboard 'Events' -> Table 'Logons'")).isEqualTo("Events - Logons");
        assertThat(AiAttachments.label("Dashboard 'Events'")).isEqualTo("Events");
        assertThat(AiAttachments.label("Query 'Failed logons'")).isEqualTo("Failed logons");
        // Anything else is shown as it is.
        assertThat(AiAttachments.label("Query table")).isEqualTo("Query table");
        assertThat(AiAttachments.label(null)).isEqualTo("Table");
    }

    @Test
    void itsIconIsOfWhereItCameFrom() {
        assertThat(AiAttachments.icon("Dashboard 'Events' -> Table 'Logons'")).isEqualTo(SvgImage.DOCUMENT_DASHBOARD);
        assertThat(AiAttachments.icon("Query 'Failed logons'")).isEqualTo(SvgImage.DOCUMENT_QUERY);
        assertThat(AiAttachments.icon("Some table")).isEqualTo(SvgImage.TABLE);
    }

    @Test
    void itsTooltipHasTheRowCountOnceDownloaded() {
        assertThat(AiAttachments.tooltip(null)).isEqualTo("Downloading...");
        assertThat(AiAttachments.tooltip(attachment(AiAttachmentStatus.DOWNLOADING, null, false, null)))
                .isEqualTo("Downloading...");
        assertThat(AiAttachments.tooltip(attachment(AiAttachmentStatus.READY, 1234, false, null)))
                .isEqualTo("1234 rows");
        assertThat(AiAttachments.tooltip(attachment(AiAttachmentStatus.READY, 1, true, null)))
                .isEqualTo("1 row (cut to the row limit)");
        assertThat(AiAttachments.tooltip(attachment(AiAttachmentStatus.ERROR, null, false, "Timed out")))
                .isEqualTo("Couldn't be downloaded: Timed out");
        assertThat(AiAttachments.isDownloading(attachment(AiAttachmentStatus.PENDING, null, false, null))).isTrue();
        assertThat(AiAttachments.isDownloading(attachment(AiAttachmentStatus.READY, 2, false, null))).isFalse();
    }

    @Test
    void aDocumentsChipHasItsNameAndItsTypeAndPathAsItsTooltip() {
        final AiChatAttachment document = document(new DocRef("Dictionary", "dict-uuid", "Malicious IPs"));

        assertThat(AiAttachments.isDocument(document)).isTrue();
        assertThat(AiAttachments.isDocument(attachment(AiAttachmentStatus.READY, 2, false, null))).isFalse();
        assertThat(AiAttachments.isDocument(null)).isFalse();
        assertThat(AiAttachments.documentLabel(document, "Dictionary 'Malicious IPs'")).isEqualTo("Malicious IPs");
        assertThat(AiAttachments.documentLabel(document(new DocRef("Dictionary", "dict-uuid")), "Dictionary 'X'"))
                .isEqualTo("Dictionary 'X'");
        assertThat(AiAttachments.documentTooltip("Dictionary", "/Security/Lookups"))
                .isEqualTo("Dictionary in /Security/Lookups");
        assertThat(AiAttachments.documentTooltip(null, null)).isEqualTo("Document");
        // It is ready at once, as there is nothing to download.
        assertThat(AiAttachments.isDownloading(document)).isFalse();
    }

    @Test
    void aDocumentsChipOpensItAndCanBeRemovedUntilSent() {
        final AiChatAttachment document = document(new DocRef("Dictionary", "dict-uuid", "Malicious IPs"));

        final String removable = chipHtml(document, true);
        assertThat(removable)
                .contains(AiAttachments.OPEN_DOC_TYPE_ATTRIBUTE + "=\"Dictionary\"")
                .contains(AiAttachments.OPEN_DOC_UUID_ATTRIBUTE + "=\"dict-uuid\"")
                .contains("Malicious IPs")
                .contains("Dictionary in /Security/Lookups")
                .contains(AiAttachments.REMOVE_ATTRIBUTE)
                .doesNotContain("ai-attachment-chip-spinner")
                .doesNotContain("data-attachment-id");
        assertThat(chipHtml(document, false)).doesNotContain(AiAttachments.REMOVE_ATTRIBUTE);
    }

    @Test
    void theActiveTabsChipShowsTheTabsIconSizedAsADocumentChipsIs() {
        final HtmlBuilder hb = new HtmlBuilder();
        AiAttachments.tabChip(hb, SvgImage.DOCUMENT_XSLT, "Tab: Event translation", "Shared");
        final String html = hb.toSafeHtml().asString();

        // The icon is inside its box, which the chip's styles size, as a document chip's icon is.
        assertThat(html)
                .contains("<div class=\"ai-attachment-chip-icon\"><div class=\"svg-image "
                          + SvgImage.DOCUMENT_XSLT.getClassName() + " face\">")
                .contains(SvgImage.DOCUMENT_XSLT.getSvg())
                .contains("Tab: Event translation");
        // Without an icon, a file's.
        final HtmlBuilder plain = new HtmlBuilder();
        AiAttachments.tabChip(plain, null, "Tab: X", "Shared");
        assertThat(plain.toSafeHtml().asString()).contains(SvgImage.FILE.getClassName());
    }

    private static String chipHtml(final AiChatAttachment attachment, final boolean removable) {
        final HtmlBuilder hb = new HtmlBuilder();
        AiAttachments.chip(hb, 7, attachment.getId(), "Dictionary 'Malicious IPs'", attachment, removable,
                SvgImage.DOCUMENT_DICTIONARY, "Dictionary");
        return hb.toSafeHtml().asString();
    }

    private static AiChatAttachment document(final DocRef docRef) {
        return new AiChatAttachment(3, 2, 0L, 0L, AiAttachmentStatus.READY, AiAttachmentType.DOCUMENT,
                "Dictionary 'Malicious IPs'", null, false, null, docRef, "/Security/Lookups");
    }

    private static AiChatAttachment attachment(final AiAttachmentStatus status,
                                               final Integer rows,
                                               final boolean truncated,
                                               final String error) {
        return new AiChatAttachment(1, 2, 0L, 0L, status, AiAttachmentType.DASHBOARD, "Dashboard 'A'", rows,
                truncated, error);
    }
}
