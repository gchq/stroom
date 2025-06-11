package stroom.aws.s3.shared;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @Type(value = AwsAnonymousCredentials.class, name = "anonymous"),
        @Type(value = AwsBasicCredentials.class, name = "basic"),
        @Type(value = AwsDefaultCredentials.class, name = "default"),
        @Type(value = AwsEnvironmentVariableCredentials.class, name = "environment"),
        @Type(value = AwsProfileCredentials.class, name = "profile"),
        @Type(value = AwsSessionCredentials.class, name = "session"),
        @Type(value = AwsSystemPropertyCredentials.class, name = "system"),
        @Type(value = AwsWebCredentials.class, name = "web")
})
public sealed interface AwsCredentials permits
        AwsAnonymousCredentials,
        AwsBasicCredentials,
        AwsDefaultCredentials,
        AwsEnvironmentVariableCredentials,
        AwsProfileCredentials,
        AwsSessionCredentials,
        AwsSystemPropertyCredentials,
        AwsWebCredentials {
    // TODO: Make sealed class when GWT supports them.
}
