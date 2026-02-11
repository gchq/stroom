package stroom.credentials.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Represents the secret part of credentials.
 */
@JsonPropertyOrder({
        "uuid",
        "accessToken"
})
@JsonInclude(Include.NON_NULL)
public final class AccessTokenSecret implements Secret {

    @JsonProperty
    private final String accessToken;

    @JsonCreator
    public AccessTokenSecret(@JsonProperty("accessToken") final String accessToken) {
        this.accessToken = accessToken;
    }

    public String getAccessToken() {
        return accessToken;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AccessTokenSecret that = (AccessTokenSecret) o;
        return Objects.equals(accessToken, that.accessToken);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accessToken);
    }
}
