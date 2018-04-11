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

package stroom.util.testshared;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import stroom.util.shared.TaskIdImpl;
import stroom.util.test.StroomJUnit4ClassRunner;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestTaskIdImpl {
    @Test
    public void testSimple() {
        final TaskIdImpl p1 = new TaskIdImpl("P1", null);
        final TaskIdImpl p2 = new TaskIdImpl("P2", null);
        final TaskIdImpl c1 = new TaskIdImpl("C1", p1);
        final TaskIdImpl c2 = new TaskIdImpl("C2", p2);
        final TaskIdImpl gc1 = new TaskIdImpl("GC1", c1);
        final TaskIdImpl gc2 = new TaskIdImpl("GC2", c2);

        Assert.assertTrue(gc1.isOrHasAncestor(new TaskIdImpl("P1", null)));
        Assert.assertFalse(gc2.isOrHasAncestor(new TaskIdImpl("P1", null)));
    }

}
