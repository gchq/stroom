import * as React from "react";
import * as jwtDecode from "jwt-decode";

import useHttpQueryParam from "lib/useHttpQueryParam";

export const useTokenValidityCheck = () => {
  const [isTokenMissing, setMissingToken] = React.useState(false);
  const [isTokenInvalid, setInvalidToken] = React.useState(false);
  const [isTokenExpired, setExpiredToken] = React.useState(false);

  const token = useHttpQueryParam("token");

  React.useEffect(() => {
    let missingToken = false;
    let invalidToken = false;
    let expiredToken = false;

    // Validate token
    if (!token) {
      missingToken = true;
    } else {
      try {
        const decodedToken: { exp: number } = jwtDecode(token);
        const now = new Date().getTime() / 1000;
        expiredToken = decodedToken.exp <= now;
      } catch (err) {
        invalidToken = true;
      }
    }

    setMissingToken(missingToken);
    setInvalidToken(invalidToken);
    setExpiredToken(expiredToken);
  }, [token, setMissingToken, setInvalidToken, setExpiredToken]);
  return { isTokenMissing, isTokenInvalid, isTokenExpired };
};
