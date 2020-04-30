/*
 * Copyright 2016 Crown Copyright
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

package stroom.util.shared;


import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestSelection {
    @Test
    void testSimple() {
        final Selection<Integer> testCase = new Selection<>();

//        assertThat(testCase.isConstrained()).isFalse();
        assertThat(testCase.isMatch(1)).isTrue();

//        assertThat(testCase.isConstrained()).isTrue();
        assertThat(testCase.isMatch(1)).isFalse();
        assertThat(testCase.isMatch(null)).isTrue();

        testCase.add(1);
        assertThat(testCase.isMatch(1)).isTrue();
    }

    @Test
    void testFlags() {
        final Selection<Long> totalFolderIdSet = new Selection<>();
        totalFolderIdSet.setMatchAll(false);

//        assertThat(totalFolderIdSet.isConstrained()).isTrue();
        assertThat(totalFolderIdSet.isMatchNothing()).isTrue();

    }

    @Test
    void testNullMatches() {
        final Selection<Long> totalFolderIdSet = new Selection<>();
        totalFolderIdSet.add(1L);
        assertThat(totalFolderIdSet.isMatch((Long) null)).isFalse();
        assertThat(totalFolderIdSet.isMatch((Long) null)).isTrue();
    }
}
