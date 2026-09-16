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
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Posts a file group's zip to a downstream Stroom or proxy with the group's meta as headers. The
 * downstream's four refusals are permanent; every other response and every exception is transient
 * ({@code designs/stages/forward.md} §4.5).
 */
public class HttpDestination implements Destination {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(HttpDestination.class);

    private final String name;
    private final StreamDestination sender;
    private final String description;

    /**
     * @param sender      What sends the bytes and classifies the response.
     * @param description The URL posted to, for logs.
     */
    public HttpDestination(final String name,
                           final StreamDestination sender,
                           final String description) {
        this.name = Objects.requireNonNull(name, "name");
        this.sender = Objects.requireNonNull(sender, "sender");
        this.description = Objects.requireNonNull(description, "description");
    }

    @Override
    public void deliver(final Path group) throws Refused, IOException {
        LOGGER.debug("'{}' - deliver(), group: {}", name, group);
        final FileGroup fileGroup = new FileGroup(group);
        final AttributeMap attributeMap = new AttributeMap();
        AttributeMapUtil.read(fileGroup.getMeta(), attributeMap);
        // Tell the downstream it is receiving zip data whatever the meta says.
        attributeMap.put(StandardHeaderArguments.COMPRESSION, StandardHeaderArguments.COMPRESSION_ZIP);

        try (final InputStream inputStream = new BufferedInputStream(Files.newInputStream(fileGroup.getZip()))) {
            sender.send(attributeMap, inputStream);
        } catch (final ForwardException e) {
            if (e.isRecoverable()) {
                throw new IOException(e.getMessage(), e);
            }
            throw new Refused(e.getStroomStatusCode(), e.getMessage(), e);
        }
    }

    @Override
    public Optional<LivenessCheck> livenessCheck() {
        if (!sender.hasLivenessCheck()) {
            return Optional.empty();
        }
        return Optional.of(() -> {
            if (!sender.performLivenessCheck()) {
                throw new Exception("'" + name + "' reported not live");
            }
        });
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " " + name + " - " + description;
    }
}
