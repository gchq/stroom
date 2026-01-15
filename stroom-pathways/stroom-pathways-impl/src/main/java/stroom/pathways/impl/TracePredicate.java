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

package stroom.pathways.impl;

import stroom.pathways.shared.otel.trace.AnyValue;
import stroom.pathways.shared.otel.trace.KeyValue;
import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.pathways.shared.otel.trace.Span;
import stroom.pathways.shared.otel.trace.Trace;
import stroom.pathways.shared.pathway.AnyBoolean;
import stroom.pathways.shared.pathway.AnyTypeValue;
import stroom.pathways.shared.pathway.BooleanValue;
import stroom.pathways.shared.pathway.Constraint;
import stroom.pathways.shared.pathway.ConstraintValue;
import stroom.pathways.shared.pathway.IntegerRange;
import stroom.pathways.shared.pathway.IntegerSet;
import stroom.pathways.shared.pathway.IntegerValue;
import stroom.pathways.shared.pathway.LongRange;
import stroom.pathways.shared.pathway.LongSet;
import stroom.pathways.shared.pathway.LongValue;
import stroom.pathways.shared.pathway.NanoTimeRange;
import stroom.pathways.shared.pathway.NanoTimeValue;
import stroom.pathways.shared.pathway.PathKey;
import stroom.pathways.shared.pathway.PathNode;
import stroom.pathways.shared.pathway.PathNodeSequence;
import stroom.pathways.shared.pathway.Regex;
import stroom.pathways.shared.pathway.StringSet;
import stroom.pathways.shared.pathway.StringValue;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class TracePredicate implements Predicate<Trace> {

    private final Comparator<Span> spanComparator;
    private final PathKeyFactory pathKeyFactory;
    private final Map<PathKey, PathNode> roots;

    public TracePredicate(final Comparator<Span> spanComparator,
                          final PathKeyFactory pathKeyFactory,
                          final Map<PathKey, PathNode> roots) {
        this.spanComparator = spanComparator;
        this.pathKeyFactory = pathKeyFactory;
        this.roots = roots;
    }

    @Override
    public boolean test(final Trace trace) {

        final Span root = trace.root();
        final PathKey pathKey = pathKeyFactory.create(Collections.singletonList(root));

        final PathNode node = roots.get(pathKey);
        if (node == null) {
            return false;
        } else {
            return walk(trace, root, node);
        }
    }

    private boolean walk(final Trace trace,
                         final Span parentSpan,
                         final PathNode parentNode) {
        if (!addConstraints(parentNode, parentSpan)) {
            return false;
        }

        final List<Span> childSpans = trace.children(parentSpan);
        final List<Span> sortedSpans = new ArrayList<>(childSpans);
        sortedSpans.sort(spanComparator);
        final PathKey pathKey = pathKeyFactory.create(sortedSpans);

        // Load inner map.
        final Map<PathKey, PathNodeSequence> subMap = parentNode
                .getTargets()
                .stream()
                .collect(Collectors.toMap(PathNodeSequence::getPathKey, Function.identity()));

        // Get current path node list.
        final PathNodeSequence pathNodeList = subMap.get(pathKey);
        final List<PathNode> list = NullSafe
                .getOrElse(pathNodeList, PathNodeSequence::getNodes, Collections.emptyList());

        if (sortedSpans.size() != list.size()) {
            return false;
        }

        // Loop over all child spans.
        for (int i = 0; i < sortedSpans.size(); i++) {
            final Span span = sortedSpans.get(i);
            final PathNode pathNode = list.get(i);

            // Follow the path deeper.
            if (!walk(trace, span, pathNode)) {
                return false;
            }
        }

        return true;
    }

    private boolean addConstraints(final PathNode pathNode,
                                   final Span span) {


        final Map<String, Constraint> constraints = pathNode.getConstraints();
        if (constraints == null) {
            return true;
        }

        // Set or expand duration range.
        final NanoTime startTime = NanoTime.fromString(span.getStartTimeUnixNano());
        final NanoTime endTime = NanoTime.fromString(span.getEndTimeUnixNano());
        final NanoTime duration = endTime.subtract(startTime);

        if (!checkConstraint(constraints, "duration", duration)) {
            return false;
        }

        // Set or expand flags.
        if (!checkConstraint(constraints, "flags", span.getFlags())) {
            return false;
        }

        // Set or expand kind.
        if (!checkConstraint(constraints, "kind", span.getKind().name())) {
            return false;
        }

        // Create attribute sets.
        final Map<String, KeyValue> attributes = span
                .getAttributes()
                .stream()
                .collect(Collectors.toMap(kv -> "attribute." + kv.getKey(), Function.identity()));

        final boolean allRequiredAttributesExist = constraints.entrySet().stream().allMatch(entry -> {
            final String key = entry.getKey();
            final Constraint value = entry.getValue();
            return attributes.containsKey(key) || value.isOptional() || !key.startsWith("attribute.");
        });
        if (!allRequiredAttributesExist) {
            return false;
        }

        // Set or expand attributes.
        return attributes.entrySet().stream().allMatch(entry -> {
            final String key = entry.getKey();
            final KeyValue value = entry.getValue();
            return checkConstraint(constraints, key, value.getValue());
        });
    }

    private boolean checkConstraint(final Map<String, Constraint> constraints,
                                    final String name,
                                    final Object value) {
        final Constraint constraint = constraints.get(name);

        if (constraint == null) {
            return false;
        } else if (value == null) {
            return constraint.isOptional();

        } else {
            return switch (value) {
                case final Integer val -> checkIntConstraint(getConstraintValue(constraint), val);
                case final Long val -> checkLongConstraint(getConstraintValue(constraint), val);
                case final Boolean val -> checkBooleanConstraint(getConstraintValue(constraint), val);
                case final String val -> checkStringConstraint(getConstraintValue(constraint), val);
                case final NanoTime val -> checkNanoTimeConstraint(getConstraintValue(constraint), val);
                case final AnyValue val -> {
                    // Unwrap.
                    if (val.getStringValue() != null) {
                        yield checkConstraint(constraints,
                                name,
                                val.getStringValue());
                    } else if (val.getBoolValue() != null) {
                        yield checkConstraint(constraints,
                                name,
                                val.getBoolValue());
                    } else if (val.getIntValue() != null) {
                        yield checkConstraint(constraints,
                                name,
                                val.getIntValue());
                    }

                    yield false;
                    // TODO : Add constraints for other attribute types.
                }
                default -> true;
            };
        }
    }

    private ConstraintValue getConstraintValue(final Constraint constraint) {
        if (constraint == null) {
            return null;
        }
        return constraint.getValue();
    }

    private boolean checkNanoTimeConstraint(final ConstraintValue current,
                                            final NanoTime value) {
        return switch (current) {
            case null -> false;
            case final NanoTimeValue nanoTimeValue -> Objects.equals(nanoTimeValue.getValue(), value);
            case final NanoTimeRange timeRange ->
                    timeRange.getMin().isLessThanEquals(value) && timeRange.getMax().isGreaterThanEquals(value);
            default -> current instanceof AnyTypeValue;
        };
    }

    private boolean checkIntConstraint(final ConstraintValue current,
                                       final int value) {
        switch (current) {
            case null -> {
                return false;
            }
            case final IntegerValue intValue -> {
                return Objects.equals(intValue.getValue(), value);
            }
            case final IntegerSet intSet -> {
                return intSet.getSet().contains(value);
            }
            case final IntegerRange intRange -> {
                return intRange.getMin() <= value && intRange.getMax() >= value;
            }
            default -> {
                return current instanceof AnyTypeValue;
            }
        }
    }

    private boolean checkLongConstraint(final ConstraintValue current,
                                       final long value) {
        switch (current) {
            case null -> {
                return false;
            }
            case final LongValue longValue -> {
                return Objects.equals(longValue.getValue(), value);
            }
            case final LongSet longSet -> {
                return longSet.getSet().contains(value);
            }
            case final LongRange longRange -> {
                return longRange.getMin() <= value && longRange.getMax() >= value;
            }
            default -> {
                return current instanceof AnyTypeValue;
            }
        }
    }

    private boolean checkBooleanConstraint(final ConstraintValue current,
                                           final boolean value) {
        switch (current) {
            case null -> {
                return false;
            }
            case final BooleanValue booleanValue -> {
                return Objects.equals(booleanValue.getValue(), value);
            }
            case final AnyBoolean booleanValue -> {
                // Do nothing.
            }
            default -> {
                return current instanceof AnyTypeValue;
            }
        }
        return true;
    }

    private boolean checkStringConstraint(final ConstraintValue current,
                                          final String value) {
        switch (current) {
            case null -> {
                return false;
            }
            case final StringValue stringValue -> {
                return Objects.equals(stringValue.getValue(), value);
            }
            case final StringSet stringSet -> {
                final Set<String> set = new HashSet<>(stringSet.getSet());
                return set.contains(value);
            }
            case final Regex stringPattern -> {
                // TODO : Create some sort of pattern expansion if possible.
            }
            default -> {
                return current instanceof AnyTypeValue;
            }
        }
        return true;
    }
}
