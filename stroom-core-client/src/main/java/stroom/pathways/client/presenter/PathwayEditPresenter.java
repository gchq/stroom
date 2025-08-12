/*
 * Copyright 2017 Crown Copyright
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

import stroom.pathways.client.presenter.PathwayEditPresenter.PathwayEditView;
import stroom.pathways.shared.otel.trace.KeyValue;
import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.pathways.shared.otel.trace.Span;
import stroom.pathways.shared.pathway.PathNode;
import stroom.pathways.shared.pathway.Pathway;
import stroom.util.shared.NullSafe;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;

import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.HTML;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.validation.ValidationException;

public class PathwayEditPresenter extends MyPresenterWidget<PathwayEditView> {

    private Pathway pathway;
    private Element selected;
    private final Map<String, PathNode> nodeMap = new HashMap<>();

    @Inject
    public PathwayEditPresenter(final EventBus eventBus, final PathwayEditView view) {
        super(eventBus, view);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(getView().getDetails().addClickHandler(e -> {
            final Element target = e.getNativeEvent().getEventTarget().cast();
            if (target != null) {
                final Element node = ElementUtil.findParent(target, element ->
                        NullSafe.isNonBlankString(element.getAttribute("uuid")), 3);
                if (node != null) {
                    final String uuid = node.getAttribute("uuid");
//                    AlertEvent.fireInfo(this, uuid, null);

                    final PathNode pathNode = nodeMap.get(uuid);
                    // Calculate min, max, average time.
                    NanoTime min = NanoTime.ofSeconds(Long.MAX_VALUE);
                    NanoTime max = NanoTime.ZERO;
                    NanoTime sum = NanoTime.ZERO;
                    int count = pathNode.getSpans().size();

                    final HtmlBuilder spanDetails = new HtmlBuilder();
                    final CommonSpanBuilder commonSpanBuilder = new CommonSpanBuilder();
                    for (final Span span : pathNode.getSpans()) {
                        commonSpanBuilder.add(span);

                        final NanoTime startTime = NanoTime.fromString(span.getStartTimeUnixNano());
                        final NanoTime endTime = NanoTime.fromString(span.getEndTimeUnixNano());
                        final NanoTime duration = endTime.diff(startTime);

                        if (min.isGreaterThan(duration)) {
                            min = duration;
                        }
                        if (max.isLessThan(duration)) {
                            max = duration;
                        }
                        sum = sum.add(duration);
                    }


                    final Span commonSpan = commonSpanBuilder.build();
                    final SafeHtml durations = getDurationsHtml(min, max, sum, count);
                    spanDetails.div(div -> {
                        div.append(durations);
                    });
                    spanDetails.div(div -> {
                        div.append(getSpanHtml(commonSpan));
                    });


//                    getView().setConstraints(getDurationsHtml(min, max, sum, count));
                    getView().setSpans(HtmlBuilder.builder().div(d -> d.append(spanDetails.toSafeHtml()),
                            Attribute.className("pathway-spans")).toSafeHtml());

                    if (!Objects.equals(selected, node)) {
                        if (selected != null) {
                            selected.removeClassName("pathway-nodeName--selected");
                        }
                        selected = node;
                        selected.addClassName("pathway-nodeName--selected");
                    }
                }
            }
        }));
    }

    private SafeHtml getSpanHtml(final Span span) {

        final HtmlBuilder hb = new HtmlBuilder();
        if (span.getTraceId() != null) {
            append(hb, "Trace Id", span.getTraceId());
        }
        if (span.getSpanId() != null) {
            append(hb, "Span Id", span.getSpanId());
        }
        if (span.getTraceState() != null) {
            append(hb, "Trace State", span.getTraceState());
        }
        if (span.getParentSpanId() != null) {
            append(hb, "Parent Span Id", span.getParentSpanId());
        }
        if (span.getFlags() != -1) {
            append(hb, "Flags", String.valueOf(span.getFlags()));
        }
        if (span.getName() != null) {
            append(hb, "Name", span.getName());
        }
        if (span.getKind() != null) {
            append(hb, "Kind", String.valueOf(span.getKind()));
        }
//        if (span.getstartTimeUnixNano() != null) {
//
//        if (span.getendTimeUnixNano() != null) {

        if (!NullSafe.isEmptyCollection(span.getAttributes())) {
            append(hb, "Attributes", "");
            hb.div(div -> {
                for (final KeyValue keyValue : span.getAttributes()) {
                    append(div, keyValue.getKey(), keyValue.getValue().toString());
                }
            }, Attribute.className("pathway-attributes"));
        }
        if (span.getDroppedAttributesCount() != -1) {
            append(hb, "Dropped Attributes Count", String.valueOf(span.getDroppedAttributesCount()));
        }
//        if (span.getevents() != null) {

        if (span.getDroppedEventsCount() != -1) {
            append(hb, "Dropped Events Count", String.valueOf(span.getDroppedEventsCount()));
        }
//        if (span.getlinks() != null) {

        if (span.getDroppedLinksCount() != -1) {
            append(hb, "Dropped Links Count", String.valueOf(span.getDroppedLinksCount()));
        }
        if (span.getStatus() != null) {
            append(hb, "Status", "");
            hb.div(div -> {
                if (span.getStatus().getCode() != null) {
                    append(div, "Code", span.getStatus().getCode().toString());
                }
                if (span.getStatus().getMessage() != null) {
                    append(div, "Message", span.getStatus().getMessage());
                }
            }, Attribute.className("pathway-attributes"));
        }

        return hb.toSafeHtml();
    }

    private void append(final HtmlBuilder hb, final String key, final String value) {
        hb.div(div -> {
            div.div(k -> k.append(key + ":"), Attribute.className("pathway-attributeKey"));
            div.div(v -> v.append(value), Attribute.className("pathway-attributeValue"));
        }, Attribute.className("pathway-attribute"));
    }

    private SafeHtml getDurationsHtml(final NanoTime min,
                                      final NanoTime max,
                                      final NanoTime sum,
                                      final int count) {
        final HtmlBuilder hb = new HtmlBuilder();
        append(hb, "Calls", Integer.toString(count));
        append(hb, "Min Duration", min.toString());
        append(hb, "Max Duration", max.toString());
        append(hb, "Average Duration",
                new NanoTime(sum.getSeconds() / count, sum.getNanos() / count).toString());
        return hb.toSafeHtml();
    }

    public void read(final Pathway pathway) {
        this.pathway = pathway;
        getView().setName(pathway.getName());
        getView().setDetails(new PathwayTree().build(pathway));

        addNode(pathway.getRoot());
    }

    private void addNode(final PathNode node) {
        nodeMap.put(node.getUuid(), node);
        node.getTargets().forEach(target -> {
            target.getNodes().forEach(this::addNode);
        });
    }

    public Pathway write() {
        String name = getView().getName();
        name = name.trim();

        if (name.isEmpty()) {
            throw new ValidationException("A pathway must have a name");
        }

        return new Pathway(name, pathway.getPathKey(), pathway.getRoot());
    }

    public void show(final String caption, final HidePopupRequestEvent.Handler handler) {
        final PopupSize popupSize = PopupSize.resizable(800, 800);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption(caption)
                .onShow(e -> getView().focus())
                .onHideRequest(handler)
                .fire();
    }

    public interface PathwayEditView extends View, Focus {

        String getName();

        void setName(final String name);

        void setDetails(SafeHtml html);

        void setConstraints(SafeHtml html);

        void setSpans(SafeHtml html);

        HTML getDetails();
    }
}
