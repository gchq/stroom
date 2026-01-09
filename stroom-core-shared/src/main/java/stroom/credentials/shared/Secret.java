package stroom.credentials.shared;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = UsernamePasswordSecret.class, name = "usernamePassword"),
        @JsonSubTypes.Type(value = AccessTokenSecret.class, name = "accessToken"),
        @JsonSubTypes.Type(value = KeyPairSecret.class, name = "keyPair"),
        @JsonSubTypes.Type(value = KeyStoreSecret.class, name = "keyStore")
})
public sealed interface Secret permits
        UsernamePasswordSecret,
        AccessTokenSecret,
        KeyPairSecret,
        KeyStoreSecret {

}
