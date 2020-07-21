// Get token config
import { useEffect, useState } from "react";
import { PasswordPolicyConfig } from "./api/types";
import { useAuthenticationResource } from "./api";

export const usePasswordPolicy = () => {
  const [passwordPolicyConfig, setPasswordPolicyConfig] = useState<
    PasswordPolicyConfig
  >(undefined);
  const { fetchPasswordPolicyConfig } = useAuthenticationResource();

  useEffect(() => {
    console.log("Fetching password policy config");
    fetchPasswordPolicyConfig().then(
      (passwordPolicyConfig: PasswordPolicyConfig) => {
        setPasswordPolicyConfig(passwordPolicyConfig);
      },
    );
  }, [fetchPasswordPolicyConfig]);

  return passwordPolicyConfig;
};
