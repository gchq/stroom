package stroom.pathways.impl;

import stroom.pathways.shared.PathwaysDoc;
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
import stroom.pathways.shared.pathway.PathNodeSequence;
import stroom.pathways.shared.pathway.Regex;
import stroom.pathways.shared.pathway.StringSet;
import stroom.pathways.shared.pathway.StringValue;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class NodeMutatorImpl implements TraceWalker {

    private static final int MAX_SET_SIZE = 10;

    private final Comparator<Span> spanComparator;
    private final PathKeyFactory pathKeyFactory;

    public NodeMutatorImpl(final Comparator<Span> spanComparator,
                           final PathKeyFactory pathKeyFactory) {
        this.spanComparator = spanComparator;
        this.pathKeyFactory = pathKeyFactory;
    }


    public PathNode process(final Trace trace,
                            final PathKey pathKey,
                            final PathNode pathNode,
                            final MessageReceiver messageReceiver,
                            final PathwaysDoc pathwaysDoc) {
        final Span root = trace.root();
        if (pathNode == null && !pathwaysDoc.isAllowPathwayCreation()) {
            messageReceiver.log(Severity.ERROR, () -> "Invalid path: " + pathKey);
            return pathNode;
        }


        final PathNode node;
        if (pathNode == null) {
            messageReceiver.log(Severity.INFO, () -> "Adding new root path: " + root.getName());
            node = new PathNode(root.getName());
        } else {
            node = pathNode;
        }

        final Map<PathKey, Map<String, Map<PathKey, PathNodeSequence>>> maps = new HashMap<>();
        final Map<String, Map<PathKey, PathNodeSequence>> map = maps.computeIfAbsent(pathKey, k -> new HashMap<>());
        return walk(trace, root, node, map, messageReceiver, pathwaysDoc);


//        final Map<PathKey, Map<String, Map<PathKey, PathNodeSequence>>> maps = new HashMap<>();
//        final Span root = trace.root();
//        final PathKey pathKey = pathKeyFactory.create(Collections.singletonList(root));
//
//        PathNode node = roots.get(pathKey);
    }

    @Override
    public void process(final Trace trace,
                        final Map<PathKey, PathNode> roots,
                        final MessageReceiver messageReceiver,
                        final PathwaysDoc pathwaysDoc) {
        final Map<PathKey, Map<String, Map<PathKey, PathNodeSequence>>> maps = new HashMap<>();
        final Span root = trace.root();
        final PathKey pathKey = pathKeyFactory.create(Collections.singletonList(root));

        PathNode node = roots.get(pathKey);
        if (node == null && !pathwaysDoc.isAllowPathwayCreation()) {
            messageReceiver.log(Severity.ERROR, () -> "Invalid path: " + pathKey);
        } else {
            node = roots.computeIfAbsent(pathKey, k -> {
                messageReceiver.log(Severity.INFO, () -> "Adding new root path: " + root.getName());
                return new PathNode(root.getName());
            });
            final Map<String, Map<PathKey, PathNodeSequence>> map = maps.computeIfAbsent(pathKey, k -> new HashMap<>());
            final PathNode pathNode = walk(trace, root, node, map, messageReceiver, pathwaysDoc);
            roots.put(pathKey, pathNode);
        }
    }

    private PathNode walk(final Trace trace,
                          final Span parentSpan,
                          final PathNode parentNode,
                          final Map<String, Map<PathKey, PathNodeSequence>> map,
                          final MessageReceiver messageReceiver,
                          final PathwaysDoc pathwaysDoc) {
        final PathNode.Builder pathNodeBuilder = addConstraints(parentNode, parentSpan, messageReceiver, pathwaysDoc);

        final List<Span> childSpans = trace.children(parentSpan);
        final List<Span> sortedSpans = new ArrayList<>(childSpans);
        sortedSpans.sort(spanComparator);
        final PathKey pathKey = pathKeyFactory.create(sortedSpans);

        // Load inner map.
        final Map<PathKey, PathNodeSequence> innerMap = map.computeIfAbsent(parentNode.getUuid(), k -> {
            final Map<PathKey, PathNodeSequence> subMap = new HashMap<>();
            parentNode.getTargets().forEach(target -> subMap.put(target.getPathKey(), target));
            return subMap;
        });

        // Get current path node list.
        final PathNodeSequence pathNodeList = innerMap.get(pathKey);
        if (pathNodeList == null && !pathwaysDoc.isAllowPathwayMutation()) {
            messageReceiver.log(Severity.ERROR, () -> "Invalid path: " + parentNode + " " + pathKey);

        } else {
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
                    messageReceiver.log(Severity.INFO, () -> "Adding new path: " + path);
                    pathNode = new PathNode(span.getName(), path);
                }

                // Follow the path deeper.
                final PathNode updated = walk(trace, span, pathNode, map, messageReceiver, pathwaysDoc);
                childNodes.add(updated);
            }

            // Update the path node list.
            innerMap.put(pathKey, new PathNodeSequence(UUID.randomUUID().toString(), pathKey, childNodes));

            // Update the targets for this node.
            pathNodeBuilder.targets(new ArrayList<>(innerMap.values()));
        }

        return pathNodeBuilder.build();
    }

    private PathNode.Builder addConstraints(final PathNode pathNode,
                                            final Span span,
                                            final MessageReceiver messageReceiver,
                                            final PathwaysDoc pathwaysDoc) {
        final PathNode.Builder pathNodeBuilder = pathNode.copy();

//        // Add additional span info if wanted.
//        final List<Span> spans;
//        if (pathNode.getSpans() != null) {
//            spans = new ArrayList<>(pathNode.getSpans());
//            spans.add(span);
//        } else {
//            spans = Collections.singletonList(span);
//        }
//        pathNodeBuilder.spans(spans);

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

        setOrExpand(constraints, pathNode, "duration", duration, false, messageReceiver, pathwaysDoc);

        // Set or expand flags.
        setOrExpand(constraints, pathNode, "flags", span.getFlags(), false, messageReceiver, pathwaysDoc);

        // Set or expand kind.
        setOrExpand(constraints, pathNode, "kind", span.getKind().name(), false, messageReceiver, pathwaysDoc);

        // Create attribute sets.
        final Map<String, KeyValue> attributes = span
                .getAttributes()
                .stream()
                .collect(Collectors.toMap(kv -> "attribute." + kv.getKey(), Function.identity()));

        // Make required constraints optional if they don't exist in this set.
        final Map<String, Constraint> newConstraints = new HashMap<>(constraints.size());
        constraints.forEach((key, value) -> {
            if (!attributes.containsKey(key) && !value.isOptional() && key.startsWith("attribute.")) {
                if (!pathwaysDoc.isAllowConstraintMutation()) {
                    messageReceiver.log(Severity.ERROR, () ->
                            "Attribute required: " + pathNode.getPath() + " " + key);
                } else {
                    messageReceiver.log(Severity.INFO, () -> "Making constraint optional: " +
                                                             pathNode.getPath() + " " +
                                                             key);
                    newConstraints.put(key, new Constraint(value.getName(), value.getValue(), true));
                }
            } else {
                newConstraints.put(key, value);
            }
        });

        // Set or expand attributes.
        attributes.forEach((key, value) ->
                setOrExpand(newConstraints, pathNode, key, value.getValue(), optional, messageReceiver, pathwaysDoc));

        pathNodeBuilder.constraints(newConstraints);
        return pathNodeBuilder;
    }

    private void setOrExpand(final Map<String, Constraint> constraints,
                             final PathNode pathNode,
                             final String name,
                             final Object value,
                             final boolean optional,
                             final MessageReceiver messageReceiver,
                             final PathwaysDoc pathwaysDoc) {
        final Supplier<String> location = () -> pathNode.getPath() + " " + name;
        final Constraint constraint = constraints.get(name);

        if (value == null) {
            if (!optional) {
                messageReceiver.log(Severity.ERROR, () ->
                        "Null value for: " + location.get());
            }
        } else {
            if (constraint == null && !pathwaysDoc.isAllowConstraintCreation()) {
                messageReceiver.log(Severity.ERROR, () ->
                        "Constraint not found: " + location.get() + " " + value);

            } else {
                final boolean opt = NullSafe.getOrElse(constraint, Constraint::isOptional, optional);
                switch (value) {
                    case final Integer integer -> constraints.put(name, new Constraint(name,
                            createIntConstraint(location,
                                    getConstraintValue(constraint),
                                    integer,
                                    messageReceiver,
                                    pathwaysDoc),
                            opt));
                    case final Boolean bool -> constraints.put(name, new Constraint(name,
                            createBooleanConstraint(location,
                                    getConstraintValue(constraint),
                                    bool,
                                    messageReceiver,
                                    pathwaysDoc),
                            opt));
                    case final String string -> constraints.put(name, new Constraint(name,
                            createStringConstraint(location,
                                    getConstraintValue(constraint),
                                    string,
                                    messageReceiver,
                                    pathwaysDoc),
                            opt));
                    case final NanoTime nanoTime -> constraints.put(name, new Constraint(name,
                            createNanoTimeConstraint(location,
                                    getConstraintValue(constraint),
                                    nanoTime,
                                    messageReceiver,
                                    pathwaysDoc),
                            opt));
                    case final AnyValue anyValue -> {
                        // Unwrap.
                        if (anyValue.getStringValue() != null) {
                            setOrExpand(constraints,
                                    pathNode,
                                    name,
                                    anyValue.getStringValue(),
                                    optional,
                                    messageReceiver,
                                    pathwaysDoc);
                        } else if (anyValue.getBoolValue() != null) {
                            setOrExpand(constraints,
                                    pathNode,
                                    name,
                                    anyValue.getBoolValue(),
                                    optional,
                                    messageReceiver,
                                    pathwaysDoc);
                        } else if (anyValue.getIntValue() != null) {
                            setOrExpand(constraints,
                                    pathNode,
                                    name,
                                    anyValue.getIntValue(),
                                    optional,
                                    messageReceiver,
                                    pathwaysDoc);
                        }

                        // TODO : Add constraints for other attribute types.
                    }
                    default -> {
                    }
                }
            }
        }
    }

    private ConstraintValue getConstraintValue(final Constraint constraint) {
        if (constraint == null) {
            return null;
        }
        return constraint.getValue();
    }

    private ConstraintValue createNanoTimeConstraint(final Supplier<String> location,
                                                     final ConstraintValue current,
                                                     final NanoTime value,
                                                     final MessageReceiver messageReceiver,
                                                     final PathwaysDoc pathwaysDoc) {
        if (current == null) {
            if (!pathwaysDoc.isAllowPathwayMutation()) {
                messageReceiver.log(Severity.ERROR, () ->
                        "Unexpected time: " + location.get() + " " + value);
            } else {
                messageReceiver.log(Severity.INFO, () ->
                        "Adding time constraint: " + location.get() + " " + value);
                return new NanoTimeValue(value);
            }
        } else if (current instanceof final NanoTimeValue nanoTimeValue) {
            if (!Objects.equals(nanoTimeValue.getValue(), value)) {
                if (!pathwaysDoc.isAllowPathwayMutation()) {
                    messageReceiver.log(Severity.ERROR, () ->
                            "Unexpected time: " + location.get() + " " + value);
                } else {
                    if (nanoTimeValue.getValue().isGreaterThan(value)) {
                        messageReceiver.log(Severity.INFO, () ->
                                "Expanding max time constraint: " + location.get() + " " + value);
                        return new NanoTimeRange(value, nanoTimeValue.getValue());
                    } else if (nanoTimeValue.getValue().isLessThan(value)) {
                        messageReceiver.log(Severity.INFO, () ->
                                "Expanding min time constraint: " + location.get() + " " + value);
                        return new NanoTimeRange(nanoTimeValue.getValue(), value);
                    }
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
                if (!pathwaysDoc.isAllowPathwayMutation()) {
                    messageReceiver.log(Severity.ERROR, () ->
                            "Time exceeds min constraint: " + location.get() + " " + value);
                } else {
                    messageReceiver.log(Severity.INFO, () ->
                            "Expanding min time constraint: " + location.get() + " " + value);
                    return new NanoTimeRange(value, timeRange.getMax());
                }
            } else if (timeRange.getMax().isLessThan(value)) {
                if (!pathwaysDoc.isAllowPathwayMutation()) {
                    messageReceiver.log(Severity.ERROR, () ->
                            "Time exceeds max constraint: " + location.get() + " " + value);
                } else {
                    messageReceiver.log(Severity.INFO, () ->
                            "Expanding max time constraint: " + location.get() + " " + value);
                    return new NanoTimeRange(timeRange.getMin(), value);
                }
            }
        } else if (!(current instanceof AnyTypeValue)) {
            if (!pathwaysDoc.isAllowPathwayMutation()) {
                messageReceiver.log(Severity.ERROR, () ->
                        "Unexpected type found: " + location.get() + " " + value);
            } else {
                messageReceiver.log(Severity.WARNING, () ->
                        "Changing to any type: " + location.get() + " " + value);
                return new AnyTypeValue();
            }
        }
        return current;
    }

    private ConstraintValue createIntConstraint(final Supplier<String> location,
                                                final ConstraintValue current,
                                                final int value,
                                                final MessageReceiver messageReceiver,
                                                final PathwaysDoc pathwaysDoc) {
        switch (current) {
            case null -> {
                if (!pathwaysDoc.isAllowPathwayMutation()) {
                    messageReceiver.log(Severity.ERROR, () ->
                            "Unexpected integer: " + location.get() + " " + value);
                } else {
                    messageReceiver.log(Severity.INFO, () ->
                            "Adding integer constraint: " + location.get() + " " + value);
                    return new IntegerValue(value);
                }
            }
            case final IntegerValue intValue -> {
                if (!Objects.equals(intValue.getValue(), value)) {
                    if (!pathwaysDoc.isAllowPathwayMutation()) {
                        messageReceiver.log(Severity.ERROR, () ->
                                "Unexpected integer: " + location.get() + " " + value);
                    } else {
                        messageReceiver.log(Severity.INFO, () ->
                                "Expanding integer set: " + location.get() + " " + value);
                        return new IntegerSet(Set.of(intValue.getValue(), value));
                    }
                }
            }
            case final IntegerSet intSet -> {
                final Set<Integer> set = new HashSet<>(intSet.getSet());
                if (set.add(value)) {
                    if (!pathwaysDoc.isAllowPathwayMutation()) {
                        messageReceiver.log(Severity.ERROR, () ->
                                "Unexpected integer: " + location.get() + " " + value);
                    } else {
                        if (set.size() > MAX_SET_SIZE) {
                            // Convert to range.
                            int min = value;
                            int max = value;
                            for (final int num : intSet.getSet()) {
                                min = Math.min(min, num);
                                max = Math.max(max, num);
                            }
                            messageReceiver.log(Severity.INFO, () ->
                                    "Making integer range: " + location.get() + " " + value);
                            return new IntegerRange(min, max);
                        } else {
                            messageReceiver.log(Severity.INFO, () ->
                                    "Expanding integer set: " + location.get() + " " + value);
                            return new IntegerSet(set);
                        }
                    }
                }
            }
            case final IntegerRange intRange -> {
                if (intRange.getMin() > value) {
                    if (!pathwaysDoc.isAllowPathwayMutation()) {
                        messageReceiver.log(Severity.ERROR, () ->
                                "Integer exceeds min constraint: " + location.get() + " " + value);
                    } else {
                        messageReceiver.log(Severity.INFO, () ->
                                "Expanding integer range min: " + location.get() + " " + value);
                        return new IntegerRange(value, intRange.getMax());
                    }
                } else if (intRange.getMax() < value) {
                    if (!pathwaysDoc.isAllowPathwayMutation()) {
                        messageReceiver.log(Severity.ERROR, () ->
                                "Integer exceeds max constraint: " + location.get() + " " + value);
                    } else {
                        messageReceiver.log(Severity.INFO, () ->
                                "Expanding integer range max: " + location.get() + " " + value);
                        return new IntegerRange(intRange.getMin(), value);
                    }
                }
            }
            default -> {
                if (!(current instanceof AnyTypeValue)) {
                    if (!pathwaysDoc.isAllowPathwayMutation()) {
                        messageReceiver.log(Severity.ERROR, () ->
                                "Unexpected type found: " + location.get() + " " + value);
                    } else {
                        messageReceiver.log(Severity.WARNING, () ->
                                "Changing to any type: " + location.get() + " " + value);
                        return new AnyTypeValue();
                    }
                }
            }
        }
        return current;
    }

    private ConstraintValue createBooleanConstraint(final Supplier<String> location,
                                                    final ConstraintValue current,
                                                    final boolean value,
                                                    final MessageReceiver messageReceiver,
                                                    final PathwaysDoc pathwaysDoc) {
        switch (current) {
            case null -> {
                if (!pathwaysDoc.isAllowPathwayMutation()) {
                    messageReceiver.log(Severity.ERROR, () ->
                            "Unexpected boolean: " + location.get() + " " + value);
                } else {
                    messageReceiver.log(Severity.INFO, () ->
                            "Adding boolean constraint: " + location.get() + " " + value);
                    return new BooleanValue(value);
                }
            }
            case final BooleanValue booleanValue -> {
                if (!Objects.equals(booleanValue.getValue(), value)) {
                    if (!pathwaysDoc.isAllowPathwayMutation()) {
                        messageReceiver.log(Severity.ERROR, () ->
                                "Unexpected boolean: " + location.get() + " " + value);
                    } else {
                        messageReceiver.log(Severity.INFO, () ->
                                "Expanding to any boolean: " + location.get() + " " + value);
                        return new AnyBoolean();
                    }
                }
            }
            case final AnyBoolean booleanValue -> {
                // Do nothing.
            }
            default -> {
                if (!(current instanceof AnyTypeValue)) {
                    if (!pathwaysDoc.isAllowPathwayMutation()) {
                        messageReceiver.log(Severity.ERROR, () ->
                                "Unexpected type found: " + location.get() + " " + value);
                    } else {
                        messageReceiver.log(Severity.WARNING, () ->
                                "Changing to any type: " + location.get() + " " + value);
                        return new AnyTypeValue();
                    }
                }
            }
        }
        return current;
    }

    private ConstraintValue createStringConstraint(final Supplier<String> location,
                                                   final ConstraintValue current,
                                                   final String value,
                                                   final MessageReceiver messageReceiver,
                                                   final PathwaysDoc pathwaysDoc) {
        switch (current) {
            case null -> {
                if (!pathwaysDoc.isAllowPathwayMutation()) {
                    messageReceiver.log(Severity.ERROR, () ->
                            "Unexpected string: " + location.get() + " " + value);
                } else {
                    messageReceiver.log(Severity.INFO, () ->
                            "Adding string constraint: " + location.get() + " " + value);
                    return new StringValue(value);
                }
            }
            case final StringValue stringValue -> {
                if (!Objects.equals(stringValue.getValue(), value)) {
                    if (!pathwaysDoc.isAllowPathwayMutation()) {
                        messageReceiver.log(Severity.ERROR, () ->
                                "Unexpected string " + location.get() + " " + value);
                    } else {
                        messageReceiver.log(Severity.INFO, () ->
                                "Expanding string set: " + location.get() + " " + value);
                        return new StringSet(Set.of(stringValue.getValue(), value));
                    }
                }
            }
            case final StringSet stringSet -> {
                final Set<String> set = new HashSet<>(stringSet.getSet());
                if (set.add(value)) {
                    if (!pathwaysDoc.isAllowPathwayMutation()) {
                        messageReceiver.log(Severity.ERROR, () ->
                                "Unexpected string: " + location.get() + " " + value);
                    } else {
                        if (set.size() > MAX_SET_SIZE) {
                            // Convert to pattern.
                            // TODO : Create some sort of pattern expansion if possible.
                            messageReceiver.log(Severity.INFO, () ->
                                    "Converting string set to pattern: " + location.get() + " " + value);
                            return new Regex(".*");
                        } else {
                            messageReceiver.log(Severity.INFO, () ->
                                    "Expanding string set: " + location.get() + " " + value);
                            return new StringSet(set);
                        }
                    }
                }
            }
            case final Regex stringPattern -> {
                // TODO : Create some sort of pattern expansion if possible.
            }
            default -> {
                if (!(current instanceof AnyTypeValue)) {
                    if (!pathwaysDoc.isAllowPathwayMutation()) {
                        messageReceiver.log(Severity.ERROR, () ->
                                "Unexpected type found: " + location.get() + " " + value);
                    } else {
                        messageReceiver.log(Severity.WARNING, () ->
                                "Changing to any type: " + location.get() + " " + value);
                        return new AnyTypeValue();
                    }
                }
            }
        }
        return current;
    }
}
