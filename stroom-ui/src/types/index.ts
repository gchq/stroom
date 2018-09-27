export interface ItemWithId {
  uuid: string;
}

export interface DocRefType extends ItemWithId {
  type: string;
  name?: string;
}

export interface DocRefInfoType {
  docRef: DocRefType;
  createTime: number;
  updateTime: number;
  createUser: string;
  updateUser: string;
  otherInfo: string;
}

export interface Tree<T extends ItemWithId> {
  children?: Array<T & Tree<T>>;
}

export interface DocRefTree extends DocRefType, Tree<DocRefType> {}

export interface TWithLineage<T extends ItemWithId> {
  node: Tree<T> & T;
  lineage: Array<T>;
}

export interface DocRefWithLineage extends TWithLineage<DocRefType> {}

export type DocRefConsumer = (d: DocRefType) => void;

export interface SelectOptionType {
  text: string;
  value: string;
}

export interface OptionType {
  text: string;
  value: string;
}

export interface Dictionary {
  docRef?: DocRefType;
  description?: string;
  data?: string;
  imports?: Array<DocRefType>;
}

export type ConditionType =
  | "EQUALS"
  | "IN"
  | "IN_DICTIONARY"
  | "CONTAINS"
  | "BETWEEN"
  | "GREATER_THAN"
  | "GREATER_THAN_OR_EQUAL_TO"
  | "LESS_THAN"
  | "LESS_THAN_OR_EQUAL_TO";

export interface DataSourceFieldType {
  type: "ID" | "FIELD" | "NUMERIC_FIELD" | "DATE_FIELD";
  name: string;
  queryable: boolean;
  conditions: Array<ConditionType>;
}

export interface DataSourceType {
  fields: Array<DataSourceFieldType>;
}

export interface ExpressionItem extends ItemWithId {
  type: string;
  enabled: boolean;
}

export interface ExpressionOperator
  extends ExpressionItem,
    Tree<ExpressionTerm | ExpressionOperator> {
  type: "operator";
  op: "AND" | "OR" | "NOT";
}

export interface ExpressionTerm extends ExpressionItem {
  type: "term";
  field?: string;
  condition?: ConditionType;
  value?: any;
  dictionary?: Dictionary | null;
}

export interface ElementDefinition {
  type: string;
  category: string;
  roles: Array<string>;
  icon: string;
}

export type ElementDefinitions = Array<ElementDefinition>;
export type ElementDefinitionsByCategory = {
  [category: string]: ElementDefinition;
};
export type ElementDefinitionsByType = { [type: string]: ElementDefinition };

export interface ElementPropertyType {
  [propName: string]: {
    elementType: ElementDefinition;
    name: string;
    type: string;
    description: string;
    defaultValue: string;
    pipelineReference: boolean;
    docRefTypes: Array<string> | null;
    displayPriority: number;
  };
}
export interface ElementPropertyTypes {
  [pipelineElementType: string]: ElementPropertyType;
}

export interface ControlledInput<T> {
  onChange: (value: T) => any;
  value: T;
}
export interface AddRemove<T> {
  add: Array<T>;
  remove: Array<T>;
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
  pipelines: Array<DocRefType>;
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

export interface PipelineModelType {
  docRef: DocRefType;
  description: string;
  parentPipeline?: DocRefType;
  configStack: Array<PipelineDataType>;
  merged: PipelineDataType;
}

export interface PipelineAsTreeType {
  uuid: string;
  type: string;
  children: Array<PipelineAsTreeType>;
}

export interface PageResponse {
  offset: number;
  length: number;
  total:number;
  exact: boolean;
}

export enum DataStatus {
  UNLOCKED= "Unlocked",
    LOCKED="Locked",
    DELETED="Deleted";
}

export interface DataItem {
  id: number;
  feedName:string;
  typeName:string;
  pipelineUuid: string;
  parentDataId: number;
  processTaskId:number;
  processorId:number;
  status:DataStatus;
  statusMs: number;
  createMs: number;
  effectiveMs: number;
}

export interface DataRow {
  data: DataItem;
  attributes: {
    [key: string]: string;
  }
}

export interface StreamAttributeMapResult {
  pageResponse: PageResponse;
  streamAttributeMaps: Array<DataRow>;
}
