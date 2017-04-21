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

package stroom.benchmark.server;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import stroom.entity.shared.Period;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.StroomUnitTest;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestBenchmarkClusterTaskHandler extends StroomUnitTest {
    @Test
    public void testSimple() {
        final BenchmarkClusterExecutor benchmarkClusterTaskHandler = new BenchmarkClusterExecutor(null, null, null, null, null, null, null, null, null, null, null, null, 0, 0, 0);
        Assert.assertEquals("1000 EPS", 1000, benchmarkClusterTaskHandler.toEPS(1000, new Period(0L, 1000L)));
        Assert.assertEquals("1000000 EPS", 1000000, benchmarkClusterTaskHandler.toEPS(1000000L, new Period(0L, 1000L)));
    }
}
