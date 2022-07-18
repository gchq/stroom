package stroom.proxy.app.event;

import stroom.meta.api.AttributeMap;
import stroom.proxy.app.event.model.Event;
import stroom.proxy.app.event.model.Header;
import stroom.util.date.DateUtil;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class EventSerialiser {

    private final ObjectMapper mapper;

    public EventSerialiser() {
        mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, false);
        mapper.setSerializationInclusion(Include.NON_NULL);
    }

    public String serialise(final String requestUuid,
                            final String proxyId,
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
                requestUuid,
                proxyId,
                feedKey.feed(),
                feedKey.type(),
                DateUtil.createNormalDateTimeString(),
                headers,
                data
        );

        return mapper.writeValueAsString(event);
    }
}
