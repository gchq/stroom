package stroom.aws.s3.shared;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public class AwsDefaultCredentials implements AwsCredentials {

    public AwsDefaultCredentials() {
    }
}
