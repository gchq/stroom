package stroom.planb.impl.serde.keyprefix;

import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValString;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({"val", "tags"})
@JsonInclude(Include.NON_NULL)
public class KeyPrefix {

    @JsonProperty
    private final Val val;
    @JsonProperty
    private final List<Tag> tags;

    @JsonCreator
    public KeyPrefix(@JsonProperty("val") final Val val,
                     @JsonProperty("tags") final List<Tag> tags) {
        this.val = val;
        this.tags = tags;
    }

    public static KeyPrefix create(final Val val) {
        return new KeyPrefix(val, null);
    }

    public static KeyPrefix create(final String name) {
        return new KeyPrefix(ValString.create(name), null);
    }

    public static KeyPrefix create(final List<Tag> tags) {
        final StringBuilder sb = new StringBuilder();
        for (final Tag tag : tags) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(tag.getTagName());
            sb.append("=");
            sb.append(tag.getTagValue());
        }
        return new KeyPrefix(ValString.create(sb.toString()), tags);
    }

    public Val getVal() {
        return val;
    }

    public List<Tag> getTags() {
        return tags;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final KeyPrefix keyPrefix = (KeyPrefix) o;
        return Objects.equals(val, keyPrefix.val) &&
               Objects.equals(tags, keyPrefix.tags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(val, tags);
    }

    @Override
    public String toString() {
        return val.toString();
    }
}
