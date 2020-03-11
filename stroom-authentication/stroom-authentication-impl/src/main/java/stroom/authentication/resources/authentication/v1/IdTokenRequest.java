package stroom.authentication.resources.authentication.v1;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.NotNull;

@ApiModel(description = "A request to exchange an access code with an id token. This is the final step in the OpenID authentication flow and must be a back-channel call, i.e. the client secret should never be sent to a browser.")
public class IdTokenRequest {
    @NotNull
    @ApiModelProperty(value = "The client id. This is a string that uniquely identifies the client.", required = true)
    private String clientId;

    @NotNull
    @ApiModelProperty(value = "The client's secret. This is a cryptographically random string that authenticates the client.", required = true)
    private String clientSecret;

    @NotNull
    @ApiModelProperty(value = "The access code to exchange for an id token. This will have been provided to the RP after successfully authenticating, or if they were already authenticated", required = true)
    private String accessCode;

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String getAccessCode() {
        return accessCode;
    }
}
