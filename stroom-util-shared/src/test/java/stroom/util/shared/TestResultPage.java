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

package stroom.util.shared;

import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TestResultPage {

    @Test
    void testCollector1() {

        final PageRequest pageRequest = new PageRequest(10, 10);
        final ResultPage<WrappedInt> resultPage = IntStream.rangeClosed(0, 99)
                .boxed()
                .map(WrappedInt::new)
                .collect(ResultPage.collector(pageRequest));

        assertThat(resultPage.size())
                .isEqualTo(pageRequest.getLength());
        assertThat(resultPage.getFirst().getValue()).isEqualTo(10);
    }

    @Test
    void testCollector2() {

        final ResultPage<WrappedInt> resultPage = IntStream.rangeClosed(0, 99)
                .boxed()
                .map(WrappedInt::new)
                .collect(ResultPage.collector(ResultPage::new));

        assertThat(resultPage.size())
                .isEqualTo(100);
        assertThat(resultPage.getFirst().getValue()).isEqualTo(0);
    }

    @Test
    void testCollector3() {

        final ResultPage<WrappedInt> resultPage = IntStream.rangeClosed(0, 99)
                .boxed()
                .map(WrappedInt::new)
                .collect(ResultPage.collector((PageRequest) null));

        assertThat(resultPage.size())
                .isEqualTo(100);
        assertThat(resultPage.getFirst().getValue()).isEqualTo(0);
    }

    private static class WrappedInt {

        private final int value;

        WrappedInt(final int value) {
            this.value = value;
        }

        int getValue() {
            return value;
        }
    }

}
