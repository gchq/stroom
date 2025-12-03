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

package stroom.util.exception;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class TestThrowingFunction {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestThrowingFunction.class);

    @Test
    void unchecked() {
        final List<Integer> list = Stream.of(1)
                .map(ThrowingFunction.unchecked(this::addTen))
                .collect(Collectors.toList());

        Assertions.assertThat(list)
                .containsExactly(11);
    }

    @Test
    void unchecked_throws() {
        Assertions.assertThatThrownBy(() ->
                        Stream.of(0)
                                .map(ThrowingFunction.unchecked(this::addTen))
                                .collect(Collectors.toList()))
                .isInstanceOf(RuntimeException.class);
    }

    private int addTen(final int i) throws MyCheckedException {
        if (i == 0) {
            throw new MyCheckedException();
        }
        return i + 10;
    }

    private static class MyCheckedException extends Exception {

    }
}
