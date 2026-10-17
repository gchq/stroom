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

import stroom.ai.shared.AiChatRunStatus;
import stroom.ai.shared.AiChatStatus;
import stroom.svg.shared.SvgImage;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestAiChatTexts {

    @Test
    void theWorkingLineShowsWhatTheModelSaid() {
        // The Working line has the first line as plain text, cut short, with the tool's name.
        assertThat(AiChatTexts.isShownAsProgress(
                "Confirmed: raw data is <tweet> fragments with no root element, so this needs an XML… "
                + "(build_data_splitter)",
                "**Confirmed**: raw data is `<tweet>` fragments with no root element, so this needs an XML "
                + "fragment wrapper.\n\nLet me build it.")).isTrue();
        assertThat(AiChatTexts.isShownAsProgress("Checking the feed now. (describe_feed)",
                "Checking [the feed](?action=open-doc&docType=Feed) now.")).isTrue();
        // A short remark is shown whole.
        assertThat(AiChatTexts.isShownAsProgress("Now writing (write_documentation)", "Now writing.")).isTrue();
    }

    @Test
    void theWorkingLineHasMovedOn() {
        assertThat(AiChatTexts.isShownAsProgress("Running read_stream — stream_id=1465",
                "Now writing the documentation.")).isFalse();
        assertThat(AiChatTexts.isShownAsProgress("Working...", "Now writing the documentation.")).isFalse();
        assertThat(AiChatTexts.isShownAsProgress("Now writing (write_documentation)", null)).isFalse();
        assertThat(AiChatTexts.isShownAsProgress(null, "Now writing.")).isFalse();
    }

    @Test
    void theMostImportantStateIsShown() {
        // Needing the user beats being unread.
        assertThat(AiChatTexts.statusIcon(status(AiChatRunStatus.WAITING_FOR_USER, true, false)))
                .isEqualTo(SvgImage.ALERT);
        assertThat(AiChatTexts.statusNote(status(AiChatRunStatus.WAITING_FOR_USER, true, false)))
                .isEqualTo("needs you");
        assertThat(AiChatTexts.statusIcon(status(AiChatRunStatus.RUNNING, true, false)))
                .isEqualTo(SvgImage.SPINNER_SPINNING);
        assertThat(AiChatTexts.statusNote(status(AiChatRunStatus.QUEUED, false, false))).isEqualTo("queued");
        assertThat(AiChatTexts.statusNote(status(AiChatRunStatus.RUNNING, false, false))).isEqualTo("working");
        assertThat(AiChatTexts.statusIcon(status(AiChatRunStatus.FAILED, true, false))).isEqualTo(SvgImage.ERROR);
        assertThat(AiChatTexts.statusIcon(status(AiChatRunStatus.STOPPED, false, false))).isEqualTo(SvgImage.PAUSE);
        assertThat(AiChatTexts.statusNote(status(AiChatRunStatus.INTERRUPTED, false, false)))
                .isEqualTo("interrupted");
        assertThat(AiChatTexts.statusIcon(status(AiChatRunStatus.DONE, true, false))).isEqualTo(SvgImage.DOT);
        assertThat(AiChatTexts.statusNote(status(AiChatRunStatus.DONE, true, false))).isEqualTo("unread");
    }

    @Test
    void eachStateHasTheColourOfItsBadge() {
        assertThat(AiChatTexts.statusColour(status(AiChatRunStatus.WAITING_FOR_USER, true, false)))
                .isEqualTo("needs-you");
        assertThat(AiChatTexts.statusColour(status(AiChatRunStatus.RUNNING, true, false))).isEqualTo("busy");
        assertThat(AiChatTexts.statusColour(status(AiChatRunStatus.FAILED, true, false))).isEqualTo("failed");
        assertThat(AiChatTexts.statusColour(status(AiChatRunStatus.STOPPED, false, false))).isEqualTo("paused");
        assertThat(AiChatTexts.statusColour(status(AiChatRunStatus.INTERRUPTED, false, false)))
                .isEqualTo("paused");
        assertThat(AiChatTexts.statusColour(status(AiChatRunStatus.DONE, true, false))).isEqualTo("unread");
        assertThat(AiChatTexts.statusColour(status(AiChatRunStatus.DONE, false, false))).isNull();
    }

    @Test
    void aQuietChatHasNoNoteUnlessItApprovesEverything() {
        assertThat(AiChatTexts.statusIcon(status(null, false, false))).isEqualTo(SvgImage.AI);
        assertThat(AiChatTexts.statusNote(status(AiChatRunStatus.DONE, false, false))).isEmpty();
        assertThat(AiChatTexts.statusNote(status(null, false, true))).isEqualTo("approving everything");
    }

    @Test
    void formStatuses() {
        assertThat(AiChatTexts.formStatus("ACCEPTED")).isEqualTo("You answered it.");
        assertThat(AiChatTexts.formStatus("EXPIRED")).isEqualTo("Not answered in time.");
        assertThat(AiChatTexts.formStatus("SOMETHING_NEW")).isEqualTo("SOMETHING_NEW");
        assertThat(AiChatTexts.formStatus(null)).isEmpty();
    }

    @Test
    void deleteQuestions() {
        assertThat(AiChatTexts.deleteChatQuestion("Firewall errors", false))
                .startsWith("Delete \u201cFirewall errors\u201d?")
                .doesNotContain("stopped");
        assertThat(AiChatTexts.deleteChatQuestion(" ", true))
                .startsWith("Delete this chat?")
                .endsWith("It is still working, so it will be stopped first.");
        assertThat(AiChatTexts.deleteAllChatsQuestion(1, false))
                .startsWith("Delete all your chat?")
                .contains("Your own memory is kept.")
                .doesNotContain("stopped");
        assertThat(AiChatTexts.deleteAllChatsQuestion(12, true))
                .startsWith("Delete all 12 of your chats?")
                .contains("Chats that are working are stopped first.");
    }

    @Test
    void clearQuestion() {
        assertThat(AiChatTexts.clearChatQuestion(false))
                .startsWith("Clear this chat?")
                .contains("as is your own memory")
                .doesNotContain("stopped");
        assertThat(AiChatTexts.clearChatQuestion(true)).endsWith("It is still working, so it will be stopped first.");
    }

    @Test
    void editWarnings() {
        assertThat(AiChatTexts.editWarning(0, false)).isEqualTo("Editing will ask this question again.");
        assertThat(AiChatTexts.editWarning(1, false))
                .isEqualTo("Editing will remove 1 later message and ask this question again.");
        assertThat(AiChatTexts.editWarning(3, true))
                .startsWith("Editing will remove 3 later messages")
                .endsWith("will not be undone.");
    }

    private static AiChatStatus status(final AiChatRunStatus runStatus,
                                       final boolean unread,
                                       final boolean approveEverything) {
        return new AiChatStatus(1, "t", 0L, runStatus, unread, approveEverything, 0L, null);
    }

    @Test
    void eachToolClassificationIsExplained() {
        for (final String risk : List.of("read-only", "can modify", "destructive", "reaches outside Stroom",
                "untrusted server", "after reading data")) {
            assertThat(AiChatTexts.riskExplanation(risk)).as(risk).isNotBlank().contains("\n");
        }
        assertThat(AiChatTexts.riskExplanation("can modify")).contains("feeds, pipelines");
        assertThat(AiChatTexts.riskExplanation("something else")).isNull();
    }


    @Test
    void theContextPillIsGreyThenYellowThenRed() {
        assertThat(AiChatTexts.contextLevel(0)).isNull();
        assertThat(AiChatTexts.contextLevel(74)).isNull();
        assertThat(AiChatTexts.contextLevel(75)).isEqualTo("warn");
        assertThat(AiChatTexts.contextLevel(89)).isEqualTo("warn");
        assertThat(AiChatTexts.contextLevel(90)).isEqualTo("over");
        assertThat(AiChatTexts.contextLevel(120)).isEqualTo("over");
    }


    @Test
    void theTrustIconIsExplained() {
        assertThat(AiChatTexts.trustExplanation(true)).startsWith("Trusted server.\n");
        assertThat(AiChatTexts.trustExplanation(false)).startsWith("Untrusted server.\n").contains("asks before");
    }

}
