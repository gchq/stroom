package stroom.credentials.shared;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

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
@Schema(
        discriminatorProperty = "type",
        discriminatorMapping = {
                @DiscriminatorMapping(value = "accessToken", schema = AccessTokenSecret.class),
                @DiscriminatorMapping(value = "keyStore", schema = KeyStoreSecret.class),
                @DiscriminatorMapping(value = "sshKey", schema = SshKeySecret.class),
                @DiscriminatorMapping(value = "usernamePassword", schema = UsernamePasswordSecret.class)})
public sealed interface Secret permits
        AccessTokenSecret,
        KeyStoreSecret,
        SshKeySecret,
        UsernamePasswordSecret {

}
