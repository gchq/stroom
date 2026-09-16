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
import stroom.receive.common.StroomStreamException;

import java.nio.file.Path;
import java.time.Instant;

/**
 * Where every entry point ends: the datafeed handler, the directory scanner and the event store all
 * bring bytes here, and here they become the pipeline's or the caller is told why not.
 * <p>
 * Both methods return only when the receipt is complete - the data durable and its message
 * published, or relayed and accepted downstream - so a caller that gets a normal return may delete
 * or acknowledge its source. A caller that gets an exception still owns its source and must keep it.
 * The contract in full is {@code designs/stages/receive.md} §2.
 * </p>
 * <p>
 * Callers run these as the processing user: the receipt policy consults feed status, which needs an
 * identity, and no caller's own carries that permission. The receiver never elevates.
 * </p>
 */
public interface Receiver {

    /**
     * Receive a request body. {@code attributeMap} holds the caller's headers with feed, type and
     * compression normalised and the receipt id minted; {@code Compression} decides whether the body
     * is read as a zip. {@code source} is what the receive log records as the URL.
     *
     * @throws StroomStreamException With the status code the caller should report to its sender.
     */
    void receive(Instant startTime,
                 AttributeMap attributeMap,
                 String source,
                 InputStreamSupplier body);

    /**
     * Receive a zip that is already a file on local disk. The file is read in place and belongs to
     * the caller before and after the call.
     *
     * @throws StroomStreamException With the status code the caller should report to its sender.
     */
    void receiveZip(Instant startTime,
                    AttributeMap attributeMap,
                    String source,
                    Path zipFile);
}
