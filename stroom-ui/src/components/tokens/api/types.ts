import { Filter, SortingRule } from "react-table";

export interface TokenConfig {
  defaultApiKeyExpiryInMinutes: number;
}

export interface Token {
  id?: number;
  version?: number;
  createTimeMs?: number;
  updateTimeMs?: number;
  createUser?: string;
  updateUser?: string;

  userId?: string;
  tokenType?: "user" | "api" | "email_reset";
  data?: string;
  expiresOnMs?: number;
  comments?: string;
  enabled?: boolean;
}

export interface SearchConfig {
  filters: Filter[];
  page: number;
  pageSize: number;
  sorting: SortingRule[];
}

export interface TokenSearchResponse {
  tokens: Token[];
  totalPages: number;
}
