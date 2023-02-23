package stroom.query.common.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class ResultStoreSettings {

    @JsonProperty
    private final Lifespan searchProcessLifespan;
    @JsonProperty
    private final Lifespan storeLifespan;

    @JsonCreator
    public ResultStoreSettings(@JsonProperty("searchProcessLifespan") final Lifespan searchProcessLifespan,
                               @JsonProperty("storeLifespan") final Lifespan storeLifespan) {
        this.searchProcessLifespan = searchProcessLifespan;
        this.storeLifespan = storeLifespan;
    }

    public Lifespan getSearchProcessLifespan() {
        return searchProcessLifespan;
    }

    public Lifespan getStoreLifespan() {
        return storeLifespan;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ResultStoreSettings that = (ResultStoreSettings) o;
        return Objects.equals(searchProcessLifespan, that.searchProcessLifespan) && Objects.equals(
                storeLifespan,
                that.storeLifespan);
    }

    @Override
    public int hashCode() {
        return Objects.hash(searchProcessLifespan, storeLifespan);
    }

    @Override
    public String toString() {
        return "ResultStoreSettings{" +
                "searchProcessLifespan=" + searchProcessLifespan +
                ", storeLifespan=" + storeLifespan +
                '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Lifespan searchProcessLifespan;
        private Lifespan storeLifespan;

        public Builder() {
        }

        public Builder(final ResultStoreSettings resultStoreSettings) {
            this.searchProcessLifespan = resultStoreSettings.searchProcessLifespan;
            this.storeLifespan = resultStoreSettings.storeLifespan;
        }

        public Builder searchProcessLifespan(final Lifespan searchProcessLifespan) {
            this.searchProcessLifespan = searchProcessLifespan;
            return this;
        }

        public Builder storeLifespan(final Lifespan storeLifespan) {
            this.storeLifespan = storeLifespan;
            return this;
        }

        public ResultStoreSettings build() {
            return new ResultStoreSettings(
                    searchProcessLifespan,
                    storeLifespan);
        }
    }
}
