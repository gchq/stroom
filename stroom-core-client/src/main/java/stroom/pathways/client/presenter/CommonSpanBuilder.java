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

import stroom.pathways.shared.otel.trace.KeyValue;
import stroom.pathways.shared.otel.trace.Span;
import stroom.pathways.shared.otel.trace.SpanEvent;
import stroom.pathways.shared.otel.trace.SpanKind;
import stroom.pathways.shared.otel.trace.SpanLink;
import stroom.pathways.shared.otel.trace.SpanStatus;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CommonSpanBuilder {

    private List<KeyValue> referenceAttributes;
    private final Map<String, KeyValue> attributeMap = new HashMap<>();
    private String traceId;
    private String spanId;
    private String traceState;
    private String parentSpanId;
    private Integer flags;
    private String name;
    private SpanKind kind;
    private String startTimeUnixNano;
    private String endTimeUnixNano;
    private Integer droppedAttributesCount;
    private List<SpanEvent> events;
    private Integer droppedEventsCount;
    private List<SpanLink> links;
    private Integer droppedLinksCount;
    private SpanStatus status;

    public void add(final Span incoming) {
        if (referenceAttributes == null) {
            referenceAttributes = NullSafe.list(incoming.getAttributes());

            if (incoming.getAttributes() != null) {
                incoming.getAttributes().forEach(kv -> attributeMap.put(kv.getKey(), kv));
            }


            traceId = incoming.getTraceId();
            spanId = incoming.getSpanId();
            traceState = incoming.getTraceState();


            parentSpanId = incoming.getParentSpanId();
            flags = incoming.getFlags();
            name = incoming.getName();
            kind = incoming.getKind();

            // TODO : Merge time ranges.
            startTimeUnixNano = incoming.getStartTimeUnixNano();
            endTimeUnixNano = incoming.getEndTimeUnixNano();

            droppedAttributesCount = incoming.getDroppedAttributesCount();
            // TODO : Make common events.
            events = null;
            droppedEventsCount = incoming.getDroppedEventsCount();
            // TODO : Make common links.
            links = null;
            droppedLinksCount = incoming.getDroppedLinksCount();
            status = incoming.getStatus();


        } else {
            final Map<String, KeyValue> attributeMap2 = new HashMap<>();
            incoming.getAttributes().forEach(kv -> attributeMap2.put(kv.getKey(), kv));

            for (final String key : new HashSet<>(attributeMap.keySet())) {
                final KeyValue keyValue1 = attributeMap.get(key);
                final KeyValue keyValue2 = attributeMap2.get(key);
                if (keyValue2 == null || !Objects.equals(keyValue2.getValue(), keyValue1.getValue())) {
                    attributeMap.remove(key);
                }
            }

            traceId = common(traceId, incoming.getTraceId());
            spanId = common(spanId, incoming.getSpanId());
            traceState = common(traceState, incoming.getTraceState());


            parentSpanId = common(parentSpanId, incoming.getParentSpanId());
            flags = common(flags, incoming.getFlags());
            name = common(name, incoming.getName());
            kind = common(kind, incoming.getKind());

            // TODO : Merge time ranges.
            startTimeUnixNano = null;
            endTimeUnixNano = null;


            droppedAttributesCount = common(droppedAttributesCount,
                    incoming.getDroppedAttributesCount());
            // TODO : Make common events.
            events = null;
            droppedEventsCount = common(droppedEventsCount,
                    incoming.getDroppedEventsCount());
            // TODO : Make common links.
            links = null;
            droppedLinksCount = common(droppedLinksCount, incoming.getDroppedLinksCount());
            status = common(status, incoming.getStatus());
        }
    }

    private <T> T common(final T string1, final T string2) {
        if (Objects.equals(string1, string2)) {
            return string1;
        }
        return null;
    }

    public Span build() {
        final List<KeyValue> attributes = new ArrayList<>();
        for (final KeyValue keyValue : referenceAttributes) {
            if (attributeMap.containsKey(keyValue.getKey())) {
                attributes.add(keyValue);
            }
        }


        return new Span(traceId,
                spanId,
                traceState,
                parentSpanId,
                flags == null
                        ? -1
                        : flags,
                name,
                kind,
                startTimeUnixNano,
                endTimeUnixNano,
                attributes,
                droppedAttributesCount == null
                        ? -1
                        : droppedAttributesCount,
                events,
                droppedEventsCount == null
                        ? -1
                        : droppedEventsCount,
                links,
                droppedLinksCount == null
                        ? -1
                        : droppedLinksCount,
                status);
    }
}
