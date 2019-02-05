export interface HasUuid {
  uuid: string;
}

export interface HasAuditInfo {
  createTimeMs: number;
  updateTimeMs: number;
  createUser: string;
  updateUser: string;
}

export interface User extends HasUuid {
  name: string;
  isGroup: boolean;
}

export interface IndexVolumeGroup extends HasAuditInfo {
  name: string;
}

export interface IndexVolume extends HasAuditInfo {
  id: number;
  path: string;
  nodeName: string;
  bytesLimit: number;
  bytesUsed: number;
  bytesFree: number;
  bytesTotal: number;
  statusMs: number;
}

export type DocRefTypeList = Array<string>;

export interface IndexVolumeGroupMembership {
  volumeId: number;
  groupName: string;
}

export interface DocRefType extends HasUuid {
  type: string;
  name?: string;
}

export const copyDocRef = (input: DocRefType): DocRefType => {
  return {
    uuid: input.uuid,
    type: input.type,
    name: input.name
  };
};

export interface DocRefInfoType {
  docRef: DocRefType;
  createTime: number;
  updateTime: number;
  createUser: string;
  updateUser: string;
  otherInfo: string;
}

export interface Tree<T> {
  children?: Array<T & Tree<T>>;
}

export interface DocRefTree extends DocRefType, Tree<DocRefType> {}

export interface TWithLineage<T extends HasUuid> {
  node: Tree<T> & T;
  lineage: Array<T>;
}

export interface DocRefWithLineage extends TWithLineage<DocRefType> {}

export type DocRefConsumer = (d: DocRefType) => void;

export interface SelectOptionType {
  value: string;
  label: string;
}

export type SelectOptionsType = Array<SelectOptionType>;

export interface OptionType {
  text: string;
  value: string;
}

export interface DictionaryUpdates {
  description?: string;
  data?: string;
  imports?: Array<DocRefType>;
}

export interface Dictionary extends DocRefType, DictionaryUpdates {}

export interface IndexUpdates {
  description?: string;
}

export interface IndexDoc extends DocRefType, IndexUpdates {}

export interface XsltUpdates {
  description?: string;
  data?: string;
}

export interface XsltDoc extends DocRefType, XsltUpdates {}

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

export const ConditionDisplayValues = {
  CONTAINS: "contains",
  EQUALS: "=",
  GREATER_THAN: ">",
  GREATER_THAN_OR_EQUAL_TO: ">=",
  LESS_THAN: "<",
  LESS_THAN_OR_EQUAL_TO: "<=",
  BETWEEN: "between",
  IN: "in",
  IN_DICTIONARY: "in dictionary"
};

export interface DataSourceFieldType {
  type: "ID" | "FIELD" | "NUMERIC_FIELD" | "DATE_FIELD";
  name: string;
  queryable: boolean;
  conditions: Array<ConditionType>;
}

export interface DataSourceType {
  fields: Array<DataSourceFieldType>;
}

export interface ExpressionItem {
  type: "operator" | "term";
  enabled: boolean;
}

export type OperatorType = "AND" | "OR" | "NOT";
export const OperatorTypeValues: Array<OperatorType> = ["AND", "OR", "NOT"];

export interface ExpressionOperatorType extends ExpressionItem {
  type: "operator";
  op: OperatorType;
  children: Array<ExpressionTermType | ExpressionOperatorType>;
}

export interface ExpressionTermType extends ExpressionItem {
  type: "term";
  field?: string;
  condition?: ConditionType;
  value?: any;
  dictionary?: Dictionary | null;
}

export interface ExpressionHasUuid extends ExpressionItem, HasUuid {}

export interface ExpressionOperatorWithUuid
  extends ExpressionOperatorType,
    HasUuid {
  enabled: boolean;
  children: Array<ExpressionOperatorWithUuid | ExpressionTermWithUuid>;
}

export interface ExpressionTermWithUuid extends ExpressionTermType, HasUuid {}

export interface ElementDefinition {
  type: string;
  category: string;
  roles: Array<string>;
  icon: string;
}

export type ElementDefinitions = Array<ElementDefinition>;
export type ElementDefinitionsByCategory = {
  [category: string]: Array<ElementDefinition>;
};
export type ElementDefinitionsByType = { [type: string]: ElementDefinition };

export interface ElementPropertyType {
  elementType: ElementDefinition;
  name: string;
  type: string;
  description: string;
  defaultValue: string;
  pipelineReference: boolean;
  docRefTypes: Array<string> | null;
  displayPriority: number;
}

export interface ElementPropertiesType {
  [propName: string]: ElementPropertyType;
}
export interface ElementPropertiesByElementIdType {
  [pipelineElementType: string]: ElementPropertiesType;
}

export interface ControlledInput<T> {
  onChange: (value: T) => any;
  value: T;
}
export interface AddRemove<T> {
  add?: Array<T>;
  remove?: Array<T>;
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
  description?: string;
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
  total: number;
  exact: boolean;
}

export enum PermissionInheritance {
  NONE = "None",
  SOURCE = "Source",
  DESTINATION = "Destination",
  COMBINED = "Combined"
}

export interface OffsetRange {
  offset: number;
  length: number;
}
export interface RowCount {
  count: number;
  exact: boolean;
}

export interface AbstractFetchDataResult {
  streamType: string;
  classification: string;
  streamRange: OffsetRange;
  streamRowCount: RowCount;
  pageRange: OffsetRange;
  pageRowCount: RowCount;
  availableChildStreamType: Array<string>;
}

export type Severity = "INFO" | "WARN" | "ERROR" | "FATAL";

export interface Marker {
  severity: Severity;
}
export interface FetchMarkerResult extends AbstractFetchDataResult {
  markers: Array<Marker>;
}

export interface FetchDataResult extends AbstractFetchDataResult {
  data: string;
  html: boolean;
}

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
  streamTasks: Array<StreamTaskType>;
  totalStreamTasks: number;
}

export interface StyledComponentProps {
  className?: string;
}

export enum Direction {
  UP = "up",
  DOWN = "down"
}
