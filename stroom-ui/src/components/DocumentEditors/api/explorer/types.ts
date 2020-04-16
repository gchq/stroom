export interface SearchProps {
  term?: string;
  docRefType?: string;
  pageOffset?: number;
  pageSize?: number;
}

export interface HasAuditInfo {
  createTimeMs: number;
  updateTimeMs: number;
  createUser: string;
  updateUser: string;
}
