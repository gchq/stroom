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

package stroom.planb.impl.dao.trace;

import stroom.pathways.shared.otel.trace.AnyValue;
import stroom.pathways.shared.otel.trace.KeyValue;
import stroom.pathways.shared.otel.trace.NanoDuration;
import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.pathways.shared.otel.trace.Span;
import stroom.util.shared.NullSafe;
import stroom.util.shared.time.SimpleDuration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Puts a span's children into an order that is the same every time the same work happens, so that the
 * key built from their names identifies the route rather than the run.
 *
 * <p>Children that ran one after another keep their order: the program decided it, so a change in it is
 * a change in behaviour and the model should say so. Children that ran at the same time do not: which
 * of them started first is whatever the scheduler did that second, and treating it as a route would
 * make every run a new one.
 *
 * <p>So the children are gathered into groups of spans that overlap in time — allowing
 * {@code tolerance} for spans that are close enough to be concurrent without quite touching, which is
 * usual for short calls made from several threads at once. Groups cannot overlap each other, so they
 * are ordered by when they happened.
 *
 * <p>Within a group the order comes from which thread ran each span. Two spans of one thread cannot
 * have run at the same time, so the order they ran in is the program's and is kept; which thread went
 * first is the scheduler's, so a thread's spans are laid out one after another rather than woven in
 * among another thread's. That also lets threads that did the same work be seen as the repeat they
 * are, so a job that ran four of them reads the same as one that ran two. Spans that do not say which
 * thread ran them are ordered by name, which is all that can be said about them.
 *
 * <p>This is a function over the whole list rather than a comparison between two spans, and that is
 * what makes it safe. Asking "is this one before that one" with a tolerance gives answers that
 * contradict each other — a before b, b before c, c before a — so the result depends on the order the
 * spans arrived in and {@code List.sort} may refuse it outright. Deciding the groups first, and only
 * then ordering, cannot contradict itself.
 */
public class CanonicalSpanOrder {

    /**
     * The attribute the OpenTelemetry agent puts the running thread's name in. Read here to decide
     * what could have run at the same time; the model does not learn a value for it, because thread
     * names change every run and a constraint over them would say nothing.
     */
    private static final String THREAD_NAME = "thread.name";
    private static final String NO_THREAD = "";


    /**
     * Used where a document does not say. Short calls made from several threads at once land tens of
     * microseconds apart, so nothing smaller than this gathers them; and children of a span that are
     * genuinely separate steps are normally further apart than this.
     */
    private static final long DEFAULT_TOLERANCE_NANOS = 1_000_000L;

    private final long toleranceNanos;

    public CanonicalSpanOrder(final SimpleDuration simpleDuration) {
        this.toleranceNanos = simpleDuration == null
                ? DEFAULT_TOLERANCE_NANOS
                : toNanos(simpleDuration);
    }

    public CanonicalSpanOrder(final NanoDuration tolerance) {
        this.toleranceNanos = tolerance == null
                ? DEFAULT_TOLERANCE_NANOS
                : tolerance.getNanos();
    }

    /**
     * The children in one order, however they were timed. A run of one thread's spans follows the one
     * before it rather than being woven in among it, so what a reader follows is the work each thread
     * did.
     */
    public List<Span> sort(final List<Span> spans) {
        final List<Span> ordered = new ArrayList<>(NullSafe.list(spans).size());
        groups(spans).forEach(group -> group.runs().forEach(ordered::addAll));
        return ordered;
    }

