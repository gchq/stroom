package stroom.pathways.impl;

import stroom.pathways.shared.PathwaysDoc;
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
import stroom.pathways.shared.pathway.PathNodeSequence;
import stroom.pathways.shared.pathway.Regex;
import stroom.pathways.shared.pathway.StringSet;
import stroom.pathways.shared.pathway.StringValue;
import stroom.util.shared.Severity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TraceValidator implements TraceWalker {

    private final Comparator<Span> spanComparator;
    private final PathKeyFactory pathKeyFactory;

    public TraceValidator(final Comparator<Span> spanComparator,
                          final PathKeyFactory pathKeyFactory) {
        this.spanComparator = spanComparator;
        this.pathKeyFactory = pathKeyFactory;
    }

    @Override
    public void process(final Trace trace,
                        final Map<PathKey, PathNode> roots,
                        final MessageReceiver messageReceiver,
                        final PathwaysDoc pathwaysDoc) {
        final Map<PathKey, Map<String, Map<PathKey, PathNodeSequence>>> maps = new HashMap<>();
        final Span root = trace.root();
        final PathKey pathKey = pathKeyFactory.create(Collections.singletonList(root));
        final PathNode node = roots.get(pathKey);
        if (node == null) {
            messageReceiver.log(Severity.ERROR, () -> "Invalid path: " + pathKey);
        } else {
            final Map<String, Map<PathKey, PathNodeSequence>> map = maps.computeIfAbsent(pathKey, k -> new HashMap<>());
            walk(trace, root, node, map, messageReceiver);
        }
    }

    private void walk(final Trace trace,
                      final Span parentSpan,
                      final PathNode parentNode,
                      final Map<String, Map<PathKey, PathNodeSequence>> map,
                      final MessageReceiver messageReceiver) {
        checkConstraints(parentNode, parentSpan, messageReceiver);

        final List<Span> childSpans = trace.children(parentSpan);
        final List<Span> sortedSpans = new ArrayList<>(childSpans);
        sortedSpans.sort(spanComparator);
        final PathKey pathKey = pathKeyFactory.create(sortedSpans);

        // Load inner map.
        final Map<PathKey, PathNodeSequence> innerMap = map.computeIfAbsent(parentNode.getUuid(), k ->
                parentNode
                        .getTargets()
                        .stream()
                        .collect(Collectors.toMap(PathNodeSequence::getPathKey, Function.identity())));

        final PathNodeSequence childNodes = innerMap.get(pathKey);
        if (childNodes == null) {
            messageReceiver.log(Severity.ERROR, () -> "Invalid path: " + parentNode + " " + pathKey);
        } else {

            // Follow the path deeper.
            for (int i = 0; i < childNodes.getNodes().size(); i++) {
                final PathNode target = childNodes.getNodes().get(i);
                final Span span = sortedSpans.get(i);
                walk(trace, span, target, map, messageReceiver);
            }
        }
    }

    private void checkConstraints(final PathNode pathNode,
                                  final Span span,
                                  final MessageReceiver messageReceiver) {
        final PathNode.Builder pathNodeBuilder = pathNode.copy();

        final Map<String, Constraint> constraints = pathNode.getConstraints();
        if (constraints != null) {

            // Set or expand duration range.
            final Constraint durationConstraint = constraints.get("duration");
            if (durationConstraint != null) {
                final NanoTime startTime = NanoTime.fromString(span.getStartTimeUnixNano());
                final NanoTime endTime = NanoTime.fromString(span.getEndTimeUnixNano());
                final NanoTime duration = endTime.subtract(startTime);
                validateNanoTime(() -> pathNode.getPath() + " Duration",
                        durationConstraint.getValue(),
                        duration,
                        messageReceiver);
            }

            final Constraint flagsConstraint = constraints.get("flags");
            if (flagsConstraint != null) {
                validateInteger(() -> pathNode.getPath() + " Flags",
                        flagsConstraint.getValue(),
                        span.getFlags(),
                        messageReceiver);
            }

            final Constraint kindConstraint = constraints.get("kind");
            if (kindConstraint != null) {
                validateStringConstraint(() -> pathNode.getPath() + " Kind",
                        kindConstraint.getValue(),
                        span.getKind().name(),
                        messageReceiver);
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
                            messageReceiver.log(Severity.ERROR, () -> "Missing required attribute: " +
                                                                      pathNode.getPath() + " " +
                                                                      attributeName);
                        }
                    } else {
                        validateAttribute(() -> pathNode.getPath() + " " + attributeName,
                                v.getValue(),
                                keyValue,
                                messageReceiver);
                    }
                }
            });
        }
    }

    private void validateNanoTime(final Supplier<String> location,
                                  final ConstraintValue constraint,
                                  final NanoTime value,
                                  final MessageReceiver messageReceiver) {
        if (constraint instanceof final NanoTimeRange nanoTimeRange) {
            if (nanoTimeRange.getMin().isGreaterThan(value)) {
                messageReceiver.log(Severity.ERROR, () -> location.get() + " '" + value + "' less than expected");
            } else if (nanoTimeRange.getMax().isLessThan(value)) {
                messageReceiver.log(Severity.ERROR, () -> location.get() + " '" + value + "' greater than expected");
            }
        }
    }

    private void validateInteger(final Supplier<String> location,
                                 final ConstraintValue constraint,
                                 final int value,
                                 final MessageReceiver messageReceiver) {
        if (constraint instanceof final IntegerValue integerValue) {
            if (!integerValue.validate(value)) {
                messageReceiver.log(Severity.ERROR, () -> location.get() + " '" + value + "' not equal");
            }
        } else if (constraint instanceof final IntegerSet integerSet) {
            if (!integerSet.validate(value)) {
                messageReceiver.log(Severity.ERROR, () -> location.get() + " '" + value + "' not equal");
            }
        } else if (constraint instanceof final IntegerRange integerRange) {
            if (integerRange.getMin() > value) {
                messageReceiver.log(Severity.ERROR, () -> location.get() + " '" + value + "' less than expected");
            } else if (integerRange.getMax() < value) {
                messageReceiver.log(Severity.ERROR, () -> location.get() + " '" + value + "' greater than expected");
            }
        }
    }

    private void validateBooleanConstraint(final Supplier<String> location,
                                           final ConstraintValue constraint,
                                           final boolean value,
                                           final MessageReceiver messageReceiver) {
        if (constraint instanceof final BooleanValue booleanValue) {
            if (!booleanValue.validate(value)) {
                messageReceiver.log(Severity.ERROR, () -> location.get() + " '" + value + "' not equal");
            }
        } else if (constraint instanceof final AnyBoolean booleanSet) {
            if (!booleanSet.validate(value)) {
                messageReceiver.log(Severity.ERROR, () -> location.get() + " '" + value + "' not equal");
            }
        }
    }

    private void validateStringConstraint(final Supplier<String> location,
                                          final ConstraintValue constraint,
                                          final String value,
                                          final MessageReceiver messageReceiver) {
        if (constraint instanceof final StringValue stringValue) {
            if (!stringValue.validate(value)) {
                messageReceiver.log(Severity.ERROR, () -> location.get() + " '" + value + "' not equal");
            }
        } else if (constraint instanceof final StringSet stringSet) {
            if (!stringSet.validate(value)) {
                messageReceiver.log(Severity.ERROR, () -> location.get() + " '" + value + "' not equal");
            }
        } else if (constraint instanceof final Regex stringPattern) {
            // TODO : Cache
            final Pattern pattern = Pattern.compile(stringPattern.getValue());
            if (!pattern.matcher(value).find()) {
                messageReceiver.log(Severity.ERROR, () -> location.get() + " '" + value + "' not equal");
            }
        }
    }

    private void validateAttribute(final Supplier<String> location,
                                   final ConstraintValue constraint,
                                   final KeyValue keyValue,
                                   final MessageReceiver messageReceiver) {
        final AnyValue value = keyValue.getValue();

        if (value.getStringValue() != null) {
            validateStringConstraint(location, constraint, value.getStringValue(), messageReceiver);
        } else if (value.getBoolValue() != null) {
            validateBooleanConstraint(location, constraint, value.getBoolValue(), messageReceiver);
        } else if (value.getIntValue() != null) {
            validateInteger(location, constraint, value.getIntValue(), messageReceiver);
        }
        // TODO : Add validation for other attribute types.

    }
}
