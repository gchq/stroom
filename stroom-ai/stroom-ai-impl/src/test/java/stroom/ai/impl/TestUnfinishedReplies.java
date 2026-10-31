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

package stroom.ai.impl;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TestUnfinishedReplies {

    @ParameterizedTest
    @ValueSource(strings = {
            // Seen in testing.
            "There are 779 pipelines total. I need to examine their structures to find which ones have tee (fork) "
            + "elements. Let me use the pipeline tool to look at their structures.",
            "Let me check the generated XSLT:",
            "The guard is in the mapping but not the XSLT.\n\nNow I'll rebuild the translation with the fix.",
            "Found it.\n\n- **Next, I need to** step the Connect events too.",
            "I’m going to reprocess the streams now.",
            "Okay, let me look at the Connect events…",
            "Time to step the sample again."
    })
    void aReplyThatOnlySaysWhatComesNextIsUnfinished(final String reply) {
        assertThat(UnfinishedReplies.announcesMoreWork(reply)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "The index now holds 1,000 events, none rejected.",
            "Let me know if you want the Connect events indexed too.",
            "I'll reprocess the streams once you confirm the template is committed.",
            "Shall I reprocess the streams now?",
            "Let me check the XSLT, or would you rather review it first?",
            "I'll do that if you'd like.",
            // An earlier sentence says what it would do, but the reply ends with the answer.
            "Let me sum up. Three pipelines fork to more than one translation."
    })
    void anAnswerQuestionOrOfferIsFinished(final String reply) {
        assertThat(UnfinishedReplies.announcesMoreWork(reply)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n\n"})
    void noReplyIsNotUnfinished(final String reply) {
        assertThat(UnfinishedReplies.announcesMoreWork(reply)).isFalse();
    }
}
