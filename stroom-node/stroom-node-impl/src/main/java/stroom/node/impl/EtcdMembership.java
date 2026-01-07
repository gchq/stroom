/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.node.impl;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.Lease;
import io.etcd.jetcd.Watch;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.lease.LeaseKeepAliveResponse;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.options.PutOption;
import io.etcd.jetcd.options.WatchOption;
import io.etcd.jetcd.watch.WatchEvent;
import io.etcd.jetcd.watch.WatchResponse;
import io.grpc.stub.StreamObserver;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

public class EtcdMembership {

        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(EtcdMembership.class);

    private final Client client;
    private final String nodeId;
    private long leaseId;

    public EtcdMembership(final String etcdEndpoint, final String nodeId) {
        this.client = Client.builder()
                .endpoints(etcdEndpoint)
                .build();
        this.nodeId = nodeId;
    }

    public void registerNode(final String nodeInfo) throws Exception {
        final Lease leaseClient = client.getLeaseClient();

        // Create lease with 10 second TTL
        leaseId = leaseClient.grant(10).get().getID();

        // Start keep-alive in background
        leaseClient.keepAlive(leaseId, new StreamObserver<LeaseKeepAliveResponse>() {
            @Override
            public void onNext(final LeaseKeepAliveResponse response) {
                LOGGER.info("Lease kept alive: {}", response.getTTL());
            }

            @Override
            public void onError(final Throwable t) {
                LOGGER.error("Keep-alive error: {}", t.getMessage());
            }

            @Override
            public void onCompleted() {
                LOGGER.info("Keep-alive stream completed");
            }
        });

        // Write node info with lease attached
        final KV kvClient = client.getKVClient();
        final ByteSequence key = ByteSequence.from(
                "/cluster/nodes/" + nodeId,
                StandardCharsets.UTF_8
        );
        final ByteSequence value = ByteSequence.from(nodeInfo, StandardCharsets.UTF_8);

        final PutOption putOption = PutOption.builder()
                .withLeaseId(leaseId)
                .build();

        kvClient.put(key, value, putOption).get();
        LOGGER.info("Node registered: {}", nodeId);
    }

    public void unregister() throws Exception {
        // Revoke lease - this auto-deletes the key
        client.getLeaseClient().revoke(leaseId).get();
        LOGGER.info("Node unregistered: {}", nodeId);
    }

    public void watchMembership() {
        final Watch watchClient = client.getWatchClient();

        final ByteSequence prefix = ByteSequence.from(
                "/cluster/nodes/",
                StandardCharsets.UTF_8
        );

        final WatchOption watchOption = WatchOption.builder()
                .isPrefix(true)
                .build();

        final Watch.Watcher watcher = watchClient.watch(prefix, watchOption, new Watch.Listener() {
            @Override
            public void onNext(final WatchResponse response) {
                for (final WatchEvent event : response.getEvents()) {
                    final String key = event.getKeyValue()
                            .getKey()
                            .toString(StandardCharsets.UTF_8);
                    final String value = event.getKeyValue()
                            .getValue()
                            .toString(StandardCharsets.UTF_8);

                    switch (event.getEventType()) {
                        case PUT:
                            LOGGER.info("Node joined: {} -> {}", key, value);
                            break;
                        case DELETE:
                            LOGGER.info("Node left: {}", key);
                            break;
                    }
                }
            }

            @Override
            public void onError(final Throwable throwable) {
                LOGGER.error("Watch error: {}", throwable.getMessage(), throwable);
            }

            @Override
            public void onCompleted() {
                LOGGER.info("Watch completed");
            }
        });
    }

    public List<String> getClusterMembers() throws Exception {
        final KV kvClient = client.getKVClient();

        final ByteSequence prefix = ByteSequence.from(
                "/cluster/nodes/",
                StandardCharsets.UTF_8
        );

        final GetOption getOption = GetOption.builder()
                .isPrefix(true)
                .build();

        final GetResponse response = kvClient.get(prefix, getOption).get();

        return response.getKvs().stream()
                .map(kv -> kv.getValue().toString(StandardCharsets.UTF_8))
                .collect(Collectors.toList());
    }

    public void printMembers() throws Exception {
        final List<String> members = getClusterMembers();
        LOGGER.info("Current cluster members:\n{}", String.join("\n", members));
    }

    public void close() {
        client.close();
    }
}
