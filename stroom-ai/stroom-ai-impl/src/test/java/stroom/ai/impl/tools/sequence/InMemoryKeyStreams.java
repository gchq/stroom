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

package stroom.ai.impl.tools.sequence;

import stroom.ai.api.ElasticEql;
import stroom.ai.api.ElasticKeyStreams;
import stroom.docref.DocRef;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.TimeRange;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/// [ElasticKeyStreams] over events held in memory, for the streaming join's tests: each doc's events are filtered
/// (its expression's `=` with `*` patterns, `in`, `not`, `or`, `and` and the case-insensitive option), their keys
/// transformed, ranged and termed, sorted by key then time, and paged, as Elasticsearch would. It records each side
/// read, so a test can say what was pushed down.
public final class InMemoryKeyStreams implements ElasticKeyStreams {

    private final Map<DocRef, Data> data = new LinkedHashMap<>();
    private final Set<DocRef> denied = ConcurrentHashMap.newKeySet();
    private final Map<DocRef, String> refused = new ConcurrentHashMap<>();
    private final List<Side> scanned = new CopyOnWriteArrayList<>();
    private final List<Side> keyed = new CopyOnWriteArrayList<>();
    private final AtomicInteger opened = new AtomicInteger();
    private final AtomicInteger closed = new AtomicInteger();

    /// @param doc       A doc.
    /// @param timeField Its time field, or null.
    /// @param mapping   Its fields' Elasticsearch types, by name.
    /// @return Its events, to add to.
    public List<Map<String, Object>> add(final DocRef doc, final String timeField, final Map<String, String> mapping) {
        final Data added = new Data(timeField, mapping, new ArrayList<>());
        data.put(doc, added);
        return added.events();
    }

    /// @param doc A doc the user may not use.
    void deny(final DocRef doc) {
        denied.add(doc);
    }

    /// @param doc    A doc whose cluster refuses to be read, as one without its credentials does.
    /// @param reason What the cluster says.
    public void refuse(final DocRef doc, final String reason) {
        refused.put(doc, reason);
    }

    /// @return The sides scanned (a chunk each).
    public List<Side> scanned() {
        return scanned;
    }

    /// @return The sides read as keys (a chunk each).
    List<Side> keyed() {
        return keyed;
    }

    /// @return Scans opened and not yet closed or finished.
    public int open() {
        return opened.get() - closed.get();
    }

    /// @param doc A doc.
    /// @return Its events.
    List<Map<String, Object>> events(final DocRef doc) {
        return data.get(doc).events();
    }

    @Override
    public Target target(final List<DocRef> indexes) {
        final List<Doc> docs = new ArrayList<>();
        for (final DocRef index : indexes) {
            final Data doc = use(index);
            final Map<String, ElasticEql.Field> fields = new LinkedHashMap<>();
            doc.mapping().forEach((name, type) -> fields.put(name, new ElasticEql.Field(name, type, null, null,
                    false)));
            docs.add(new Doc(index, index.getName(), doc.timeField(), fields));
        }
        return new Target("9.5.3", docs);
    }

    @Override
    public KeyPage keyPage(final KeyRequest request) {
        keyed.add(request.side());
        final List<KeyCount> keys = keys(request.side(), request.side().keys().size());
        final Comparator<List<Object>> comparator = comparator(request.side());
        final List<KeyCount> after = keys.stream()
                .filter(key -> request.after() == null || comparator.compare(key.key(), request.after()) > 0)
                .limit(request.size())
                .toList();
        return new KeyPage(after, after.isEmpty()
                ? null
                : after.getLast().key(), 1);
    }

    @Override
    public Scan openScan(final ScanRequest request) {
        final Data doc = use(request.side().index());
        if (doc.timeField() == null) {
            throw new Refusal(Reason.NO_TIME_FIELD, "no time field");
        }
        opened.incrementAndGet();
        scanned.add(request.side());
        return new Scan(request, "pit-" + opened.get(), null);
    }

