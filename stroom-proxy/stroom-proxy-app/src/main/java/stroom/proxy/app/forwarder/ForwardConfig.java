package stroom.proxy.app.forwarder;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ForwardHttpPostConfig.class, name = "post"),
        @JsonSubTypes.Type(value = ForwardFileConfig.class, name = "file")
})
public interface ForwardConfig {

    boolean isEnabled();

    String getName();
}
