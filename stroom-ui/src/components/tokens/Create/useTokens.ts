import { useTokenState } from "./useTokenState";
import { useApi } from "../api/";
import { Token } from "../api/types";
import { useCallback } from "react";
import useAppNavigation from "lib/useAppNavigation";

const useTokens = () => {
  const { token, setEnabled, setToken } = useTokenState();

  const {
    toggleState: toggleStateApi,
    fetchApiKey: fetchApiKeyApi,
    createToken: createTokenApi,
  } = useApi();

  const toggleEnabledState = useCallback(
    (tokenId: number, nextState: boolean) => {
      toggleStateApi(tokenId, nextState).then(() => setEnabled(nextState));
    },
    [toggleStateApi, setEnabled],
  );

  const fetchApiKey = useCallback(
    (tokenId: string) => {
      fetchApiKeyApi(tokenId).then((apiKey: Token) => {
        setToken(apiKey);
      });
    },
    [fetchApiKeyApi, setToken],
  );

  const {
    nav: { goToApiKey },
  } = useAppNavigation();
  const createToken = useCallback(
    (email: string, expiryDate: string) => {
      createTokenApi(email, expiryDate).then((newToken: Token) => {
        goToApiKey(newToken.id + "");
      });
    },
    [createTokenApi, goToApiKey],
  );

  return {
    token,
    toggleEnabledState,
    createToken,
    fetchApiKey,
  };
};

export default useTokens;
