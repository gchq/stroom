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

package stroom.util;


import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class TestAbstractCommandLineTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestAbstractCommandLineTool.class);

    @Test
    void testSimple() {
        final TestProgram testProgram = new TestProgram();
        testProgram.doMain(new String[]{"prop1=11"});
        testProgram.traceArguments(System.out);
    }

    public static class TestProgram extends AbstractCommandLineTool {

        int prop1;
        int prop2;

        public void setProp1(final int prop1) {
            this.prop1 = prop1;
        }

        public void setProp2(final int prop2) {
            this.prop2 = prop2;
        }

        @Override
        public void run() {
            LOGGER.info("run() - {} {}", prop1, prop2);
        }
    }
}
