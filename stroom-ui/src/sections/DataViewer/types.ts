import { PageResponse } from "../../types";
export type DataStatus = "UNLOCKED" | "LOCKED" | "DELETED";

export interface DataItem {
  id: number;
  feedName: string;
  typeName: string;
  pipelineUuid: string;
  parentDataId: number;
  processTaskId: number;
  processorId: number;
  status: DataStatus;
  statusMs: number;
  createMs: number;
  effectiveMs: number;
}

export interface DataRow {
  data: DataItem;
  attributes?: {
    [key: string]: string;
  };
}

export interface StreamAttributeMapResult {
  pageResponse: PageResponse;
  streamAttributeMaps: Array<DataRow>;
}
