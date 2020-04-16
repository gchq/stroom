import { Filter, SortingRule } from "react-table";

export interface StoreState {
  lastReadToken: any;
  isCreating: any;
  errorMessage: string;
  matchingAutoCompleteResults: string[];
  show: string;
}

export interface Token {
  id: string;
  enabled: boolean;
  userEmail: string;
  expiresOn: string;
  issuedOn: string;
  issuedByUser: string;
  updatedOn: string;
  updatedByUser: string;
  token: string;
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
