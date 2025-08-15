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
import stroom.pathways.shared.pathway.NanoTimeRange;
import stroom.pathways.shared.pathway.NanoTimeValue;
import stroom.pathways.shared.pathway.PathKey;
import stroom.pathways.shared.pathway.PathNode;
import stroom.pathways.shared.pathway.PathNodeList;
import stroom.pathways.shared.pathway.Regex;
import stroom.pathways.shared.pathway.StringSet;
import stroom.pathways.shared.pathway.StringValue;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class NodeMutatorImpl implements TraceProcessor {

    private static final int MAX_SET_SIZE = 10;

    private final Comparator<Span> spanComparator;
    private final PathKeyFactory pathKeyFactory;

    public NodeMutatorImpl(final Comparator<Span> spanComparator,
                           final PathKeyFactory pathKeyFactory) {
        this.spanComparator = spanComparator;
        this.pathKeyFactory = pathKeyFactory;
    }


    @Override
    public void process(final Trace trace, final Map<PathKey, PathNode> roots) {
        final Map<PathKey, Map<String, Map<PathKey, PathNodeList>>> maps = new HashMap<>();
        final Span root = trace.getRoot();
        final PathKey pathKey = pathKeyFactory.create(Collections.singletonList(root));
        final PathNode node = roots.computeIfAbsent(pathKey, k -> new PathNode(root.getName()));
        final Map<String, Map<PathKey, PathNodeList>> map = maps.computeIfAbsent(pathKey, k -> new HashMap<>());
        final PathNode pathNode = walk(trace, root, node, map);
        roots.put(pathKey, pathNode);
    }

    private PathNode walk(final Trace trace,
                          final Span parentSpan,
                          final PathNode parentNode,
                          final Map<String, Map<PathKey, PathNodeList>> map) {
        final PathNode.Builder pathNodeBuilder = addConstraints(parentNode, parentSpan);

        final List<Span> childSpans = trace.getChildren(parentSpan);
        final List<Span> sortedSpans = new ArrayList<>(childSpans);
        sortedSpans.sort(spanComparator);
        final PathKey pathKey = pathKeyFactory.create(sortedSpans);

        // Load inner map.
        final Map<PathKey, PathNodeList> innerMap = map.computeIfAbsent(parentNode.getUuid(), k -> {
            final Map<PathKey, PathNodeList> subMap = new HashMap<>();
            parentNode.getTargets().forEach(target -> subMap.put(target.getPathKey(), target));
            return subMap;
        });

        // Get current path node list.
        final PathNodeList pathNodeList = innerMap.get(pathKey);

        // Loop over all child spans.
        final List<PathNode> childNodes = new ArrayList<>(sortedSpans.size());
        for (int i = 0; i < sortedSpans.size(); i++) {
            final Span span = sortedSpans.get(i);

            final PathNode pathNode;
            if (pathNodeList != null) {
                pathNode = pathNodeList.getNodes().get(i);
            } else {
                final List<String> path = new ArrayList<>(parentNode.getPath());
                path.add(span.getName());
                pathNode = new PathNode(span.getName(), path);
            }

            // Follow the path deeper.
            final PathNode updated = walk(trace, span, pathNode, map);
            childNodes.add(updated);
        }

        // Update the path node list.
        innerMap.put(pathKey, new PathNodeList(pathKey, childNodes));

        // Update the targets for this node.
        pathNodeBuilder.targets(new ArrayList<>(innerMap.values()));

        return pathNodeBuilder.build();
    }

    private PathNode.Builder addConstraints(final PathNode pathNode, final Span span) {
        final PathNode.Builder pathNodeBuilder = pathNode.copy();

        // Add additional span info if wanted.
        final List<Span> spans;
        if (pathNode.getSpans() != null) {
            spans = new ArrayList<>(pathNode.getSpans());
            spans.add(span);
        } else {
            spans = Collections.singletonList(span);
        }
        pathNodeBuilder.spans(spans);

        // TODO : Expand min/max/average execution times.
        final Map<String, Constraint> constraints;
        final boolean optional;
        if (pathNode.getConstraints() != null) {
            constraints = pathNode.getConstraints();
            // We already have some constraints so make any new constraints optional.
            optional = true;
        } else {
            constraints = new HashMap<>();
            // These are new constraints so all initial ones will be set to be required.
            optional = false;
        }

        // Set or expand duration range.
        final NanoTime startTime = NanoTime.fromString(span.getStartTimeUnixNano());
        final NanoTime endTime = NanoTime.fromString(span.getEndTimeUnixNano());
        final NanoTime duration = endTime.subtract(startTime);

        setOrExpand(constraints, "duration", duration, false);

        // Set or expand flags.
        setOrExpand(constraints, "flags", span.getFlags(), false);

        // Set or expand kind.
        setOrExpand(constraints, "kind", span.getKind().name(), false);

        // Create attribute sets.
        final Map<String, KeyValue> attributes = span
                .getAttributes()
                .stream()
                .collect(Collectors.toMap(kv -> "attribute." + kv.getKey(), Function.identity()));

        // Make required constraints optional if they don't exist in this set.
        final Map<String, Constraint> newConstraints = new HashMap<>(constraints.size());
        constraints.forEach((key, value) -> {
            if (!attributes.containsKey(key) && !value.isOptional() && key.startsWith("attribute.")) {
                newConstraints.put(key, new Constraint(value.getName(), value.getValue(), true));
            } else {
                newConstraints.put(key, value);
            }
        });

        // Set or expand attributes.
        attributes.forEach((key, value) -> setOrExpand(newConstraints, key, value.getValue(), optional));

        pathNodeBuilder.constraints(newConstraints);
        return pathNodeBuilder;
    }

    private void setOrExpand(final Map<String, Constraint> constraints,
                             final String name,
                             final Object value,
                             final boolean optional) {
        final Constraint constraint = constraints.get(name);
        if (value instanceof final Integer integer) {
            constraints.put(name, new Constraint(name,
                    createIntConstraint(getConstraintValue(constraint), integer),
                    NullSafe.getOrElse(constraint, Constraint::isOptional, optional)));
        } else if (value instanceof final Boolean bool) {
            constraints.put(name, new Constraint(name,
                    createBooleanConstraint(getConstraintValue(constraint), bool),
                    NullSafe.getOrElse(constraint, Constraint::isOptional, optional)));
        } else if (value instanceof final String string) {
            constraints.put(name, new Constraint(name,
                    createStringConstraint(getConstraintValue(constraint), string),
                    NullSafe.getOrElse(constraint, Constraint::isOptional, optional)));
        } else if (value instanceof final NanoTime nanoTime) {
            constraints.put(name, new Constraint(name,
                    createNanoTimeConstraint(getConstraintValue(constraint), nanoTime),
                    NullSafe.getOrElse(constraint, Constraint::isOptional, optional)));
        } else if (value instanceof final AnyValue anyValue) {
            // Unwrap.
            if (anyValue.getStringValue() != null) {
                setOrExpand(constraints, name, anyValue.getStringValue(), optional);
            } else if (anyValue.getBoolValue() != null) {
                setOrExpand(constraints, name, anyValue.getBoolValue(), optional);
            } else if (anyValue.getIntValue() != null) {
                setOrExpand(constraints, name, anyValue.getIntValue(), optional);
            }

            // TODO : Add constraints for other attribute types.
        }
    }

    private ConstraintValue getConstraintValue(final Constraint constraint) {
        if (constraint == null) {
            return null;
        }
        return constraint.getValue();
    }

    private ConstraintValue createNanoTimeConstraint(final ConstraintValue current,
                                                     final NanoTime value) {
        if (current == null) {
            return new NanoTimeValue(value);
        } else if (current instanceof final NanoTimeValue nanoTimeValue) {
            if (!Objects.equals(nanoTimeValue.getValue(), value)) {
//                return new IntegerSet(Set.of(nanoTimeValue.getValue(), value));


                if (nanoTimeValue.getValue().isGreaterThan(value)) {
                    return new NanoTimeRange(value, nanoTimeValue.getValue());
                } else if (nanoTimeValue.getValue().isLessThan(value)) {
                    return new NanoTimeRange(nanoTimeValue.getValue(), value);
                }
            }
//        } else if (current instanceof final IntegerSet intSet) {
//            final Set<Integer> set = new HashSet<>(intSet.getSet());
//            set.add(value);
//
//            if (set.size() > MAX_SET_SIZE) {
//                // Convert to range.
//                int min = value;
//                int max = value;
//                for (final int num : intSet.getSet()) {
//                    min = Math.min(min, num);
//                    max = Math.max(max, num);
//                }
//                return new IntegerRange(min, max);
//            } else {
//                return new IntegerSet(set);
//            }
        } else if (current instanceof final NanoTimeRange timeRange) {
            if (timeRange.getMin().isGreaterThan(value)) {
                return new NanoTimeRange(value, timeRange.getMax());
            } else if (timeRange.getMax().isLessThan(value)) {
                return new NanoTimeRange(timeRange.getMin(), value);
            }
        } else {
            return new AnyTypeValue();
        }
        return current;
    }

    private ConstraintValue createIntConstraint(final ConstraintValue current,
                                                final int value) {
        switch (current) {
            case null -> {
                return new IntegerValue(value);
            }
            case final IntegerValue intValue -> {
                if (!Objects.equals(intValue.getValue(), value)) {
                    return new IntegerSet(Set.of(intValue.getValue(), value));
                }
            }
            case final IntegerSet intSet -> {
                final Set<Integer> set = new HashSet<>(intSet.getSet());
                set.add(value);

                if (set.size() > MAX_SET_SIZE) {
                    // Convert to range.
                    int min = value;
                    int max = value;
                    for (final int num : intSet.getSet()) {
                        min = Math.min(min, num);
                        max = Math.max(max, num);
                    }
                    return new IntegerRange(min, max);
                } else {
                    return new IntegerSet(set);
                }
            }
            case final IntegerRange intRange -> {
                if (intRange.getMin() > value) {
                    return new IntegerRange(value, intRange.getMax());
                } else if (intRange.getMax() < value) {
                    return new IntegerRange(intRange.getMin(), value);
                }
            }
            default -> {
                return new AnyTypeValue();
            }
        }
        return current;
    }

    private ConstraintValue createBooleanConstraint(final ConstraintValue current,
                                                    final boolean value) {
        switch (current) {
            case null -> {
                return new BooleanValue(value);
            }
            case final BooleanValue booleanValue -> {
                if (!Objects.equals(booleanValue.getValue(), value)) {
                    return new AnyBoolean();
                }
            }
            case final AnyBoolean booleanValue -> {
                // Do nothing.
            }
            default -> {
                return new AnyTypeValue();
            }
        }
        return current;
    }

    private ConstraintValue createStringConstraint(final ConstraintValue current,
                                                   final String value) {
        switch (current) {
            case null -> {
                return new StringValue(value);
            }
            case final StringValue stringValue -> {
                if (!Objects.equals(stringValue.getValue(), value)) {
                    return new StringSet(Set.of(stringValue.getValue(), value));
                }
            }
            case final StringSet stringSet -> {
                final Set<String> set = new HashSet<>(stringSet.getSet());
                set.add(value);

                if (set.size() > MAX_SET_SIZE) {
                    // Convert to pattern.
                    // TODO : Create some sort of pattern expansion if possible.
                    return new Regex(".*");
                } else {
                    return new StringSet(set);
                }
            }
            case final Regex stringPattern -> {
                // TODO : Create some sort of pattern expansion if possible.
            }
            default -> {
                return new AnyTypeValue();
            }
        }
        return current;
    }
}
