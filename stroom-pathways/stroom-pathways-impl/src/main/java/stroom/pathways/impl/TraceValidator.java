package stroom.pathways.impl;

import stroom.pathways.shared.otel.trace.AnyValue;
import stroom.pathways.shared.otel.trace.KeyValue;
import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.pathways.shared.otel.trace.Span;
import stroom.pathways.shared.otel.trace.Trace;
import stroom.pathways.shared.pathway.AnyBoolean;
import stroom.pathways.shared.pathway.BooleanValue;
import stroom.pathways.shared.pathway.Constraint;
import stroom.pathways.shared.pathway.ConstraintValue;
import stroom.pathways.shared.pathway.IntegerRange;
import stroom.pathways.shared.pathway.IntegerSet;
import stroom.pathways.shared.pathway.IntegerValue;
import stroom.pathways.shared.pathway.NanoTimeRange;
import stroom.pathways.shared.pathway.PathKey;
import stroom.pathways.shared.pathway.PathNode;
import stroom.pathways.shared.pathway.PathNodeList;
import stroom.pathways.shared.pathway.Regex;
import stroom.pathways.shared.pathway.StringSet;
import stroom.pathways.shared.pathway.StringValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TraceValidator implements TraceProcessor {

    private final Comparator<Span> spanComparator;
    private final PathKeyFactory pathKeyFactory;

    public TraceValidator(final Comparator<Span> spanComparator,
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
        walk(trace, root, node, map);
    }

    private void walk(final Trace trace,
                      final Span parentSpan,
                      final PathNode parentNode,
                      final Map<String, Map<PathKey, PathNodeList>> map) {
        checkConstraints(parentNode, parentSpan);

        final List<Span> childSpans = trace.getChildren(parentSpan);
        final List<Span> sortedSpans = new ArrayList<>(childSpans);
        sortedSpans.sort(spanComparator);
        final PathKey pathKey = pathKeyFactory.create(sortedSpans);

        // Load inner map.
        final Map<PathKey, PathNodeList> innerMap = map.computeIfAbsent(parentNode.getUuid(), k ->
                parentNode
                        .getTargets()
                        .stream()
                        .collect(Collectors.toMap(PathNodeList::getPathKey, Function.identity())));

        final PathNodeList childNodes = innerMap.get(pathKey);
        if (childNodes == null) {
            throw new RuntimeException("Invalid path: " + parentNode + " " + pathKey);
        }

        // Follow the path deeper.
        for (int i = 0; i < childNodes.getNodes().size(); i++) {
            final PathNode target = childNodes.getNodes().get(i);
            final Span span = sortedSpans.get(i);
            walk(trace, span, target, map);
        }
    }


    private void checkConstraints(final PathNode pathNode, final Span span) {
        final PathNode.Builder pathNodeBuilder = pathNode.copy();

        final Map<String, Constraint> constraints = pathNode.getConstraints();
        if (constraints != null) {

            // Set or expand duration range.
            final Constraint durationConstraint = constraints.get("duration");
            if (durationConstraint != null) {
                final NanoTime startTime = NanoTime.fromString(span.getStartTimeUnixNano());
                final NanoTime endTime = NanoTime.fromString(span.getEndTimeUnixNano());
                final NanoTime duration = endTime.subtract(startTime);
                validateNanoTime("Duration", durationConstraint.getValue(), duration);
            }

            final Constraint flagsConstraint = constraints.get("flags");
            if (flagsConstraint != null) {
                validateInteger("Flags", flagsConstraint.getValue(), span.getFlags());
            }

            final Constraint kindConstraint = constraints.get("kind");
            if (kindConstraint != null) {
                validateStringConstraint("Kind", kindConstraint.getValue(), span.getKind().name());
            }

            // Create attribute sets.
            final Map<String, KeyValue> attributes = span
                    .getAttributes()
                    .stream()
                    .collect(Collectors.toMap(KeyValue::getKey, Function.identity()));

            constraints.forEach((k, v) -> {
                if (k.startsWith("attribute.")) {
                    final String attributeName = k.substring("attribute.".length());
                    final KeyValue keyValue = attributes.get(attributeName);
                    if (keyValue == null) {
                        // Check required attributes all exist.
                        if (!v.isOptional()) {
                            throw new RuntimeException("Missing required attribute: " + attributeName);
                        }
                    } else {
                        validateAttribute(v.getValue(), keyValue);
                    }
                }
            });
        }
    }

    private void validateNanoTime(final String key, final ConstraintValue constraint, final NanoTime value) {
        if (constraint instanceof final NanoTimeRange nanoTimeRange) {
            if (nanoTimeRange.getMin().isGreaterThan(value)) {
                throw new RuntimeException(key + " less than expected");
            } else if (nanoTimeRange.getMax().isLessThan(value)) {
                throw new RuntimeException(key + " greater than expected");
            }
        }
    }

    private void validateInteger(final String key, final ConstraintValue constraint, final int value) {
        if (constraint instanceof final IntegerValue integerValue) {
            if (!integerValue.validate(value)) {
                throw new RuntimeException(key + " not equal");
            }
        } else if (constraint instanceof final IntegerSet integerSet) {
            if (!integerSet.validate(value)) {
                throw new RuntimeException(key + " not equal");
            }
        } else if (constraint instanceof final IntegerRange integerRange) {
            if (integerRange.getMin() > value) {
                throw new RuntimeException(key + " less than expected");
            } else if (integerRange.getMax() < value) {
                throw new RuntimeException(key + " greater than expected");
            }
        }
    }

    private void validateBooleanConstraint(final String key,
                                           final ConstraintValue constraint,
                                           final boolean value) {
        if (constraint instanceof final BooleanValue booleanValue) {
            if (!booleanValue.validate(value)) {
                throw new RuntimeException(key + " not equal");
            }
        } else if (constraint instanceof final AnyBoolean booleanSet) {
            if (!booleanSet.validate(value)) {
                throw new RuntimeException(key + " not equal");
            }
        }
    }

    private void validateStringConstraint(final String key,
                                          final ConstraintValue constraint,
                                          final String value) {
        if (constraint instanceof final StringValue stringValue) {
            if (!stringValue.validate(value)) {
                throw new RuntimeException(key + " not equal");
            }
        } else if (constraint instanceof final StringSet stringSet) {
            if (!stringSet.validate(value)) {
                throw new RuntimeException(key + " not equal");
            }
        } else if (constraint instanceof final Regex stringPattern) {
            // TODO : Cache
            final Pattern pattern = Pattern.compile(stringPattern.getValue());
            if (!pattern.matcher(value).find()) {
                throw new RuntimeException(key + " not equal");
            }
        }
    }

    private void validateAttribute(final ConstraintValue constraint,
                                   final KeyValue keyValue) {
        final AnyValue value = keyValue.getValue();

        if (value.getStringValue() != null) {
            validateStringConstraint(keyValue.getKey(), constraint, value.getStringValue());
        } else if (value.getBoolValue() != null) {
            validateBooleanConstraint(keyValue.getKey(), constraint, value.getBoolValue());
        } else if (value.getIntValue() != null) {
            validateInteger(keyValue.getKey(), constraint, value.getIntValue());
        }
        // TODO : Add validation for other attribute types.

    }
}
