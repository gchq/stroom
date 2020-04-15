import { DocRefType } from "../DocumentEditors/useDocumentApi/types/base";
import { ExpressionOperatorType } from "../ExpressionBuilder/types";

export interface LimitsType {
  streamCount: number;
  eventCount: number;
  durationMs: number;
}

export interface QueryDataType {
  dataSource: DocRefType;
  expression: ExpressionOperatorType;
  limits?: LimitsType;
}

export interface StreamTaskType {
  // Most important data, probably
  filterName: string;
  pipelineName: string;
  pipelineId: number;
  trackerMs: number;
  trackerPercent: number;
  lastPollAge: string;
  taskCount: number;
  priority: number;
  streamCount: number;
  eventCount: number;
  status: string;
  enabled: boolean;
  filter: QueryDataType;

  // supporting data
  filterId?: number;
  createUser?: string;
  createdOn?: number;
  updateUser?: string;
  updatedOn?: number;
  minStreamId?: number;
  minEventId?: number;
}

export interface StreamTasksResponseType {
  streamTasks: StreamTaskType[];
  totalStreamTasks: number;
}
