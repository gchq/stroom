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

package stroom.pathways.client.presenter;

import stroom.pathways.client.presenter.ConstraintEditPresenter.ConstraintEditView;
import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.pathways.shared.pathway.AnyBoolean;
import stroom.pathways.shared.pathway.AnyTypeValue;
import stroom.pathways.shared.pathway.BooleanValue;
import stroom.pathways.shared.pathway.Constraint;
import stroom.pathways.shared.pathway.Constraint.Builder;
import stroom.pathways.shared.pathway.ConstraintValueType;
import stroom.pathways.shared.pathway.DoubleRange;
import stroom.pathways.shared.pathway.DoubleSet;
import stroom.pathways.shared.pathway.DoubleValue;
import stroom.pathways.shared.pathway.IntegerRange;
import stroom.pathways.shared.pathway.IntegerSet;
import stroom.pathways.shared.pathway.IntegerValue;
import stroom.pathways.shared.pathway.NanoTimeRange;
import stroom.pathways.shared.pathway.NanoTimeValue;
import stroom.pathways.shared.pathway.Regex;
import stroom.pathways.shared.pathway.StringSet;
import stroom.pathways.shared.pathway.StringValue;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.ValidationException;

public class ConstraintEditPresenter extends MyPresenterWidget<ConstraintEditView> {

    private Constraint constraint;
//    private Element selected;
//    private final Map<String, PathNode> nodeMap = new HashMap<>();

    @Inject
    public ConstraintEditPresenter(final EventBus eventBus, final ConstraintEditView view) {
        super(eventBus, view);
    }

