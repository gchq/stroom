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

package stroom.ai.impl.workspace;

/// Wraps text so that anything reading it, such as a regular expression matcher, checks the current tool call's
/// time limit as it goes (see [ToolDeadline]). A pattern that backtracks catastrophically (ReDoS) reads the same
/// characters over and over, so it is stopped at the limit rather than running for ever.
public final class DeadlineCharSequence implements CharSequence {

    private static final int CHECK_EVERY = 4_096;

    private final CharSequence text;
    private int reads;

    public DeadlineCharSequence(final CharSequence text) {
        this.text = text;
    }

    @Override
    public int length() {
        return text.length();
    }

    @Override
    public char charAt(final int index) {
        if (++reads >= CHECK_EVERY) {
            reads = 0;
            ToolDeadline.check();
        }
        return text.charAt(index);
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
        return new DeadlineCharSequence(text.subSequence(start, end));
    }

    @Override
    public String toString() {
        return text.toString();
    }
}
