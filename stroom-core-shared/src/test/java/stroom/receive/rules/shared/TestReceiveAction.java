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

package stroom.receive.rules.shared;

import stroom.test.common.TestUtil;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestReceiveAction {

    @Test
    void test() {
        TestUtil.testSerialisation(ReceiveAction.DROP, ReceiveAction.class);
    }

    @Test
    void testFromCiString() {
        final String json = "\"DrOp\"";

        final ReceiveAction receiveAction = JsonUtil.readValue(json, ReceiveAction.class);
        assertThat(receiveAction)
                .isEqualTo(ReceiveAction.DROP);
    }

    @Test
    void testGetFilterOutcome_receive() {
        final AtomicBoolean wasCalled = new AtomicBoolean(false);
        final Supplier<RuntimeException> supplier = () -> {
            wasCalled.set(true);
            return new IllegalStateException("foo");
        };
        assertThat(ReceiveAction.RECEIVE.toFilterResultOrThrow(supplier))
                .isTrue();
        assertThat(wasCalled)
                .isFalse();
    }

    @Test
    void testGetFilterOutcome_drop() {
        final AtomicBoolean wasCalled = new AtomicBoolean(false);
        final Supplier<RuntimeException> supplier = () -> {
            wasCalled.set(true);
            return new IllegalStateException("foo");
        };
        assertThat(ReceiveAction.DROP.toFilterResultOrThrow(supplier))
                .isFalse();
        assertThat(wasCalled)
                .isFalse();
    }

    @Test
    void testGetFilterOutcome_reject() {
        final AtomicBoolean wasCalled = new AtomicBoolean(false);
        final Supplier<RuntimeException> supplier = () -> {
            wasCalled.set(true);
            return new IllegalStateException("foo");
        };
        assertThatThrownBy(() ->
                ReceiveAction.REJECT.toFilterResultOrThrow(supplier))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("foo");
        assertThat(wasCalled)
                .isTrue();
    }
}
