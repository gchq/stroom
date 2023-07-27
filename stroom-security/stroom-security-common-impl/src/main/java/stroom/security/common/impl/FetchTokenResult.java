package stroom.security.common.impl;

import stroom.security.openid.api.TokenResponse;

import org.jose4j.jwt.JwtClaims;

public record FetchTokenResult(
        TokenResponse tokenResponse,
        JwtClaims jwtClaims) {

}
