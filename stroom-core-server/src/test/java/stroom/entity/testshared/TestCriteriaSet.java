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

package stroom.entity.testshared;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import stroom.entity.shared.CriteriaSet;
import stroom.entity.shared.EntityIdSet;
import stroom.entity.shared.Folder;
import stroom.util.test.StroomUnitTest;
import stroom.util.test.StroomJUnit4ClassRunner;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestCriteriaSet extends StroomUnitTest {
    @Test
    public void testSimple() {
        final CriteriaSet<Integer> testCase = new CriteriaSet<>();

        Assert.assertFalse(testCase.isConstrained());
        Assert.assertTrue(testCase.isMatch(1));

        testCase.setMatchNull(true);

        Assert.assertTrue(testCase.isConstrained());
        Assert.assertFalse(testCase.isMatch(1));
        Assert.assertTrue(testCase.isMatch(null));

        testCase.add(1);
        Assert.assertTrue(testCase.isMatch(1));

    }

    @Test
    public void testFlags() {
        final EntityIdSet<Folder> totalFolderIdSet = new EntityIdSet<>();
        totalFolderIdSet.setMatchAll(false);

        Assert.assertTrue(totalFolderIdSet.isConstrained());
        Assert.assertTrue(totalFolderIdSet.isMatchNothing());

    }

    @Test
    public void testNullMatchs() {
        final EntityIdSet<Folder> totalFolderIdSet = new EntityIdSet<>();
        totalFolderIdSet.add(1L);
        Assert.assertFalse(totalFolderIdSet.isMatch((Long) null));
        totalFolderIdSet.setMatchNull(Boolean.TRUE);
        Assert.assertTrue(totalFolderIdSet.isMatch((Long) null));
    }

}
