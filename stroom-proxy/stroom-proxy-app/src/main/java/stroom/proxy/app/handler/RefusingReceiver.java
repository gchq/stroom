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

import java.nio.file.Path;
import java.time.Instant;

/**
 * The receiver on a node whose receive stage is disabled: a worker that only drains queues. Every
 * call is refused with a status code, so a sender pointed at the wrong node is told so rather than
 * having its data written to a store nothing drains.
 */
public class RefusingReceiver implements Receiver {

    public static final String MESSAGE = "This proxy does not receive data: stages.receive.enabled is false";

    @Override
    public void receive(final Instant startTime,
                        final AttributeMap attributeMap,
                        final String source,
                        final InputStreamSupplier body) {
        throw refusal(attributeMap);
    }

    @Override
    public void receiveZip(final Instant startTime,
                           final AttributeMap attributeMap,
                           final String source,
                           final Path zipFile) {
        throw refusal(attributeMap);
    }

    private static StroomStreamException refusal(final AttributeMap attributeMap) {
        return new StroomStreamException(StroomStatusCode.UNKNOWN_ERROR, attributeMap, MESSAGE);
    }
}
