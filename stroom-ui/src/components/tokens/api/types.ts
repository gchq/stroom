import { Filter, SortingRule } from "react-table";

export interface TokenConfig {
  defaultApiKeyExpiryInMinutes: number;
}

export interface Token {
  id: string;
  version: number;
  createTimeMs: number;
  updateTimeMs: number;
  createUser: string;
  updateUser: string;

  userEmail: string;
  tokenType: string;
  data: string;
  expiresOnMs: number;
  comments: string;
  enabled: boolean;
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
