package stroom.security.shared;

import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class VerifyApiKeyRequest {

    @JsonProperty
    private final String apiKey;
    @JsonProperty
    private final AppPermissionSet requiredAppPermissions;

    @JsonCreator
    public VerifyApiKeyRequest(
            @JsonProperty("apiKey") final String apiKey,
            @JsonProperty("requiredAppPermissions") final AppPermissionSet requiredAppPermissions) {

        this.apiKey = Objects.requireNonNull(apiKey);
        this.requiredAppPermissions = NullSafe.requireNonNullElseGet(
                requiredAppPermissions,
                AppPermissionSet::empty);
    }

    public VerifyApiKeyRequest(final String apiKey) {
        this.apiKey = Objects.requireNonNull(apiKey);
        this.requiredAppPermissions = AppPermissionSet.empty();
    }

    public String getApiKey() {
        return apiKey;
    }

    public AppPermissionSet getRequiredAppPermissions() {
        return requiredAppPermissions;
    }

    @Override
    public String toString() {
        return "VerifyApiKeyRequest{" +
               // Just output the prefix bit
               "apiKey='" + getPrefix(apiKey) +
               ", requiredAppPermissions=" + requiredAppPermissions +
               '}';
    }

    private String getPrefix(final String apiKey) {
        if (apiKey == null) {
            return null;
        } else if (apiKey.length() < 15) {
            return apiKey;
        } else {
            try {
                return apiKey.substring(0, 15);
            } catch (final Exception e) {
                return "ERROR";
            }
        }
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final VerifyApiKeyRequest that = (VerifyApiKeyRequest) object;
        return Objects.equals(apiKey, that.apiKey) && Objects.equals(requiredAppPermissions,
                that.requiredAppPermissions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(apiKey, requiredAppPermissions);
    }
}
