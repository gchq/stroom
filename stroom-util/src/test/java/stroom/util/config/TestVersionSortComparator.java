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

package stroom.util.config;


import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestVersionSortComparator {

    @Test
    void testSplit() {
        Assertions.assertThat(Arrays.asList(VersionSortComparator.parts("app-3.1.1"))).isEqualTo(Arrays.asList("app",
                "3",
                "1",
                "1"));

        assertThat(Arrays.asList(VersionSortComparator.parts("app-3.1.1-abc"))).isEqualTo(Arrays.asList("app",
                "3",
                "1",
                "1",
                "abc"));
    }

    @Test
    void testCompare1() {
        doTest(Arrays.asList("app-3.0.0", "app-3.0.1", "app-3.0.1-beta-2"),
                Arrays.asList("app-3.0.0", "app-3.0.1-beta-2", "app-3.0.1"));
    }

    @Test
    void testCompare2() {
        doTest(Arrays.asList("app-3.0.7", "app-3.0.0", "app-3.0.4"),
                Arrays.asList("app-3.0.0", "app-3.0.4", "app-3.0.7"));
    }

    @Test
    void testCompare3() {
        doTest(Arrays.asList("app-3.0.77", "app-3.0.0", "app-3.0.9"),
                Arrays.asList("app-3.0.0", "app-3.0.9", "app-3.0.77"));
    }

    public void doTest(final List<String> testCase, final List<String> expectedList) {
        final List<String> sortList = new ArrayList<>();
        sortList.addAll(testCase);
        Collections.sort(sortList, new VersionSortComparator());

        assertThat(sortList).isEqualTo(expectedList);

    }
}
