package stroom.data.store.impl.fs.shared;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @Type(value = AwsBasicCredentials.class, name = "basic"),
        @Type(value = AwsSessionCredentials.class, name = "session"),
        @Type(value = AwsProfileCredentials.class, name = "profile"),
        @Type(value = AwsWebCredentials.class, name = "web")
})
public interface AwsCredentials {

}
