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

package stroom.pipeline.xml.converter.ds3;


import stroom.test.common.util.test.StroomUnitTest;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestExecutionProfilerTopN extends StroomUnitTest {

    @Test
    void test() {
        final List<ExecutionProfiler> list = new ArrayList<>();
        list.add(new SimpleExecutionProfiler(10));
        list.add(new SimpleExecutionProfiler(1));
        list.add(new SimpleExecutionProfiler(5));

        final ExecutionProfilerTopN topN = new ExecutionProfilerTopN(list, 2);

        assertThat(topN.getTopN().get(0).getTotalExecutionCount()).isEqualTo(10);
        assertThat(topN.getTopN().get(1).getTotalExecutionCount()).isEqualTo(5);
        assertThat(topN.getTopN().size()).isEqualTo(2);
    }

    class SimpleExecutionProfiler implements ExecutionProfiler {

        int id;

        public SimpleExecutionProfiler(final int id) {
            this.id = id;
        }

        @Override
        public String getExecutionString() {
            return String.valueOf(id);
        }

        @Override
        public long getTotalExecutionTime() {
            return id;
        }

        @Override
        public long getTotalExecutionCount() {
            return id;
        }

    }
}
