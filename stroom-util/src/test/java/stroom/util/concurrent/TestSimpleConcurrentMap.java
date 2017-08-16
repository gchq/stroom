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

import java.util.concurrent.atomic.AtomicInteger;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestSimpleConcurrentMap {
    @Test
    public void testSimple() {
        ExampleSimpleConcurrentMap test = new ExampleSimpleConcurrentMap();

        Assert.assertEquals(0, test.get("TEST").get());
        Assert.assertEquals(1, test.get("TEST").incrementAndGet());
        Assert.assertEquals(2, test.get("TEST").incrementAndGet());

        Assert.assertEquals(1, test.keySet().size());
        Assert.assertEquals(1, test.get("TEST1").incrementAndGet());
        Assert.assertEquals(2, test.keySet().size());
    }

    private static class ExampleSimpleConcurrentMap extends SimpleConcurrentMap<String, AtomicInteger> {
        @Override
        protected AtomicInteger initialValue(final String key) {
            return new AtomicInteger(0);
        }
    }

}