    @Override
    protected void onBind() {
        super.onBind();
//        registerHandler(getView().getDetails().addClickHandler(e -> {
//            final Element target = e.getNativeEvent().getEventTarget().cast();
//            if (target != null) {
//                final Element node = ElementUtil.findParent(target, element ->
//                        NullSafe.isNonBlankString(element.getAttribute("uuid")), 3);
//                if (node != null) {
//                    final String uuid = node.getAttribute("uuid");
////                    AlertEvent.fireInfo(this, uuid, null);
//
//                    final PathNode pathNode = nodeMap.get(uuid);
//                    // Calculate min, max, average time.
//                    NanoTime min = NanoTime.ofSeconds(Long.MAX_VALUE);
//                    NanoTime max = NanoTime.ZERO;
//                    NanoTime sum = NanoTime.ZERO;
//                    final int count = pathNode.getSpans().size();
//
//                    final HtmlBuilder spanDetails = new HtmlBuilder();
//                    final CommonSpanBuilder commonSpanBuilder = new CommonSpanBuilder();
//                    for (final Span span : pathNode.getSpans()) {
//                        commonSpanBuilder.add(span);
//
//                        final NanoTime startTime = NanoTime.fromString(span.getStartTimeUnixNano());
//                        final NanoTime endTime = NanoTime.fromString(span.getEndTimeUnixNano());
//                        final NanoTime duration = endTime.diff(startTime);
//
//                        if (min.isGreaterThan(duration)) {
//                            min = duration;
//                        }
//                        if (max.isLessThan(duration)) {
//                            max = duration;
//                        }
//                        sum = sum.add(duration);
//                    }
//
//
//                    final Span commonSpan = commonSpanBuilder.build();
//                    final SafeHtml durations = getDurationsHtml(min, max, sum, count);
//                    spanDetails.div(div -> {
//                        div.append(durations);
//                    });
//                    spanDetails.div(div -> {
//                        div.append(getSpanHtml(commonSpan));
//                    });
//
//                    getView().setConstraints(HtmlBuilder.builder().div(d -> d.append(getConstraintsHtml(pathNode)),
//                            Attribute.className("constraint-constraints")).toSafeHtml());
//
//                    getView().setSpans(HtmlBuilder.builder().div(d -> d.append(spanDetails.toSafeHtml()),
//                            Attribute.className("constraint-spans")).toSafeHtml());
//
//                    if (!Objects.equals(selected, node)) {
//                        if (selected != null) {
//                            selected.removeClassName("constraint-nodeName--selected");
//                        }
//                        selected = node;
//                        selected.addClassName("constraint-nodeName--selected");
//                    }
//                }
//            }
//        }));
    }
//
//    private SafeHtml getConstraintsHtml(final PathNode pathNode) {
//        final HtmlBuilder hb = new HtmlBuilder();
//
//        final Map<String, Constraint> constraints = pathNode.getConstraints();
//        if (constraints != null) {
//            if (constraints.getDuration() != null) {
//                append(hb, "Duration", constraints.getDuration().toString());
//            }
//            if (constraints.getFlags() != null) {
//                append(hb, "Flag", constraints.getFlags().toString());
//            }
//            if (constraints.getKind() != null) {
//                append(hb, "Kind", constraints.getKind().toString());
//            }
//            append(hb, "Attributes", "");
//
//            final Map<String, ConstraintValue> requiredAttributes = NullSafe.map(constraints.getRequiredAttributes());
//            final Map<String, ConstraintValue> optionalAttributes = NullSafe.map(constraints.getOptionalAttributes());
//            final Set<String> keys = new HashSet<>(requiredAttributes.keySet());
//            keys.addAll(optionalAttributes.keySet());
//            final List<String> sortedKeys = keys.stream().sorted().collect(Collectors.toList());
//            for (final String key : sortedKeys) {
//                final ConstraintValue required = requiredAttributes.get(key);
//                if (required != null) {
//                    hb.div(div ->
//                            append(div, key, required.toString()), Attribute.className("constraint-attributes"));
//                }
//                final ConstraintValue optional = optionalAttributes.get(key);
//                if (optional != null) {
//                    hb.div(div ->
//                            append(div, key, optional.toString()),
//                            Attribute.className("constraint-attributes-optional"));
//                }
//            }
//        }
//        return hb.toSafeHtml();
//    }
//
//    private SafeHtml getSpanHtml(final Span span) {
//        final HtmlBuilder hb = new HtmlBuilder();
//        if (span.getTraceId() != null) {
//            append(hb, "Trace Id", span.getTraceId());
//        }
//        if (span.getSpanId() != null) {
//            append(hb, "Span Id", span.getSpanId());
//        }
//        if (span.getTraceState() != null) {
//            append(hb, "Trace State", span.getTraceState());
//        }
//        if (span.getParentSpanId() != null) {
//            append(hb, "Parent Span Id", span.getParentSpanId());
//        }
//        if (span.getFlags() != -1) {
//            append(hb, "Flags", String.valueOf(span.getFlags()));
//        }
//        if (span.getName() != null) {
//            append(hb, "Name", span.getName());
//        }
//        if (span.getKind() != null) {
//            append(hb, "Kind", String.valueOf(span.getKind()));
//        }
////        if (span.getstartTimeUnixNano() != null) {
////
////        if (span.getendTimeUnixNano() != null) {
//
//        if (!NullSafe.isEmptyCollection(span.getAttributes())) {
//            append(hb, "Attributes", "");
//            hb.div(div -> {
//                for (final KeyValue keyValue : span.getAttributes()) {
//                    append(div, keyValue.getKey(), keyValue.getValue().toString());
//                }
//            }, Attribute.className("constraint-attributes"));
//        }
//        if (span.getDroppedAttributesCount() != -1) {
//            append(hb, "Dropped Attributes Count", String.valueOf(span.getDroppedAttributesCount()));
//        }
////        if (span.getevents() != null) {
//
//        if (span.getDroppedEventsCount() != -1) {
//            append(hb, "Dropped Events Count", String.valueOf(span.getDroppedEventsCount()));
//        }
////        if (span.getlinks() != null) {
//
//        if (span.getDroppedLinksCount() != -1) {
//            append(hb, "Dropped Links Count", String.valueOf(span.getDroppedLinksCount()));
//        }
//        if (span.getStatus() != null) {
//            append(hb, "Status", "");
//            hb.div(div -> {
//                if (span.getStatus().getCode() != null) {
//                    append(div, "Code", span.getStatus().getCode().toString());
//                }
//                if (span.getStatus().getMessage() != null) {
//                    append(div, "Message", span.getStatus().getMessage());
//                }
//            }, Attribute.className("constraint-attributes"));
//        }
//
//        return hb.toSafeHtml();
//    }
//
//    private void append(final HtmlBuilder hb, final String key, final String value) {
//        hb.div(div -> {
//            div.div(k -> k.append(key + ":"), Attribute.className("constraint-attributeKey"));
//            div.div(v -> v.append(value), Attribute.className("constraint-attributeValue"));
//        }, Attribute.className("constraint-attribute"));
//    }
//
//    private SafeHtml getDurationsHtml(final NanoTime min,
//                                      final NanoTime max,
//                                      final NanoTime sum,
//                                      final int count) {
//        final HtmlBuilder hb = new HtmlBuilder();
//        append(hb, "Calls", Integer.toString(count));
//        append(hb, "Min Duration", min.toString());
//        append(hb, "Max Duration", max.toString());
//        append(hb, "Average Duration",
//                new NanoTime(sum.getSeconds() / count, sum.getNanos() / count).toString());
//        return hb.toSafeHtml();
//    }

