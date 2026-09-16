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

package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.proxy.StroomStatusCode;
import stroom.receive.common.StroomStreamException;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestRefusingReceiver {

    @Test
    void testEveryCallIsRefusedWithAStatusCode() {
        final RefusingReceiver receiver = new RefusingReceiver();

        assertThatThrownBy(() -> receiver.receive(Instant.now(), new AttributeMap(), "test",
                () -> new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(StroomStreamException.class)
                .hasMessageContaining(RefusingReceiver.MESSAGE)
                .extracting(e -> ((StroomStreamException) e).getStroomStatusCode())
                .isEqualTo(StroomStatusCode.UNKNOWN_ERROR);
        assertThatThrownBy(() -> receiver.receiveZip(Instant.now(), new AttributeMap(), "test", Path.of("x.zip")))
                .isInstanceOf(StroomStreamException.class)
                .hasMessageContaining(RefusingReceiver.MESSAGE);
    }
}
