import { useCallback } from "react";
import { useHttpClient2 } from "lib/useHttpClient";
import useUrlFactory from "lib/useUrlFactory";

export interface ValidateSessionResponse {
  valid: boolean;
  userId: string;
  redirectUri: string;
}

interface StroomSessionResource {
  validateSession: (redirectUri: string) => Promise<ValidateSessionResponse>;
}

export const useStroomSessionResource = (): StroomSessionResource => {
  const { httpGet } = useHttpClient2();
  const { apiUrl } = useUrlFactory();
  const resource = apiUrl("/stroomSession/v1/noauth/validateSession");

  const validateSession = useCallback(
    (redirectUri: string) =>
      httpGet(`${resource}?redirect_uri=` + encodeURIComponent(redirectUri)),
    [resource, httpGet],
  );

  return {
    validateSession,
  };
};
