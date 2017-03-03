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

package stroom.util.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.test.StroomExpectedException;
import org.junit.Test;
import org.junit.runner.RunWith;

import stroom.util.test.StroomJUnit4ClassRunner;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestStroomJUnit4ClassRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestStroomJUnit4ClassRunner.class);

    @Test
    @StroomExpectedException(exception = RuntimeException.class)
    public void testSimple1() {
        LOGGER.error("MSG", new RuntimeException());
    }
}
