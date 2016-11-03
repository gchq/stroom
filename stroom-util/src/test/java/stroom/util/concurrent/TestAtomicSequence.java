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

package stroom.util.concurrent;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import stroom.util.test.StroomJUnit4ClassRunner;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestAtomicSequence {
    @Test
    public void testSimple() {
        AtomicSequence atomicSequence = new AtomicSequence(3);
        Assert.assertEquals(0, atomicSequence.next());
        Assert.assertEquals(1, atomicSequence.next());
        Assert.assertEquals(2, atomicSequence.next());
        Assert.assertEquals(0, atomicSequence.next());
    }

    @Test
    public void testCallLimit1() {
        AtomicSequence atomicSequence = new AtomicSequence(1);
        Assert.assertEquals(0, atomicSequence.next());
        Assert.assertEquals(0, atomicSequence.next());
        Assert.assertEquals(0, atomicSequence.next());
        Assert.assertEquals(0, atomicSequence.next());
        Assert.assertEquals(0, atomicSequence.next());
    }

    @Test
    public void testCallLimit2() {
        AtomicSequence atomicSequence = new AtomicSequence();
        Assert.assertEquals(0, atomicSequence.next(2));
        Assert.assertEquals(1, atomicSequence.next(2));
        Assert.assertEquals(0, atomicSequence.next(2));
        Assert.assertEquals(1, atomicSequence.next(2));
        Assert.assertEquals(0, atomicSequence.next(2));
    }

    @Test
    public void testCallLimit3() {
        AtomicSequence atomicSequence = new AtomicSequence(3);
        Assert.assertEquals(0, atomicSequence.next());
        Assert.assertEquals(1, atomicSequence.next());
        Assert.assertEquals(2, atomicSequence.next());
        Assert.assertEquals(0, atomicSequence.next());
        Assert.assertEquals(1, atomicSequence.next());
        Assert.assertEquals(2, atomicSequence.next());
        Assert.assertEquals(0, atomicSequence.next());
        Assert.assertEquals(1, atomicSequence.next());
        Assert.assertEquals(2, atomicSequence.next());
    }
}
