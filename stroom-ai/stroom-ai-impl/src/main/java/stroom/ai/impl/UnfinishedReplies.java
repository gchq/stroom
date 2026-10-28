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

import stroom.util.shared.NullSafe;

import java.util.Locale;
import java.util.regex.Pattern;

/// Tells a reply that only says what the model will do next from an answer.
///
/// Some models end a step with "Let me use the pipeline tool to look at their structures." and no tool call. With
/// no call to make, the run took that as the answer and ended, with nothing done (seen in testing: twice in one
/// session, once right after the model had worked out the fix it was about to make).
final class UnfinishedReplies {

    // How much of the end of a reply is read for its last sentence.
    private static final int TAIL_CHARS = 400;
    // Where one sentence ends and the next starts, or a line break.
    private static final Pattern SENTENCE_BREAK = Pattern.compile("(?<=[.!?:…])\\s+|\\n+");
    // Markdown a sentence may start with: a list item, a quote, a heading or emphasis.
    private static final Pattern LEADING_MARKUP = Pattern.compile("^[\\s*_#>`-]+");
    // A sentence that says what the model is about to do, perhaps after a word like "Now" or "Next".
    private static final Pattern ANNOUNCES = Pattern.compile(
            "^(?:(?:ok|okay|now|next|so|first|then|right|good|great)[,:]?\\s+)*"
            + "(?:let me|let's|i'll|i will|i'm going to|i am going to|i need to|i'm now going to|i now need to"
            + "|i should now|i'll now|i will now|time to)\\b.*");
    // Words that make it an offer or a question for the user, which ends a turn properly.
    private static final Pattern FOR_THE_USER = Pattern.compile(
            "\\b(?:if you|once you|when you|let me know|would you|shall i|should i|do you|you can|you could"
            + "|your go-ahead|your approval|you confirm|you'd like|you want)\\b");

    private UnfinishedReplies() {
        // Static methods only.
    }

    /// @param reply A reply the model gave without calling a tool.
    /// @return True if it ends by saying what the model will do next, rather than answering, asking the user
    /// something or offering them something.
    static boolean announcesMoreWork(final String reply) {
        if (NullSafe.isBlankString(reply)) {
            return false;
        }
        final String text = reply.strip();
        final String tail = text.substring(Math.max(0, text.length() - TAIL_CHARS))
                .replace('’', '\'')
                .toLowerCase(Locale.ROOT);
        final String[] sentences = SENTENCE_BREAK.split(tail);
        String last = "";
        for (int i = sentences.length - 1; i >= 0 && last.isEmpty(); i--) {
            last = LEADING_MARKUP.matcher(sentences[i]).replaceFirst("").strip();
        }
        return !last.endsWith("?")
               && ANNOUNCES.matcher(last).matches()
               && !FOR_THE_USER.matcher(last).find();
    }
}
