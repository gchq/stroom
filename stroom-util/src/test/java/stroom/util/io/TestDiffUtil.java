/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.util.io;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

class TestDiffUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestDiffUtil.class);

    @Test
    void testUnifiedDiff() {

        final String original = "" +
                "This is some text.\n" +
                "And so is this.\n" +
                "This line will be deleted.\n" +
                "This line will be modified a bit.";

        final String revised = "" +
                "This line will be added.\n" +
                "This is some text.\n" +
                "And so is this.\n" +
                "This line will be changed a bit.";

        final AtomicBoolean wasCalled = new AtomicBoolean(false);
        final boolean isDifferent = DiffUtil.unifiedDiff(
                original,
                revised,
                false, 100, diffLines -> {
                    LOGGER.info("\n{}", String.join("\n", diffLines));

                    assertLineState(diffLines, 3, "This line will be added.", DiffState.ADDED);
                    assertLineState(diffLines, 4, "This is some text.", DiffState.NO_CHANGE);
                    assertLineState(diffLines, 5, "And so is this.", DiffState.NO_CHANGE);
                    assertLineState(diffLines, 6, "This line will be deleted.", DiffState.DELETED);
                    assertLineState(diffLines, 7, "This line will be modified a bit.", DiffState.DELETED);
                    assertLineState(diffLines, 8, "This line will be changed a bit.", DiffState.ADDED);

                    wasCalled.set(true);
                }
        );

        Assertions.assertThat(isDifferent)
                .isTrue();

        Assertions.assertThat(wasCalled)
                .isTrue();
    }

    @Test
    void testUnifiedDiff_same() {

        final String original = "" +
                "This is some text.\n" +
                "And so is this.";

        final String revised = "" +
                "This is some text.\n" +
                "And so is this.";

        final AtomicBoolean wasCalled = new AtomicBoolean(false);
        final boolean isDifferent = DiffUtil.unifiedDiff(
                original,
                revised,
                false, 100, diffLines ->
                        wasCalled.set(true)
        );

        Assertions.assertThat(isDifferent)
                .isFalse();
        Assertions.assertThat(wasCalled)
                .isFalse();
    }


    private void assertLineState(final List<String> diffLines,
                                 final int lineNo,
                                 final String lineText,
                                 final DiffState expectedDiffState) {
        final String line = diffLines.get(lineNo);

        final String stateStr = line.substring(0, 1);
        final String text = line.substring(1);
        final DiffState diffState;
        if (stateStr.equals(" ")) {
            diffState = DiffState.NO_CHANGE;
        } else if (stateStr.equals("+")) {
            diffState = DiffState.ADDED;
        } else if (stateStr.equals("-")) {
            diffState = DiffState.DELETED;
        } else {
            throw new RuntimeException("Unknown value " + stateStr);
        }

        Assertions.assertThat(diffState)
                .isEqualTo(expectedDiffState);

        Assertions.assertThat(text)
                .isEqualTo(lineText);
    }

    private enum DiffState {
        ADDED,
        DELETED,
        NO_CHANGE
    }


}
