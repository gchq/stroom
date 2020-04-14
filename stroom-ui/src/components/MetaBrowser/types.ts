export type DataStatus = "UNLOCKED" | "LOCKED" | "DELETED";

export interface PageRequest {
  pageOffset?: number;
  pageSize?: number;
}

export interface PageResponse {
  offset: number;
  length: number;
  total: number;
  exact: boolean;
}

export interface Meta {
  id: number;
  feedName: string;
  typeName: string;
  processorUuid: number;
  pipelineUuid: string;
  parentDataId: number;
  processTaskId: number;
  status: DataStatus;
  statusMs: number;
  createMs: number;
  effectiveMs: number;
}

export interface MetaRow {
  meta: Meta;
  attributes?: {
    [key: string]: string;
  };
}

export interface StreamAttributeMapResult {
  pageResponse: PageResponse;
  streamAttributeMaps: MetaRow[];
}
