// Get token config
import { useEffect, useState } from "react";
import { PasswordPolicyConfig } from "api/stroom";
import { useStroomApi } from "lib/useStroomApi";

export const usePasswordPolicy = (): PasswordPolicyConfig => {
  const [passwordPolicyConfig, setPasswordPolicyConfig] = useState<
    PasswordPolicyConfig
  >(undefined);

  const { exec } = useStroomApi();
  useEffect(() => {
    console.log("Fetching password policy config");

    exec(
      (api) => api.authentication.fetchPasswordPolicy(),
      (passwordPolicyConfig: PasswordPolicyConfig) =>
        setPasswordPolicyConfig(passwordPolicyConfig),
    );
  }, [exec]);
  return passwordPolicyConfig;
};
