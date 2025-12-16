package stroom.credentials.shared;

import stroom.ai.shared.KeyStoreType;
import stroom.util.shared.ResourceKey;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Represents the secret part of credentials.
 * All fields can be null except for UUID.
 */
@JsonPropertyOrder({
        "keyStoreType",
        "keyStorePassword",
        "resourceKey"
})
@JsonInclude(Include.NON_NULL)
public final class KeyStoreSecret implements Secret {

    @JsonProperty
    private final KeyStoreType keyStoreType;
    @JsonProperty
    private final String keyStorePassword;
    @JsonProperty
    private final ResourceKey resourceKey;

    @JsonCreator
    public KeyStoreSecret(
            @JsonProperty("keyStoreType") final KeyStoreType keyStoreType,
            @JsonProperty("keyStorePassword") final String keyStorePassword,
            @JsonProperty("resourceKey") final ResourceKey resourceKey) {
        this.keyStoreType = keyStoreType;
        this.keyStorePassword = keyStorePassword;
        this.resourceKey = resourceKey;
    }

    public KeyStoreType getKeyStoreType() {
        return keyStoreType;
    }

    public String getKeyStorePassword() {
        return keyStorePassword;
    }

    public ResourceKey getResourceKey() {
        return resourceKey;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final KeyStoreSecret that = (KeyStoreSecret) o;
        return keyStoreType == that.keyStoreType &&
               Objects.equals(keyStorePassword, that.keyStorePassword) &&
               Objects.equals(resourceKey, that.resourceKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyStoreType, keyStorePassword, resourceKey);
    }
}
