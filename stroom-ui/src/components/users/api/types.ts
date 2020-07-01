export enum Direction {
  ASCENDING,
  DESCENDING,
}

export interface Sort {
  field: string;
  direction?: Direction;
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

export interface UserSearchRequest extends BaseCriteria {
  quickFilter?: string;
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
