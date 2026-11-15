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

package stroom.mcp.impl.e2e;

import stroom.search.elastic.ElasticTestNode;
import stroom.util.json.JsonUtil;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.List;

/// The web proxy upload events of the ES|QL evaluation cases (62 to 64), indexed into Elasticsearch before the user
/// asks: as `esql/proxy-uploads-generator.py` makes them, drawn from [SeededRandom] as that script draws from
/// CPython's `random.Random(62)`, so the answers the script prints, which the cases give, are worked out
/// independently of Stroom and Elasticsearch.
///
/// Fields: `@timestamp`, `user.name`, `url.domain`, `http.request.bytes` (long), `event.action` (`allowed` or
/// `blocked`), `source.ip` (ip), and `StreamId` and `EventId`, as Stroom indexes them, of streams in Stroom.
final class EvalProxyUploads {

    /// How many events.
    static final int EVENTS = 10_000;
    /// The mapping of the index.
    static final String INDEX_BODY = """
            {"settings": {"number_of_shards": 1, "number_of_replicas": 0},
             "mappings": {"properties": {
               "@timestamp": {"type": "date"},
               "user": {"properties": {"name": {"type": "keyword"}}},
               "url": {"properties": {"domain": {"type": "keyword"}}},
               "http": {"properties": {"request": {"properties": {"bytes": {"type": "long"}}}}},
               "event": {"properties": {"action": {"type": "keyword"}}},
               "source": {"properties": {"ip": {"type": "ip"}}},
               "StreamId": {"type": "long"},
               "EventId": {"type": "long"}}}}""";

    private static final long SEED = 62;
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final List<Weighted> USERS = List.of(new Weighted("alice", 22, 1), new Weighted("bob", 18, 1),
            new Weighted("carol", 15, 2), new Weighted("dave", 13, 1), new Weighted("erin", 12, 4),
            new Weighted("frank", 10, 1), new Weighted("grace", 6, 3), new Weighted("heidi", 4, 1));
    private static final List<Weighted> DOMAINS = List.of(new Weighted("files.example.com", 15, 1),
            new Weighted("intranet.example.com", 8, 1), new Weighted("box.com", 12, 1),
            new Weighted("dropbox.com", 10, 1), new Weighted("github.com", 10, 1),
            new Weighted("drive.google.com", 10, 1), new Weighted("mega.nz", 8, 1), new Weighted("pastebin.com", 7, 1),
            new Weighted("transfer.sh", 8, 1), new Weighted("wetransfer.com", 7, 1), new Weighted("icloud.com", 5, 1));
    private static final List<Integer> MULTIPLIERS = List.of(1, 1, 1, 2, 5, 20);
    private static final int BATCH = 5_000;

    private EvalProxyUploads() {
        // Static only.
    }

    /// Creates the index and indexes the events.
    ///
    /// @param node      The node.
    /// @param indexName The index.
    /// @param streamIds The streams in Stroom the events are in, in turn.
    static void seed(final ElasticTestNode node, final String indexName, final List<Long> streamIds) {
        node.createIndex(indexName, INDEX_BODY);
        final SeededRandom rng = new SeededRandom(SEED);
        ArrayNode batch = JsonUtil.getMapper().createArrayNode();
        for (int i = 0; i < EVENTS; i++) {
            batch.add(document(rng, i, streamIds));
            if (batch.size() == BATCH) {
                node.bulkIndex(indexName, batch);
                batch = JsonUtil.getMapper().createArrayNode();
            }
        }
        if (!batch.isEmpty()) {
            node.bulkIndex(indexName, batch);
        }
    }

    // The next event, drawn as the script draws it.
    private static ObjectNode document(final SeededRandom rng, final int i, final List<Long> streamIds) {
        final Weighted user = weighted(rng, USERS);
        final String domain = weighted(rng, DOMAINS).name();
        final long size = (long) rng.randint(1, 50_000) * rng.choice(MULTIPLIERS) * user.multiplier();
        final boolean blocked = rng.randint(0, 9) == 0;
        final int second = rng.randint(0, 59);
        final ObjectNode doc = JsonUtil.getMapper().createObjectNode();
        doc.put("_id", String.valueOf(i));
        doc.put("@timestamp", START.plusSeconds(60L * i + second).toString());
        doc.putObject("user").put("name", user.name());
        doc.putObject("url").put("domain", domain);
        doc.putObject("http").putObject("request").put("bytes", size);
        doc.putObject("event").put("action", blocked
                ? "blocked"
                : "allowed");
        doc.putObject("source").put("ip", "10.1.0." + (10 + USERS.indexOf(user)));
        doc.put("StreamId", streamIds.get(i % streamIds.size()));
        doc.put("EventId", 1 + i / streamIds.size());
        return doc;
    }

    private static Weighted weighted(final SeededRandom rng, final List<Weighted> items) {
        int pick = rng.randint(0, 99);
        for (final Weighted item : items) {
            if (pick < item.weight()) {
                return item;
            }
            pick -= item.weight();
        }
        throw new IllegalStateException("The weights must add up to 100");
    }


    // --------------------------------------------------------------------------------


    // A user or domain, how often it is drawn (of 100), and how much more a user sends than others.
    private record Weighted(String name, int weight, int multiplier) {

    }
}
