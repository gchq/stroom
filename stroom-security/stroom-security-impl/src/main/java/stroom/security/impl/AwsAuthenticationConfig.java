package stroom.security.impl;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Config specific to using Stroom(-Proxy)? with the AWS' Cognito identity
 * provider or with AWS' Application Load Balancer (either in Verify or Pass-through mode)
 */
@JsonPropertyOrder(alphabetic = true)
public class AwsAuthenticationConfig extends AbstractConfig implements IsStroomConfig {

    // TODO refactor AbstractOpenIdConfig to

//    private final  ;

}
