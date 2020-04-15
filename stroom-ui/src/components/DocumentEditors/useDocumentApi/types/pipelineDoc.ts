import { DocumentBase, DocRefType } from "./base";

export interface AddRemove<T> {
  add?: T[];
  remove?: T[];
}

export interface SourcePipeline {
  pipeline: DocRefType;
}

export interface PipelineLinkType {
  from: string;
  to: string;
}

export interface PipelineReferenceType {
  element: string;
  name: string;
  pipeline: DocRefType;
  feed: DocRefType;
  streamType: string;
  source: SourcePipeline;
}

export interface PipelinePropertyValue {
  string?: string | null;
  integer?: number | null;
  long?: number | null;
  boolean?: boolean | null;
  entity?: DocRefType | null;
}

export interface PipelinePropertyType {
  source?: SourcePipeline;
  element: string;
  name: string;
  value: PipelinePropertyValue;
}

export interface PipelineSearchCriteriaType {
  filter: string;
  pageOffset: number;
  pageSize: number;
}

export interface PipelineSearchResultType {
  total: number;
  pipelines: DocRefType[];
}

export interface PipelineElementType {
  id: string;
  type: string;
}

export interface PipelineDataType {
  elements: AddRemove<PipelineElementType>;
  properties: AddRemove<PipelinePropertyType>;
  pipelineReferences: AddRemove<PipelineReferenceType>;
  links: AddRemove<PipelineLinkType>;
}

export interface PipelineDocumentType extends DocumentBase<"Pipeline"> {
  description?: string;
  parentPipeline?: DocRefType;
  configStack: PipelineDataType[];
  merged: PipelineDataType;
}
