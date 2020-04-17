export interface PageResponse {
  offset: number;
  length: number;
  total?: number;
  exact: boolean;
}

export interface ResultPage<T> {
  values: [T];
  pageResponse: PageResponse;
}