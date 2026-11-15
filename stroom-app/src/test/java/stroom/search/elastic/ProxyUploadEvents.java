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

package stroom.search.elastic;

import stroom.util.json.JsonUtil;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;

/// Synthetic web proxy events, each an upload by a user to a site domain of some bytes, indexed directly into
/// Elasticsearch: for the ES|QL tests, which check grouped answers against totals worked out here from the same
/// events. The events are made from their number alone, so the same count always gives the same events.
///
/// Fields: `@timestamp` (one minute apart from [#START]), `user.name` and `url.domain` (keyword), `bytes` (long),
/// `event.action` (`allowed` or `blocked`, keyword), `message` (text, with a `message.keyword` sub-field), and
/// `StreamId` and `EventId` (long), as Stroom indexes them. The events are shared among the streams given, in turn,
/// which must be in Stroom for a search to find their events.
public final class ProxyUploadEvents {

    /// The time of the first event.
    public static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    /// The users.
    public static final List<String> USERS = List.of("alice", "bob", "carol", "dave", "erin", "frank", "grace");
    /// The site domains.
    public static final List<String> DOMAINS = List.of("dropbox.com", "drive.google.com", "github.com",
            "mega.nz", "onedrive.live.com", "pastebin.com", "transfer.sh", "wetransfer.com", "box.com",
            "s3.amazonaws.com", "icloud.com");
    /// The mapping of the index.
    public static final String INDEX_BODY = """
            {"settings": {"number_of_shards": 2, "number_of_replicas": 0},
             "mappings": {"properties": {
               "@timestamp": {"type": "date"},
               "user": {"properties": {"name": {"type": "keyword"}}},
               "url": {"properties": {"domain": {"type": "keyword"}}},
               "bytes": {"type": "long"},
               "event": {"properties": {"action": {"type": "keyword"}}},
               "message": {"type": "text", "fields": {"keyword": {"type": "keyword"}}},
               "StreamId": {"type": "long"},
               "EventId": {"type": "long"}}}}""";

    private static final int BATCH = 5_000;

    private ProxyUploadEvents() {
        // Static only.
    }

    /// Creates the index and indexes the events into it.
    ///
    /// @param node      The node.
    /// @param indexName The index to create.
    /// @param count     How many events.
    /// @param streamIds The streams the events are in, in turn: event `i` is in stream `i % streamIds.size()`.
    public static void seed(final ElasticTestNode node,
                            final String indexName,
                            final int count,
                            final List<Long> streamIds) {
        node.createIndex(indexName, INDEX_BODY);
        for (int from = 0; from < count; from += BATCH) {
            final ArrayNode batch = JsonUtil.getMapper().createArrayNode();
            for (int i = from; i < Math.min(count, from + BATCH); i++) {
                batch.add(document(i, streamIds));
            }
            node.bulkIndex(indexName, batch);
        }
    }

    /// @param i         The event's number, from 0.
    /// @param streamIds The streams the events are in, in turn.
    /// @return The event as a document, with its `_id`.
    public static ObjectNode document(final int i, final List<Long> streamIds) {
        final ObjectNode doc = JsonUtil.getMapper().createObjectNode();
        doc.put("_id", String.valueOf(i));
        doc.put("@timestamp", time(i).toString());
        doc.putObject("user").put("name", user(i));
        doc.putObject("url").put("domain", domain(i));
        doc.put("bytes", bytes(i));
        doc.putObject("event").put("action", blocked(i)
                ? "blocked"
                : "allowed");
        doc.put("message", "POST https://" + domain(i) + "/upload/" + i + " by " + user(i));
        doc.put("StreamId", streamIds.get(i % streamIds.size()));
        doc.put("EventId", 1 + i / streamIds.size());
        return doc;
    }

    /// @param i The event's number.
    /// @return Its time.
    public static Instant time(final int i) {
        return START.plusSeconds(60L * i);
    }

    /// @param i The event's number.
    /// @return Its user.
    public static String user(final int i) {
        return USERS.get(i % USERS.size());
    }

    /// @param i The event's number.
    /// @return Its site domain.
    public static String domain(final int i) {
        return DOMAINS.get((i + i / USERS.size()) % DOMAINS.size());
    }

    /// @param i The event's number.
    /// @return The bytes it uploaded.
    public static long bytes(final int i) {
        return 1 + (i * 7_919L) % 100_000;
    }

    /// @param i The event's number.
    /// @return True if the proxy blocked it.
    public static boolean blocked(final int i) {
        return i % 10 == 3;
    }

    /// The total bytes of each user and domain pair, the most first (ties by user, then domain).
    ///
    /// @param count    How many events there are.
    /// @param included Whether to count an event, by its number.
    /// @return The totals, keyed by `user|domain`.
    public static Map<String, Long> totalsByUserAndDomain(final int count, final IntPredicate included) {
        final Map<String, Long> totals = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            if (included.test(i)) {
                totals.merge(user(i) + "|" + domain(i), bytes(i), Long::sum);
            }
        }
        final Map<String, Long> sorted = new LinkedHashMap<>();
        totals.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        return sorted;
    }
}
