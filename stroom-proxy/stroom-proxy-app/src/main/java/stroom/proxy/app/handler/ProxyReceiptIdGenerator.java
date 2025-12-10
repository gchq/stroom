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

package stroom.proxy.app.handler;

import stroom.receive.common.ReceiptIdGenerator;
import stroom.util.concurrent.UniqueId;
import stroom.util.concurrent.UniqueId.NodeType;
import stroom.util.concurrent.UniqueIdGenerator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * {@link ReceiptIdGenerator} for a proxy node
 */
@Singleton
public class ProxyReceiptIdGenerator implements ReceiptIdGenerator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProxyReceiptIdGenerator.class);

    private static final NodeType NODE_TYPE = NodeType.PROXY;

    private final UniqueIdGenerator receiptIdGenerator;

    @Inject
    public ProxyReceiptIdGenerator(final ProxyId proxyId) {
        LOGGER.info("Creating receiptIdGenerator for proxyId '{}'", proxyId);
        receiptIdGenerator = new UniqueIdGenerator(NODE_TYPE, proxyId.getId());
    }

    /**
     * For testing
     */
    public ProxyReceiptIdGenerator(final Supplier<String> nodeIdSupplier) {
        final String nodeId = Objects.requireNonNull(nodeIdSupplier).get();
        LOGGER.info("Creating receiptIdGenerator for proxyId '{}'", nodeId);
        receiptIdGenerator = new UniqueIdGenerator(NODE_TYPE, nodeId);
    }

    /**
     * @return A globally unique ID.
     */
    @Override
    public UniqueId generateId() {
        return receiptIdGenerator.generateId();
    }
}
