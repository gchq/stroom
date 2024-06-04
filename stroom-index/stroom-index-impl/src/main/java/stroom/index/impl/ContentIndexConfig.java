package stroom.index.impl;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
public class ContentIndexConfig extends AbstractConfig implements IsStroomConfig {

    private final boolean enabled;

    public ContentIndexConfig() {
        enabled = false;
    }

    @JsonCreator
    public ContentIndexConfig(@JsonProperty("enabled") final boolean enabled) {
        this.enabled = enabled;
    }

    @JsonProperty("enabled")
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ContentIndexConfig that = (ContentIndexConfig) o;
        return enabled == that.enabled;
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled);
    }

    @Override
    public String toString() {
        return "ContentIndexConfig{" +
                "enabled=" + enabled +
                '}';
    }
}
