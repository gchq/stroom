package stroom.credentials.shared;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = AccessTokenSecret.class, name = "accessToken"),
        @JsonSubTypes.Type(value = KeyStoreSecret.class, name = "keyStore"),
        @JsonSubTypes.Type(value = SshKeySecret.class, name = "sshKey"),
        @JsonSubTypes.Type(value = UsernamePasswordSecret.class, name = "usernamePassword")
})
public sealed interface Secret permits
        AccessTokenSecret,
        KeyStoreSecret,
        SshKeySecret,
        UsernamePasswordSecret {

}