    @Override
    public ScanPage scanPage(final NextPage request) {
        final Scan scan = request.scan();
        final Side side = scan.request().side();
        final Data doc = use(side.index());
        final List<Row> rows = rows(side, doc, scan.request().fields());
        final int from = scan.searchAfter() == null
                ? 0
                : ((Number) scan.searchAfter().getFirst()).intValue();
        final int to = Math.min(rows.size(), from + scan.request().size());
        final List<Row> page = rows.subList(from, to);
        final Scan next = page.size() < scan.request().size()
                ? null
                : new Scan(scan.request(), scan.pointInTime(), List.of((long) to));
        if (next == null) {
            closed.incrementAndGet();
        }
        return new ScanPage(page, next, 1);
    }

    @Override
    public void close(final Scan scan) {
        if (scan != null) {
            closed.incrementAndGet();
        }
    }

    @Override
    public Sample sampleKeys(final SampleRequest request) {
        final List<KeyCount> keys = keys(request.side(), 1);
        final List<Object> boundaries = new ArrayList<>();
        long rows = 0;
        long next = request.everyRows();
        for (final KeyCount key : keys) {
            rows += key.count();
            if (rows >= next && boundaries.size() < request.most()) {
                boundaries.add(key.key().getFirst());
                while (next <= rows) {
                    next += request.everyRows();
                }
            }
        }
        return new Sample(boundaries, keys.size(), rows, 1);
    }

    // ------------------------------------------------------------------------------------------------

    private Data use(final DocRef doc) {
        if (refused.containsKey(doc)) {
            throw new Refusal(Reason.CLUSTER_ERROR, "The cluster refused to give its version: " + refused.get(doc));
        }
        if (denied.contains(doc)) {
            throw new PermissionException(null, "You are not authorised to search " + doc);
        }
        return Objects.requireNonNull(data.get(doc), "no data for " + doc);
    }

    private List<KeyCount> keys(final Side side, final int fields) {
        final Data doc = use(side.index());
        final Map<List<Object>, Long> counts = new TreeMap<>(comparator(side));
        for (final Map<String, Object> event : doc.events()) {
            if (!matches(side, doc, event)) {
                continue;
            }
            final List<Object> key = key(side, event).subList(0, fields);
            counts.merge(key, 1L, Long::sum);
        }
        final List<KeyCount> keys = new ArrayList<>();
        counts.forEach((key, count) -> keys.add(new KeyCount(key, count)));
        return keys;
    }

    private List<Row> rows(final Side side, final Data doc, final List<String> fields) {
        final List<Row> rows = new ArrayList<>();
        int id = 0;
        for (final Map<String, Object> event : doc.events()) {
            id++;
            if (!matches(side, doc, event) || event.get(doc.timeField()) == null) {
                continue;
            }
            final Map<String, List<Object>> fetched = new LinkedHashMap<>();
            for (final String field : fields) {
                if (event.get(field) != null) {
                    fetched.put(field, List.of(event.get(field)));
                }
            }
            rows.add(new Row(key(side, event), time(event.get(doc.timeField())), side.index().getName(),
                    String.valueOf(id), fetched));
        }
        final Comparator<List<Object>> keys = comparator(side);
        rows.sort(Comparator.<Row, List<Object>>comparing(Row::key, keys).thenComparing(Row::time));
        return rows;
    }

    private Comparator<List<Object>> comparator(final Side side) {
        final Data doc = data.get(side.index());
        return JoinKeys.comparator(side.keys().stream()
                .map(key -> key.transforms().isEmpty()
                        ? JoinKeys.kind(doc.mapping().get(key.field()))
                        : JoinKeys.Kind.TEXT)
                .toList());
    }

    private static List<Object> key(final Side side, final Map<String, Object> event) {
        final List<Object> key = new ArrayList<>();
        for (final KeyField field : side.keys()) {
            final Object value = event.get(field.field());
            key.add(field.transforms().isEmpty() || value == null
                    ? value
                    : Transform.applyAll(field.transforms(), String.valueOf(value)));
        }
        return key;
    }

