package stroom.proxy.app.event;

import stroom.meta.api.AttributeMap;
import stroom.proxy.app.event.model.Event;
import stroom.proxy.app.event.model.Header;
import stroom.proxy.app.handler.ReceiptId;
import stroom.util.date.DateUtil;
import stroom.util.json.JsonUtil;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class EventSerialiser {

    public String serialise(final ReceiptId receiptId,
                            final FeedKey feedKey,
                            final AttributeMap attributeMap,
                            final String data) throws IOException {
        final List<Header> headers = attributeMap
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new Header(entry.getKey(), entry.getValue()))
                .toList();

        final Event event = new Event(
                0,
                receiptId.eventId(),
                receiptId.proxyId(),
                feedKey.feed(),
                feedKey.type(),
                DateUtil.createNormalDateTimeString(),
                headers,
                data
        );

        return JsonUtil.writeValueAsString(event, false);
    }
}