    public void read(final Constraint constraint) {
        this.constraint = constraint;
        getView().setName(constraint.getName());
        getView().setType(constraint.getValue().valueType());

        switch (constraint.getValue().valueType()) {
            case ANY -> {
                getView().setValue("");
            }
            case DURATION_VALUE -> {
                final NanoTime nanoTime = ((NanoTimeValue) constraint.getValue()).getValue();
                getView().setValue(String.valueOf(nanoTime.getNanos() + (nanoTime.getSeconds() * 1000000000)));
            }
            case DURATION_RANGE -> {
                final NanoTimeRange nanoTimeRange = ((NanoTimeRange) constraint.getValue());
                getView().setValue((nanoTimeRange.getMin().getNanos() +
                                    (nanoTimeRange.getMin().getSeconds() * 1000000000)) +
                                   "," +
                                   (nanoTimeRange.getMax().getNanos() +
                                    (nanoTimeRange.getMax().getSeconds() * 1000000000)));
            }
            case STRING -> {
                getView().setValue(constraint.getValue().toString());
            }
            case STRING_SET -> {
                final StringSet stringValue = (StringSet) constraint.getValue();
                getView().setValue(stringValue.getSet().stream().collect(Collectors.joining(",")));
            }
            case REGEX -> {
                getView().setValue(constraint.getValue().toString());
            }
            case BOOLEAN -> {
                getView().setValue(constraint.getValue().toString());
            }
            case ANY_BOOLEAN -> {
                getView().setValue("");
            }
            case INTEGER -> {
                getView().setValue(constraint.getValue().toString());
            }
            case INTEGER_SET -> {
                final IntegerSet set = (IntegerSet) constraint.getValue();
                getView().setValue(set.getSet().stream().map(String::valueOf)
                        .collect(Collectors.joining(",")));
            }
            case INTEGER_RANGE -> {
                final IntegerRange range = (IntegerRange) constraint.getValue();
                getView().setValue(range.getMin() + "," + range.getMax());

                getView().setValue(constraint.getValue().toString());
            }
            case DOUBLE -> {
                getView().setValue(constraint.getValue().toString());
            }
            case DOUBLE_SET -> {
                final DoubleSet set = (DoubleSet) constraint.getValue();
                getView().setValue(set.getSet().stream().map(String::valueOf)
                        .collect(Collectors.joining(",")));
            }
            case DOUBLE_RANGE -> {
                final DoubleRange range = (DoubleRange) constraint.getValue();
                getView().setValue(range.getMin() + "," + range.getMax());
            }
        }

        getView().setOptional(constraint.isOptional());

//        this.selected = null;

//        getView().setDetails(SafeHtmlUtils.EMPTY_SAFE_HTML);
//        getView().setConstraints(SafeHtmlUtils.EMPTY_SAFE_HTML);
//        getView().setSpans(SafeHtmlUtils.EMPTY_SAFE_HTML);
//
//        getView().setName(constraint.getName());
//        getView().setDetails(new ConstraintTree().build(constraint));
//
//        addNode(constraint.getRoot());
    }

//    private void addNode(final PathNode node) {
//        nodeMap.put(node.getUuid(), node);
//        node.getTargets().forEach(target -> {
//            target.getNodes().forEach(this::addNode);
//        });
//    }

