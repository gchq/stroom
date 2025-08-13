package stroom.pathways.impl;

import stroom.pathways.shared.otel.trace.AnyValue;
import stroom.pathways.shared.otel.trace.KeyValue;
import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.pathways.shared.otel.trace.Span;
import stroom.pathways.shared.otel.trace.SpanKind;
import stroom.pathways.shared.otel.trace.Trace;
import stroom.pathways.shared.pathway.BooleanSet;
import stroom.pathways.shared.pathway.BooleanValue;
import stroom.pathways.shared.pathway.Constraint;
import stroom.pathways.shared.pathway.Constraints;
import stroom.pathways.shared.pathway.IntegerRange;
import stroom.pathways.shared.pathway.IntegerSet;
import stroom.pathways.shared.pathway.IntegerValue;
import stroom.pathways.shared.pathway.NanoTimeRange;
import stroom.pathways.shared.pathway.PathKey;
import stroom.pathways.shared.pathway.PathNode;
import stroom.pathways.shared.pathway.PathNodeList;
import stroom.pathways.shared.pathway.StringPattern;
import stroom.pathways.shared.pathway.StringSet;
import stroom.pathways.shared.pathway.StringValue;
import stroom.pathways.shared.pathway.VariableTypeValue;
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
        final Constraints.Builder builder;
        if (pathNode.getConstraints() != null) {
            builder = pathNode.getConstraints().copy();
        } else {
            builder = Constraints.builder();
        }
        final Constraints constraints = builder.build();

        // Set or expand duration range.
        final NanoTime startTime = NanoTime.fromString(span.getStartTimeUnixNano());
        final NanoTime endTime = NanoTime.fromString(span.getEndTimeUnixNano());
        final NanoTime duration = endTime.subtract(startTime);
        if (constraints.getDuration() == null) {
            builder.duration(new NanoTimeRange(duration, duration));
        } else if (constraints.getDuration().getMin().isGreaterThan(duration)) {
            builder.duration(new NanoTimeRange(duration, constraints.getDuration().getMax()));
        } else if (constraints.getDuration().getMax().isLessThan(duration)) {
            builder.duration(new NanoTimeRange(constraints.getDuration().getMin(), duration));
        }

        // Set or expand flags.
        builder.flags(createIntConstraint(constraints.getFlags(), span.getFlags()));

        // Set or expand kinds.
        final Set<SpanKind> set;
        if (constraints.getKind() != null) {
            set = new HashSet<>(constraints.getKind());
        } else {
            set = new HashSet<>();
        }
        set.add(span.getKind());
        builder.kind(set);

        // Create attribute sets.
        final Map<String, Constraint> attributes;
        if (constraints.getAttributes() != null) {
            attributes = new HashMap<>(constraints.getAttributes());
        } else {
            attributes = new HashMap<>();
        }
        if (!NullSafe.isEmptyCollection(span.getAttributes())) {
            span.getAttributes().forEach(kv -> addAttribute(attributes, kv));
            builder.attributes(attributes);
        }

        pathNodeBuilder.constraints(builder.build());
        return pathNodeBuilder;
    }

    private Constraint createIntConstraint(final Constraint current,
                                           final int value) {
        if (current == null) {
            return new IntegerValue(value);
        } else if (current instanceof final IntegerValue intValue) {
            if (!Objects.equals(intValue.getValue(), value)) {
                return new IntegerSet(Set.of(intValue.getValue(), value));
            }
        } else if (current instanceof final IntegerSet intSet) {
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
        } else if (current instanceof final IntegerRange intRange) {
            if (intRange.getMin() > value) {
                return new IntegerRange(value, intRange.getMax());
            } else if (intRange.getMax() < value) {
                return new IntegerRange(intRange.getMin(), value);
            }
        } else {
            return new VariableTypeValue();
        }
        return current;
    }

    private Constraint createBooleanConstraint(final Constraint current,
                                               final boolean value) {
        if (current == null) {
            return new BooleanValue(value);
        } else if (current instanceof final BooleanValue booleanValue) {
            if (!Objects.equals(booleanValue.getValue(), value)) {
                return new BooleanSet(Set.of(booleanValue.getValue(), value));
            }
        } else {
            return new VariableTypeValue();
        }
        return current;
    }

    private Constraint createStringConstraint(final Constraint current,
                                              final String value) {
        if (current == null) {
            return new StringValue(value);
        } else if (current instanceof final StringValue stringValue) {
            if (!Objects.equals(stringValue.getValue(), value)) {
                return new StringSet(Set.of(stringValue.getValue(), value));
            }
        } else if (current instanceof final StringSet stringSet) {
            final Set<String> set = new HashSet<>(stringSet.getSet());
            set.add(value);

            if (set.size() > MAX_SET_SIZE) {
                // Convert to pattern.
                // TODO : Create some sort of pattern expansion if possible.
                return new StringPattern(".*");
            } else {
                return new StringSet(set);
            }
        } else if (current instanceof final StringPattern stringPattern) {
            // TODO : Create some sort of pattern expansion if possible.
        } else {
            return new VariableTypeValue();
        }
        return current;
    }

    private void addAttribute(final Map<String, Constraint> attributes,
                              final KeyValue keyValue) {
        final Constraint current = attributes.get(keyValue.getKey());
        final AnyValue value = keyValue.getValue();

        if (value.getStringValue() != null) {
            attributes.put(keyValue.getKey(), createStringConstraint(current, value.getStringValue()));
        } else if (value.getBoolValue() != null) {
            attributes.put(keyValue.getKey(), createBooleanConstraint(current, value.getBoolValue()));
        } else if (value.getIntValue() != null) {
            attributes.put(keyValue.getKey(), createIntConstraint(current, value.getIntValue()));
        }
        // TODO : Add constraints for other attribute types.

    }
}
