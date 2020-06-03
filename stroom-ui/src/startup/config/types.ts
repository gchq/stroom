import { CSSProperties } from "react";

export interface Config {
  allowPasswordResets?: boolean;
  dateFormat?: string;
  defaultApiKeyExpiryInMinutes?: string;
  theme?: CSSProperties;
}
