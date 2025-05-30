package stroom.planb.impl.db.histogram;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({"tags"})
@JsonInclude(Include.NON_NULL)
public class Tags {

    @JsonProperty
    private final List<Tag> tags;

    @JsonCreator
    public Tags(@JsonProperty("tags") final List<Tag> tags) {
        this.tags = tags;
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
        final Tags tags1 = (Tags) o;
        return Objects.equals(tags, tags1.tags);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(tags);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        for (final Tag tag : tags) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(tag.getTagName());
            sb.append("=");
            sb.append(tag.getTagValue().toString());
        }
        return sb.toString();
    }

    public static class Builder {

        private List<Tag> tags;

        public Builder() {
        }

        public Builder(final Tags tags) {
            this.tags = tags.tags;
        }

        public Builder tags(final List<Tag> tags) {
            this.tags = tags;
            return this;
        }

        public Tags build() {
            return new Tags(tags);
        }
    }
}