    /**
     * The children gathered into groups of work that happened at the same time, each group holding one
     * run per thread. A group of one run is work that had the moment to itself; a group of several is
     * work that overlapped, and nothing about the order of those runs is worth keeping.
     */
    public List<SpanGroup> groups(final List<Span> spans) {
        if (spans == null || spans.isEmpty()) {
            return List.of();
        }
        if (spans.size() == 1) {
            return List.of(new SpanGroup(List.of(List.copyOf(spans))));
        }

        final List<Timed> timed = new ArrayList<>(spans.size());
        for (final Span span : spans) {
            timed.add(Timed.of(span));
        }
        // Start from a fixed order so the grouping below sees the same input for the same spans.
        timed.sort(Comparator.comparingLong((Timed t) -> t.start)
                .thenComparingLong(t -> t.end)
                .thenComparing(t -> t.span.getName()));

        final List<SpanGroup> groups = new ArrayList<>();
        int groupStart = 0;
        long groupEnd = timed.getFirst().end;
        Set<String> threads = new HashSet<>();
        threads.add(timed.getFirst().thread);
        for (int i = 1; i <= timed.size(); i++) {
            // A span joins the group while it begins before the group has finished, give or take the
            // tolerance. The group's end moves out to cover it, so a chain of overlapping spans is one
            // group even where the first and last do not touch.
            //
            // It also joins where a thread already in the group ran it, however long after. A thread's
            // spans are read as one run below, and a group that ended in the middle of one would leave
            // its tail in a group of its own — where it can no longer be seen as part of the same work
            // as the thread beside it that did the same thing. Nothing is reordered by this: the run
            // it joins is its own thread's.
            if (i < timed.size()
                && (timed.get(i).start - toleranceNanos <= groupEnd
                    || sameThreadAsGroup(timed.get(i), threads))) {
                groupEnd = Math.max(groupEnd, timed.get(i).end);
                threads.add(timed.get(i).thread);
            } else {
                groups.add(group(timed.subList(groupStart, i)));
                if (i < timed.size()) {
                    groupStart = i;
                    groupEnd = timed.get(i).end;
                    threads = new HashSet<>();
                    threads.add(timed.get(i).thread);
                }
            }
        }
        return groups;
    }

    /**
     * One moment's work, as a run per thread. More than one run means they happened at the same time.
     */
    public record SpanGroup(List<List<Span>> runs) {

    }

    // Spans that do not say which thread ran them never hold a group open: nothing says they belong
    // to the same run, and treating them as one would put every such span in a single group.
    private static boolean sameThreadAsGroup(final Timed timed, final Set<String> threads) {
        return !NO_THREAD.equals(timed.thread) && threads.contains(timed.thread);
    }

    // A run per thread, each in the order that thread ran them, runs in the order they started. The
    // group arrives in start order, so gathering by thread keeps both without sorting again. Spans
    // naming no thread make one run of their own, ordered by name.
    private static SpanGroup group(final List<Timed> group) {
        final Map<String, List<Timed>> byThread = new LinkedHashMap<>();
        for (final Timed timed : group) {
            byThread.computeIfAbsent(timed.thread, k -> new ArrayList<>()).add(timed);
        }
        final List<Timed> unknown = byThread.remove(NO_THREAD);
        if (unknown != null) {
            unknown.sort(Comparator.comparing(t -> t.span.getName()));
            byThread.put(NO_THREAD, unknown);
        }
        final List<List<Span>> runs = new ArrayList<>(byThread.size());
        byThread.values().forEach(run -> runs.add(run.stream().map(t -> t.span).toList()));
        return new SpanGroup(runs);
    }

    // Which thread ran the span, or NO_THREAD where it does not say.
    private static String thread(final Span span) {
        for (final KeyValue attribute : NullSafe.list(span.getAttributes())) {
            if (THREAD_NAME.equals(attribute.getKey())) {
                final String name = NullSafe.get(attribute.getValue(), AnyValue::getStringValue);
                if (name != null) {
                    return name;
                }
            }
        }
        return NO_THREAD;
    }

    private static long toNanos(final SimpleDuration simpleDuration) {
        return switch (simpleDuration.getTimeUnit()) {
            case NANOSECONDS -> simpleDuration.getTime();
            case MILLISECONDS -> simpleDuration.getTime() * 1_000_000L;
            case SECONDS -> simpleDuration.getTime() * 1_000_000_000L;
            case MINUTES -> simpleDuration.getTime() * 60L * 1_000_000_000L;
            case HOURS -> simpleDuration.getTime() * 60L * 60L * 1_000_000_000L;
            default -> throw new IllegalArgumentException(
                    "Unable to convert duration unit " + simpleDuration.getTimeUnit());
        };
    }

    // Epoch nanos rather than NanoTime, because the grouping is arithmetic and a long holds a
    // timestamp until the year 2262.
    private record Timed(Span span, long start, long end, String thread) {

        private static Timed of(final Span span) {
            final long start = NanoTime.fromString(span.getStartTimeUnixNano()).toEpochNanos();
            // A span with no end has not finished, so it covers no more than its start.
            final long end = span.getEndTimeUnixNano() == null
                    ? start
                    : Math.max(start, NanoTime.fromString(span.getEndTimeUnixNano()).toEpochNanos());
            return new Timed(span, start, end, CanonicalSpanOrder.thread(span));
        }
    }
}