    public Constraint write() {
        String name = getView().getName();
        name = name.trim();

        if (name.isEmpty()) {
            throw new ValidationException("A constraint must have a name");
        }

        final Builder builder = constraint.copy();
        builder.name(name);

        final String value = getView().getValue();
        final ConstraintValueType type = getView().getType();
        switch (type) {
            case ANY -> {
                builder.value(new AnyTypeValue());
            }
            case DURATION_VALUE -> {
                final long l = Long.parseLong(value);
                builder.value(new NanoTimeValue(NanoTime.ofNanos(l)));
            }
            case DURATION_RANGE -> {
                final List<Long> list = Arrays
                        .stream(value.split(","))
                        .map(Long::parseLong)
                        .collect(Collectors.toList());
                builder.value(new NanoTimeRange(NanoTime.ofNanos(list.get(0)), NanoTime.ofNanos(list.get(1))));
            }
            case STRING -> {
                builder.value(new StringValue(value));
            }
            case STRING_SET -> {
                builder.value(new StringSet(Arrays
                        .stream(value.split(","))
                        .collect(Collectors.toSet())));
            }
            case REGEX -> {
                builder.value(new Regex(value));
            }
            case BOOLEAN -> {
                builder.value(new BooleanValue(Boolean.parseBoolean(value)));
            }
            case ANY_BOOLEAN -> {
                builder.value(new AnyBoolean());
            }
            case INTEGER -> {
                builder.value(new IntegerValue(Integer.parseInt(value)));
            }
            case INTEGER_SET -> {
                builder.value(new IntegerSet(Arrays
                        .stream(value.split(","))
                        .map(Integer::parseInt)
                        .collect(Collectors.toSet())));
            }
            case INTEGER_RANGE -> {
                final List<Integer> list = Arrays
                        .stream(value.split(","))
                        .map(Integer::parseInt)
                        .collect(Collectors.toList());
                builder.value(new IntegerRange(list.get(0), list.get(1)));
            }
            case DOUBLE -> {
                builder.value(new DoubleValue(Double.parseDouble(value)));
            }
            case DOUBLE_SET -> {
                builder.value(new DoubleSet(Arrays
                        .stream(value.split(","))
                        .map(Double::parseDouble)
                        .collect(Collectors.toSet())));
            }
            case DOUBLE_RANGE -> {
                final List<Double> list = Arrays
                        .stream(value.split(","))
                        .map(Double::parseDouble)
                        .collect(Collectors.toList());
                builder.value(new DoubleRange(list.get(0), list.get(1)));
            }
        }

        builder.optional(getView().isOptional());

        return builder.build();
    }

    public void show(final String caption, final HidePopupRequestEvent.Handler handler) {
        final PopupSize popupSize = PopupSize.resizable(800, 400);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption(caption)
                .onShow(e -> getView().focus())
                .onHideRequest(handler)
                .fire();
    }

    public interface ConstraintEditView extends View, Focus {

        String getName();

        void setName(String name);

        ConstraintValueType getType();

        void setType(ConstraintValueType type);

        String getValue();

        void setValue(String value);

        boolean isOptional();

        void setOptional(boolean optional);
    }
}
