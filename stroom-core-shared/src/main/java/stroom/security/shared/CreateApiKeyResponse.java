package stroom.security.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class CreateApiKeyResponse {

    // The actual api key
    @JsonProperty
    private final String apiKey;

    // The DB mapped object containing the hash of the api key
    @JsonProperty
    private final ApiKey hashedApiKey;

    @JsonCreator
    public CreateApiKeyResponse(@JsonProperty("apiKey") final String apiKey,
                                @JsonProperty("hashedApiKey") final ApiKey hashedApiKey) {
        this.apiKey = apiKey;
        this.hashedApiKey = hashedApiKey;
    }

    public ApiKey getHashedApiKey() {
        return hashedApiKey;
    }

    public String getApiKey() {
        return apiKey;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final CreateApiKeyResponse that = (CreateApiKeyResponse) object;
        return Objects.equals(apiKey, that.apiKey) && Objects.equals(hashedApiKey, that.hashedApiKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(apiKey, hashedApiKey);
    }

    @Override
    public String toString() {
        return "CreateApiKeyResponse{" +
                "apiKey='" + apiKey + '\'' +
                ", hashedApiKey=" + hashedApiKey +
                '}';
    }
}
