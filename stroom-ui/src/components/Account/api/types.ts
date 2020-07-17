import { Account } from "../types";

export interface Sort {
  field: string;
  direction?: "ASCENDING" | "DESCENDING";
  ignoreCase?: boolean;
}

export interface PageRequest {
  offset: number;
  length: number; // Page size to use, e.g. 10 is 10 records.
}

export interface BaseCriteria {
  pageRequest?: PageRequest;
  sortList?: Sort[];
}

export interface SearchAccountRequest extends BaseCriteria {
  quickFilter?: string;
}

export interface CreateAccountRequest {
  firstName: string;
  lastName: string;
  userId: string;
  email: string;
  comments: string;
  password: string;
  confirmPassword: string;
  forcePasswordChange: boolean;
  neverExpires: boolean;
}

export interface UpdateAccountRequest {
  account: Account;
  password: string;
  confirmPassword: string;
}

export interface SearchTokenRequest extends BaseCriteria {
  quickFilter?: string;
}

export interface CreateTokenRequest {
  userId: string;
  tokenType: "user" | "api" | "email_reset";
  expiresOnMs: number;
  comments: string;
  enabled: boolean;
}

export interface PageResponse {
  offset: number;
  length: number;
  total?: number;
  exact: boolean;
}

export interface ResultPage<T> {
  values: T[];
  pageResponse: PageResponse;
}

export interface TokenConfig {
  defaultApiKeyExpiryInMinutes: number;
}
