/*
 * Copyright 2026 Crown Copyright
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

package stroom.query.language.functions.ref;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestFunctionWarnings {

    @Test
    void testEachMessageIsGivenOnce() {
        final List<String> messages = new ArrayList<>();
        final FunctionWarnings functionWarnings = new FunctionWarnings();
        functionWarnings.setConsumer(messages::add);
        functionWarnings.warn("one");
        functionWarnings.warn("two");
        functionWarnings.warn("one");
        assertThat(messages).containsExactly("one", "two");
    }

    @Test
    void testDroppedUntilConsumerIsSet() {
        final List<String> messages = new ArrayList<>();
        final FunctionWarnings functionWarnings = new FunctionWarnings();
        functionWarnings.warn("one");
        functionWarnings.setConsumer(messages::add);
        assertThat(messages).isEmpty();
        functionWarnings.warn("one");
        assertThat(messages).containsExactly("one");
    }
}
