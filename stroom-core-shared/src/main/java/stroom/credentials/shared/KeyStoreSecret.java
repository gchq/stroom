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
        "uuid",
        "keyStoreType",
        "keyStorePassword",
        "resourceKey"
})
@JsonInclude(Include.NON_NULL)
public final class KeyStoreSecret implements Secret {

    @JsonProperty
    private final String uuid;
    @JsonProperty
    private final KeyStoreType keyStoreType;
    @JsonProperty
    private final String keyStorePassword;
    @JsonProperty
    private final ResourceKey resourceKey;

    @JsonCreator
    public KeyStoreSecret(
            @JsonProperty("uuid") final String uuid,
            @JsonProperty("keyStoreType") final KeyStoreType keyStoreType,
            @JsonProperty("keyStorePassword") final String keyStorePassword,
            @JsonProperty("resourceKey") final ResourceKey resourceKey) {
        this.uuid = uuid;
        this.keyStoreType = keyStoreType;
        this.keyStorePassword = keyStorePassword;
        this.resourceKey = resourceKey;
    }

    public String getUuid() {
        return uuid;
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
        return Objects.equals(uuid, that.uuid) &&
               keyStoreType == that.keyStoreType &&
               Objects.equals(keyStorePassword, that.keyStorePassword) &&
               Objects.equals(resourceKey, that.resourceKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid, keyStoreType, keyStorePassword, resourceKey);
    }
}
