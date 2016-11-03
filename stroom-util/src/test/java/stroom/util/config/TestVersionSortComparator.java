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

package stroom.util.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class TestVersionSortComparator {
    @Test
    public void testSplit() {
        Assert.assertEquals(Arrays.asList("app", "3", "1", "1"),
                Arrays.asList(VersionSortComparator.parts("app-3.1.1")));

        Assert.assertEquals(Arrays.asList("app", "3", "1", "1", "abc"),
                Arrays.asList(VersionSortComparator.parts("app-3.1.1-abc")));
    }

    @Test
    public void testCompare1() {
        doTest(Arrays.asList("app-3.0.0", "app-3.0.1", "app-3.0.1-beta-2"),
                Arrays.asList("app-3.0.0", "app-3.0.1-beta-2", "app-3.0.1"));
    }

    @Test
    public void testCompare2() {
        doTest(Arrays.asList("app-3.0.7", "app-3.0.0", "app-3.0.4"),
                Arrays.asList("app-3.0.0", "app-3.0.4", "app-3.0.7"));
    }

    @Test
    public void testCompare3() {
        doTest(Arrays.asList("app-3.0.77", "app-3.0.0", "app-3.0.9"),
                Arrays.asList("app-3.0.0", "app-3.0.9", "app-3.0.77"));
    }

    public void doTest(List<String> testCase, List<String> expectedList) {
        List<String> sortList = new ArrayList<String>();
        sortList.addAll(testCase);
        Collections.sort(sortList, new VersionSortComparator());

        Assert.assertEquals(expectedList, sortList);

    }
}
