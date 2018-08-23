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

package stroom.statistics.shared.common;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class TestCustomRollUpMask {
    @Test
    public void testIsTagRolledUp() {
        final CustomRollUpMask mask = new CustomRollUpMask(Arrays.asList(3, 1, 0));

        Assert.assertTrue(mask.isTagRolledUp(3));
        Assert.assertFalse(mask.isTagRolledUp(2));
        Assert.assertTrue(mask.isTagRolledUp(1));
        Assert.assertTrue(mask.isTagRolledUp(0));
    }

    @Test
    public void testSetRollUpState() {
        final CustomRollUpMask mask = new CustomRollUpMask(Arrays.asList(3, 1, 0));

        Assert.assertFalse(mask.isTagRolledUp(2));
        Assert.assertTrue(mask.isTagRolledUp(3));

        mask.setRollUpState(2, false);
        Assert.assertFalse(mask.isTagRolledUp(2));

        mask.setRollUpState(2, true);
        Assert.assertTrue(mask.isTagRolledUp(2));

        mask.setRollUpState(2, false);
        Assert.assertFalse(mask.isTagRolledUp(2));

        mask.setRollUpState(3, true);
        Assert.assertTrue(mask.isTagRolledUp(3));

        mask.setRollUpState(3, false);
        Assert.assertFalse(mask.isTagRolledUp(3));
    }
}