    private boolean matches(final Side side, final Data doc, final Map<String, Object> event) {
        final List<Object> key = key(side, event);
        if (key.stream().anyMatch(Objects::isNull)) {
            return false;
        }
        if (side.filter() != null && !holds(side.filter(), event)) {
            return false;
        }
        if (side.timeRange() != null && !inTime(side.timeRange(), event.get(doc.timeField()))) {
            return false;
        }
        final JoinKeys.Kind kind = side.keys().getFirst().transforms().isEmpty()
                ? JoinKeys.kind(doc.mapping().get(side.keys().getFirst().field()))
                : JoinKeys.Kind.TEXT;
        final KeyRange range = side.range();
        if (range.after() != null && JoinKeys.compare(kind, key.getFirst(), range.after()) <= 0) {
            return false;
        }
        if (range.upTo() != null && JoinKeys.compare(kind, key.getFirst(), range.upTo()) > 0) {
            return false;
        }
        for (int i = 0; i < side.terms().size() && i < key.size(); i++) {
            final List<Object> terms = side.terms().get(i);
            final String value = JoinKeys.text(key.get(i));
            if (!terms.isEmpty() && terms.stream().noneMatch(term -> JoinKeys.text(term).equals(value))) {
                return false;
            }
        }
        return true;
    }

    private static boolean inTime(final TimeRange range, final Object value) {
        final Long time = time(value);
        if (time == null) {
            return false;
        }
        if (NullSafe.isNonBlankString(range.getFrom()) && time < Instant.parse(range.getFrom()).toEpochMilli()) {
            return false;
        }
        return NullSafe.isBlankString(range.getTo()) || time < Instant.parse(range.getTo()).toEpochMilli();
    }

    private static Long time(final Object value) {
        if (value instanceof final Number number) {
            return number.longValue();
        } else if (value instanceof final String text) {
            return Instant.parse(text).toEpochMilli();
        }
        return null;
    }

    private static boolean holds(final ExpressionItem item, final Map<String, Object> event) {
        if (item instanceof final ExpressionOperator operator) {
            final List<ExpressionItem> children = NullSafe.list(operator.getChildren());
            return switch (operator.op()) {
                case AND -> children.stream().allMatch(child -> holds(child, event));
                case OR -> children.stream().anyMatch(child -> holds(child, event));
                case NOT -> children.stream().noneMatch(child -> holds(child, event));
            };
        }
        final ExpressionTerm term = (ExpressionTerm) item;
        final Object value = event.get(term.getField());
        final boolean insensitive = term.getOptions() != null
                                    && "true".equals(term.getOptions().get(StreamFilters.CASE_INSENSITIVE));
        return switch (term.getCondition()) {
            case EQUALS -> value != null && like(String.valueOf(value), term.getValue(), insensitive);
            case NOT_EQUALS -> value == null || !like(String.valueOf(value), term.getValue(), insensitive);
            case IN -> value != null && List.of(term.getValue().split(",")).stream()
                    .anyMatch(one -> like(String.valueOf(value), one.strip(), insensitive));
            default -> throw new UnsupportedOperationException(term.getCondition().name());
        };
    }

    private static boolean like(final String value, final String pattern, final boolean insensitive) {
        final String regex = String.join(".*", List.of(pattern.split("\\*", -1)).stream()
                .map(Pattern::quote)
                .toList());
        return Pattern.compile(regex, insensitive
                ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
                : 0).matcher(value).matches() || (insensitive && value.toLowerCase(Locale.ROOT)
                .equals(pattern.toLowerCase(Locale.ROOT)));
    }


    // --------------------------------------------------------------------------------


    /// One doc's events.
    ///
    /// @param timeField Its time field, or null.
    /// @param mapping   Its fields' types.
    /// @param events    Its events.
    private record Data(String timeField, Map<String, String> mapping, List<Map<String, Object>> events) {

    }
}
