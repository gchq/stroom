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
import java.util.Map;
import java.util.UUID;

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
        final Header[] headers = new Header[attributeMap.size()];
        int i = 0;
        for (Map.Entry<String, String> entry : attributeMap.entrySet()) {
            headers[i++] = new Header(entry.getKey(), entry.getValue());
        }
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
