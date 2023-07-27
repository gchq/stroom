package stroom.query.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Collections;
import java.util.List;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class Suggestions {
    public static final Suggestions EMPTY = new Suggestions(Collections.emptyList(), false);

    @JsonProperty
    private final List<String> list;
    @JsonProperty
    private final boolean cacheable;

    public Suggestions(final List<String> list) {
        this.list = list;
        this.cacheable = false;
    }

    @JsonCreator
    public Suggestions(@JsonProperty("list") final List<String> list,
                       @JsonProperty("cacheable") final boolean cacheable) {
        this.list = list;
        this.cacheable = cacheable;
    }

    public List<String> getList() {
        return list;
    }

    public boolean isCacheable() {
        return cacheable;
    }
}
