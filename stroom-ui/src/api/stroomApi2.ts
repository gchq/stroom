/* tslint:disable */
/* eslint-disable */
/*
 * ---------------------------------------------------------------
 * ## THIS FILE WAS GENERATED VIA SWAGGER-TYPESCRIPT-API        ##
 * ##                                                           ##
 * ## AUTHOR: acacode                                           ##
 * ## SOURCE: https://github.com/acacode/swagger-typescript-api ##
 * ---------------------------------------------------------------
 */

export interface AbstractFetchDataResult {
  availableChildStreamTypes?: string[];
  classification?: string;
  feedName?: string;
  itemRange?: OffsetRange;
  sourceLocation?: SourceLocation;
  streamTypeName?: string;
  totalCharacterCount?: CountLong;
  totalItemCount?: CountLong;
  type: string;
}

export interface AbstractField {
  conditions?: (
    | "CONTAINS"
    | "EQUALS"
    | "GREATER_THAN"
    | "GREATER_THAN_OR_EQUAL_TO"
    | "LESS_THAN"
    | "LESS_THAN_OR_EQUAL_TO"
    | "BETWEEN"
    | "IN"
    | "IN_DICTIONARY"
    | "IN_FOLDER"
    | "IS_DOC_REF"
    | "IS_NULL"
    | "IS_NOT_NULL"
  )[];
  name?: string;
  queryable?: boolean;
  type?: string;
}

export interface Account {
  comments?: string;

  /** @format int64 */
  createTimeMs?: number;
  createUser?: string;
  email?: string;
  enabled?: boolean;
  firstName?: string;
  forcePasswordChange?: boolean;

  /** @format int32 */
  id?: number;
  inactive?: boolean;

  /** @format int64 */
  lastLoginMs?: number;
  lastName?: string;
  locked?: boolean;

  /** @format int32 */
  loginCount?: number;

  /** @format int32 */
  loginFailures?: number;
  neverExpires?: boolean;
  processingAccount?: boolean;

  /** @format int64 */
  reactivatedMs?: number;

  /** @format int64 */
  updateTimeMs?: number;
  updateUser?: string;
  userId?: string;

  /** @format int32 */
  version?: number;
}

export interface AccountResultPage {
  pageResponse?: PageResponse;
  values?: Account[];
}

export interface AcknowledgeSplashRequest {
  message?: string;
  version?: string;
}

export interface Activity {
  /** @format int64 */
  createTimeMs?: number;
  createUser?: string;
  details?: ActivityDetails;

  /** @format int32 */
  id?: number;
  json?: string;

  /** @format int64 */
  updateTimeMs?: number;
  updateUser?: string;
  userId?: string;

  /** @format int32 */
  version?: number;
}

export interface ActivityConfig {
  chooseOnStartup?: boolean;
  editorBody?: string;
  editorTitle?: string;
  enabled?: boolean;
  managerTitle?: string;
}

export interface ActivityDetails {
  properties?: Prop[];
}

export interface ActivityValidationResult {
  messages?: string;
  valid?: boolean;
}

export type AddPermissionEvent = PermissionChangeEvent & {
  documentUuid?: string;
  permission?: string;
  userUuid?: string;
};

export interface Annotation {
  assignedTo?: string;
  comment?: string;

  /** @format int64 */
  createTime?: number;
  createUser?: string;
  history?: string;

  /** @format int64 */
  id?: number;
  status?: string;
  subject?: string;
  title?: string;

  /** @format int64 */
  updateTime?: number;
  updateUser?: string;

  /** @format int32 */
  version?: number;
}

export interface AnnotationDetail {
  annotation?: Annotation;
  entries?: AnnotationEntry[];
}

export interface AnnotationEntry {
  /** @format int64 */
  createTime?: number;
  createUser?: string;
  data?: string;
  entryType?: string;

  /** @format int64 */
  id?: number;

  /** @format int64 */
  updateTime?: number;
  updateUser?: string;

  /** @format int32 */
  version?: number;
}

export interface Arg {
  allowedValues?: string[];
  argType?: "UNKNOWN" | "BOOLEAN" | "DOUBLE" | "ERROR" | "INTEGER" | "LONG" | "NULL" | "NUMBER" | "STRING";
  defaultValue?: string;
  description?: string;

  /** @format int32 */
  minVarargsCount?: number;
  name?: string;
  optional?: boolean;
  varargs?: boolean;
}

export interface AssignTasksRequest {
  /** @format int32 */
  count?: number;
  nodeName?: string;
}

export interface AuthenticationState {
  allowPasswordResets?: boolean;
  userId?: string;
}

export interface Automate {
  open?: boolean;
  refresh?: boolean;
  refreshInterval?: string;
}

export interface Base64EncodedDocumentData {
  dataMap?: Record<string, string>;
  docRef?: DocRef;
}

export type BooleanField = AbstractField;

export interface BuildInfo {
  buildDate?: string;
  buildVersion?: string;
  upDate?: string;
}

export interface BulkActionResult {
  docRefs?: DocRef[];
  message?: string;
}

export interface CacheInfo {
  map?: Record<string, string>;
  name?: string;
  nodeName?: string;
}

export interface CacheInfoResponse {
  pageResponse?: PageResponse;
  values?: CacheInfo[];
}

export interface ChangeDocumentPermissionsRequest {
  cascade?: "NO" | "CHANGES_ONLY" | "ALL";
  changes?: Changes;
  docRef?: DocRef;
}

export interface ChangePasswordRequest {
  confirmNewPassword?: string;
  currentPassword?: string;
  newPassword?: string;
  userId?: string;
}

export interface ChangePasswordResponse {
  changeSucceeded?: boolean;
  forceSignIn?: boolean;
  message?: string;
}

export interface ChangeSetString {
  addSet?: string[];
  removeSet?: string[];
}

export interface ChangeSetUser {
  addSet?: User[];
  removeSet?: User[];
}

export interface ChangeUserRequest {
  changedAppPermissions?: ChangeSetString;
  changedLinkedUsers?: ChangeSetUser;
  user?: User;
}

export interface Changes {
  add?: Record<string, string[]>;
  remove?: Record<string, string[]>;
}

export interface CheckDocumentPermissionRequest {
  documentUuid?: string;
  permission?: string;
}

export type ClearDocumentPermissionsEvent = PermissionChangeEvent & { documentUuid?: string };

export interface ClusterLockKey {
  /** @format int64 */
  creationTime?: number;
  name?: string;
  nodeName?: string;
}

export interface ClusterNodeInfo {
  buildInfo?: BuildInfo;
  discoverTime?: string;
  endpointUrl?: string;
  error?: string;
  itemList?: ClusterNodeInfoItem[];
  nodeName?: string;

  /** @format int64 */
  ping?: number;
}

export interface ClusterNodeInfoItem {
  active?: boolean;
  master?: boolean;
  nodeName?: string;
}

export interface ClusterSearchTask {
  dateTimeLocale?: string;
  key?: QueryKey;

  /** @format int64 */
  now?: number;
  query?: Query;
  settings?: CoprocessorSettings[];
  shards?: number[];
  sourceTaskId?: TaskId;
  storedFields?: string[];
  taskName?: string;
}

export interface ComponentConfig {
  id?: string;
  name?: string;
  settings?: ComponentSettings;
  type?: string;
}

export interface ComponentResultRequest {
  componentId?: string;
  fetch?: "NONE" | "CHANGES" | "ALL";
  type: string;
}

export interface ComponentSettings {
  type: string;
}

export interface ConditionalFormattingRule {
  backgroundColor?: string;
  enabled?: boolean;
  expression?: ExpressionOperator;
  hide?: boolean;
  id?: string;
  textColor?: string;
}

export interface ConfigProperty {
  /** @format int64 */
  createTimeMs?: number;
  createUser?: string;
  dataTypeName?: string;
  databaseOverrideValue?: OverrideValueString;
  defaultValue?: string;
  description?: string;
  editable?: boolean;

  /** @format int32 */
  id?: number;
  name?: PropertyPath;
  password?: boolean;
  requireRestart?: boolean;
  requireUiRestart?: boolean;

  /** @format int64 */
  updateTimeMs?: number;
  updateUser?: string;

  /** @format int32 */
  version?: number;
  yamlOverrideValue?: OverrideValueString;
}

export interface ConfirmPasswordRequest {
  password?: string;
}

export interface ConfirmPasswordResponse {
  message?: string;
  valid?: boolean;
}

export interface CoprocessorSettings {
  /** @format int32 */
  coprocessorId?: number;
  type: string;
}

export interface CopyOp {
  destinationFolderRef?: DocRef;
  docRefs?: DocRef[];
  permissionInheritance?: "NONE" | "SOURCE" | "DESTINATION" | "COMBINED";
}

export interface CopyPermissionsFromParentRequest {
  docRef?: DocRef;
}

export interface CountLong {
  /** @format int64 */
  count?: number;
  exact?: boolean;
}

export interface CreateAccountRequest {
  comments?: string;
  confirmPassword?: string;
  email?: string;
  firstName?: string;
  forcePasswordChange?: boolean;
  lastName?: string;
  neverExpires?: boolean;
  password?: string;
  userId?: string;
}

export interface CreateEntryRequest {
  annotation?: Annotation;
  data?: string;
  linkedEvents?: EventId[];
  type?: string;
}

export interface CreateOp {
  destinationFolderRef?: DocRef;
  docRefName?: string;
  docRefType?: string;
  permissionInheritance?: "NONE" | "SOURCE" | "DESTINATION" | "COMBINED";
}

export interface CreateProcessFilterRequest {
  autoPriority?: boolean;
  enabled?: boolean;
  pipeline?: DocRef;

  /** @format int32 */
  priority?: number;
  queryData?: QueryData;
}

export interface CreateReprocessFilterRequest {
  autoPriority?: boolean;
  enabled?: boolean;

  /** @format int32 */
  priority?: number;
  queryData?: QueryData;
}

export interface CreateTokenRequest {
  comments?: string;
  enabled?: boolean;

  /** @format int64 */
  expiresOnMs?: number;

  /** @pattern ^user$|^api$|^email_reset$ */
  tokenType: string;
  userId: string;
}

export interface CriteriaFieldSort {
  desc?: boolean;
  id?: string;
  ignoreCase?: boolean;
}

export interface CustomRollUpMask {
  rolledUpTagPosition?: number[];
}

export interface CustomRollUpMaskFields {
  /** @format int32 */
  id?: number;

  /** @format int32 */
  maskValue?: number;
  rolledUpFieldPositions?: number[];
}

export interface DBTableStatus {
  /** @format int64 */
  count?: number;

  /** @format int64 */
  dataSize?: number;
  db?: string;

  /** @format int64 */
  indexSize?: number;
  table?: string;
}

export interface DashboardConfig {
  components?: ComponentConfig[];
  layout?: LayoutConfig;
  parameters?: string;
  tabVisibility?: "SHOW_ALL" | "HIDE_SINGLE" | "HIDE_ALL";
}

export interface DashboardDoc {
  /** @format int64 */
  createTime?: number;

  /** @format int64 */
  createTimeMs?: number;
  createUser?: string;
  dashboardConfig?: DashboardConfig;
  name?: string;
  type?: string;

  /** @format int64 */
  updateTime?: number;

  /** @format int64 */
  updateTimeMs?: number;
  updateUser?: string;
  uuid?: string;
  version?: string;
}

export interface DashboardQueryKey {
  componentId?: string;
  dashboardUuid?: string;
  uuid?: string;
}

export interface DataInfoSection {
  entries?: Entry[];
  title?: string;
}

export interface DataRange {
  /** @format int64 */
  byteOffsetFrom?: number;

  /** @format int64 */
  byteOffsetTo?: number;

  /** @format int64 */
  charOffsetFrom?: number;

  /** @format int64 */
  charOffsetTo?: number;

  /** @format int64 */
  length?: number;
  locationFrom?: Location;
  locationTo?: Location;
}

export interface DataRetentionDeleteSummary {
  /** @format int32 */
  count?: number;
  feedName?: string;
  metaType?: string;
  ruleName?: string;

  /** @format int32 */
  ruleNumber?: number;
}

export interface DataRetentionDeleteSummaryRequest {
  criteria?: FindDataRetentionImpactCriteria;
  dataRetentionRules?: DataRetentionRules;
  queryId?: string;
}

export interface DataRetentionDeleteSummaryResponse {
  pageResponse?: PageResponse;
  queryId?: string;
  values?: DataRetentionDeleteSummary[];
}

export interface DataRetentionRule {
  /** @format int32 */
  age?: number;

  /** @format int64 */
  creationTime?: number;
  enabled?: boolean;
  expression?: ExpressionOperator;
  forever?: boolean;
  name?: string;

  /** @format int32 */
  ruleNumber?: number;
  timeUnit?: "MINUTES" | "HOURS" | "DAYS" | "WEEKS" | "MONTHS" | "YEARS";
}

export interface DataRetentionRules {
  /** @format int64 */
  createTime?: number;

  /** @format int64 */
  createTimeMs?: number;
  createUser?: string;
  name?: string;
  rules?: DataRetentionRule[];
  type?: string;

  /** @format int64 */
  updateTime?: number;

  /** @format int64 */
  updateTimeMs?: number;
  updateUser?: string;
  uuid?: string;
  version?: string;
}

export interface DataSource {
  fields?: AbstractField[];
}

export type DateField = AbstractField;

export type DateTimeFormatSettings = FormatSettings & { pattern?: string; timeZone?: TimeZone };

export type DefaultLocation = Location;

export interface Dependency {
  from?: DocRef;
  ok?: boolean;
  to?: DocRef;
}

export interface DependencyCriteria {
  pageRequest?: PageRequest;
  partialName?: string;
  sort?: string;
  sortList?: CriteriaFieldSort[];
}

export interface DictionaryDTO {
  data?: string;
  description?: string;
  imports?: DocRef[];
  name?: string;
  type?: string;
  uuid?: string;
}

export interface DictionaryDoc {
  /** @format int64 */
  createTime?: number;

  /** @format int64 */
  createTimeMs?: number;
  createUser?: string;
  data?: string;
  description?: string;
  imports?: DocRef[];
  name?: string;
  type?: string;

  /** @format int64 */
  updateTime?: number;

  /** @format int64 */
  updateTimeMs?: number;
  updateUser?: string;
  uuid?: string;
  version?: string;
}

export interface DocRef {
  name?: string;
  type?: string;
  uuid?: string;
}

export type DocRefField = AbstractField & { docRefType?: string };

export interface DocRefInfo {
  /** @format int64 */
  createTime?: number;
  createUser?: string;
  docRef?: DocRef;
  otherInfo?: string;

  /** @format int64 */
  updateTime?: number;
  updateUser?: string;
}

export interface DocRefs {
  docRefs?: DocRef[];
}

export interface DocumentPermissions {
  docUuid?: string;
  groups?: User[];
  permissions?: Record<string, string[]>;
  users?: User[];
}

export interface DocumentType {
  displayType?: string;
  iconUrl?: string;

  /** @format int32 */
  priority?: number;
  type?: string;
}

export interface DocumentTypes {
  nonSystemTypes?: DocumentType[];
  visibleTypes?: DocumentType[];
}

export type DoubleField = AbstractField;

export interface DownloadQueryRequest {
  dashboardQueryKey?: DashboardQueryKey;
  searchRequest?: SearchRequest;
}

export interface DownloadSearchResultsRequest {
  applicationInstanceId?: string;
  componentId?: string;
  dateTimeLocale?: string;
  fileType?: "EXCEL" | "CSV" | "TSV";

  /** @format int32 */
  percent?: number;
  sample?: boolean;
  searchRequest?: SearchRequest;
}

export interface EntityEvent {
  action?: "CREATE" | "UPDATE" | "DELETE" | "CLEAR_CACHE";
  docRef?: DocRef;
}

export interface Entry {
  key?: string;
  value?: string;
}

export type EventCoprocessorSettings = CoprocessorSettings & {
  maxEvent?: EventRef;
  maxEvents?: number;
  maxEventsPerStream?: number;
  maxStreams?: number;
  minEvent?: EventRef;
};

export interface EventId {
  /** @format int64 */
  eventId?: number;

  /** @format int64 */
  streamId?: number;
}

export interface EventLink {
  /** @format int64 */
  annotationId?: number;
  eventId?: EventId;
}

export interface EventRef {
  /** @format int64 */
  eventId?: number;

  /** @format int64 */
  streamId?: number;
}

export interface Expander {
  /** @format int32 */
  depth?: number;
  expanded?: boolean;
  leaf?: boolean;
}

export interface ExplorerNode {
  children?: ExplorerNode[];

  /** @format int32 */
  depth?: number;
  iconUrl?: string;
  name?: string;
  nodeState?: "OPEN" | "CLOSED" | "LEAF";
  tags?: string;
  type?: string;
  uuid?: string;
}

export interface ExplorerNodePermissions {
  admin?: boolean;
  createPermissions?: string[];
  documentPermissions?: string[];
  explorerNode?: ExplorerNode;
}

export interface ExplorerServiceCopyRequest {
  destinationFolderRef?: DocRef;
  docRefs?: DocRef[];
  permissionInheritance?: "NONE" | "SOURCE" | "DESTINATION" | "COMBINED";
}

export interface ExplorerServiceCreateRequest {
  destinationFolderRef?: DocRef;
  docName?: string;
  docType?: string;
  permissionInheritance?: "NONE" | "SOURCE" | "DESTINATION" | "COMBINED";
}

export interface ExplorerServiceDeleteRequest {
  docRefs?: DocRef[];
}

export interface ExplorerServiceMoveRequest {
  destinationFolderRef?: DocRef;
  docRefs?: DocRef[];
  permissionInheritance?: "NONE" | "SOURCE" | "DESTINATION" | "COMBINED";
}

export interface ExplorerServiceRenameRequest {
  docName?: string;
  docRef?: DocRef;
}

export interface ExplorerTreeFilter {
  includedTypes?: string[];
  nameFilter?: string;
  nameFilterChange?: boolean;
  requiredPermissions?: string[];
  tags?: string[];
}

export interface ExpressionCriteria {
  expression?: ExpressionOperator;
  pageRequest?: PageRequest;
  sort?: string;
  sortList?: CriteriaFieldSort[];
}

export interface ExpressionItem {
  enabled?: boolean;
  type: string;
}

export interface ExpressionOperator {
  children?: ExpressionItem[];
  enabled?: boolean;
  op?: "AND" | "OR" | "NOT";
}

export type ExpressionTerm = ExpressionItem & {
  condition?:
    | "CONTAINS"
    | "EQUALS"
    | "GREATER_THAN"
    | "GREATER_THAN_OR_EQUAL_TO"
    | "LESS_THAN"
    | "LESS_THAN_OR_EQUAL_TO"
    | "BETWEEN"
    | "IN"
    | "IN_DICTIONARY"
    | "IN_FOLDER"
    | "IS_DOC_REF"
    | "IS_NULL"
    | "IS_NOT_NULL";
  docRef?: DocRef;
  field?: string;
  value?: string;
};

export interface FeedDoc {
  classification?: string;
  contextEncoding?: string;

  /** @format int64 */
  createTime?: number;

  /** @format int64 */
  createTimeMs?: number;
  createUser?: string;
  description?: string;
  encoding?: string;
  name?: string;
  reference?: boolean;

  /** @format int32 */
  retentionDayAge?: number;
  status?: "RECEIVE" | "REJECT" | "DROP";
  streamType?: string;
  type?: string;

  /** @format int64 */
  updateTime?: number;

  /** @format int64 */
  updateTimeMs?: number;
  updateUser?: string;
  uuid?: string;
  version?: string;
}

export interface FetchAllDocumentPermissionsRequest {
  docRef?: DocRef;
}

export interface FetchDataRequest {
  expandedSeverities?: ("INFO" | "WARN" | "ERROR" | "FATAL")[];
  markerMode?: boolean;
  pipeline?: DocRef;

  /** @format int64 */
  segmentCount?: number;
  showAsHtml?: boolean;
  sourceLocation?: SourceLocation;
}

export type FetchDataResult = AbstractFetchDataResult & {
  data?: string;
  dataType?: "SEGMENTED" | "NON_SEGMENTED" | "MARKER";
  html?: boolean;
  totalBytes?: number;
};

export interface FetchExplorerNodeResult {
  openedItems?: string[];
  rootNodes?: ExplorerNode[];
  temporaryOpenedItems?: string[];
}

export interface FetchLinkedScriptRequest {
  loadedScripts?: DocRef[];
  script?: DocRef;
}

export type FetchMarkerResult = AbstractFetchDataResult & { markers?: Marker[] };

export interface FetchNodeStatusResponse {
  pageResponse?: PageResponse;
  values?: NodeStatusResult[];
}

export interface FetchPipelineXmlResponse {
  pipeline?: DocRef;
  xml?: string;
}

export interface FetchProcessorRequest {
  expandedRows?: ProcessorListRow[];
  expression?: ExpressionOperator;
}

export interface FetchPropertyTypesResult {
  pipelineElementType?: PipelineElementType;
  propertyTypes?: Record<string, PipelinePropertyType>;
}

export interface FetchSuggestionsRequest {
  dataSource: DocRef;
  field: AbstractField;
  text?: string;
}

export interface Field {
  expression?: string;
  filter?: Filter;
  format?: Format;

  /** @format int32 */
  group?: number;
  id?: string;
  name?: string;
  sort?: Sort;
  special?: boolean;
  visible?: boolean;

  /** @format int32 */
  width?: number;
}

export interface Filter {
  excludes?: string;
  includes?: string;
}

export interface FilterFieldDefinition {
  defaultField?: boolean;
  displayName?: string;
  filterQualifier?: string;
}

export interface FilterUsersRequest {
  quickFilterInput?: string;
  users?: User[];
}

export interface FindDBTableCriteria {
  pageRequest?: PageRequest;
  sort?: string;
  sortList?: CriteriaFieldSort[];
}

export interface FindDataRetentionImpactCriteria {
  expression?: ExpressionOperator;
  pageRequest?: PageRequest;
  sort?: string;
  sortList?: CriteriaFieldSort[];
}

export interface FindElementDocRequest {
  feedName?: string;
  pipelineElement?: PipelineElement;
  pipelineName?: string;
  properties?: PipelineProperty[];
}

export interface FindExplorerNodeCriteria {
  ensureVisible?: string[];
  filter?: ExplorerTreeFilter;

  /** @format int32 */
  minDepth?: number;
  openItems?: string[];
  temporaryOpenedItems?: string[];
}

export interface FindFsVolumeCriteria {
  pageRequest?: PageRequest;
  selection?: SelectionVolumeUseStatus;
  sort?: string;
  sortList?: CriteriaFieldSort[];
}

export interface FindIndexShardCriteria {
  documentCountRange?: RangeInteger;
  indexShardIdSet?: SelectionLong;
  indexShardStatusSet?: SelectionIndexShardStatus;
  indexUuidSet?: SelectionString;
  nodeNameSet?: SelectionString;
  pageRequest?: PageRequest;
  partition?: StringCriteria;
  sort?: string;
  sortList?: CriteriaFieldSort[];
  volumeIdSet?: SelectionInteger;
}

export interface FindMetaCriteria {
  expression?: ExpressionOperator;
  fetchRelationships?: boolean;
  pageRequest?: PageRequest;
  sort?: string;
  sortList?: CriteriaFieldSort[];
}

export interface FindStoredQueryCriteria {
  componentId?: string;
  dashboardUuid?: string;
  favourite?: boolean;
  name?: StringCriteria;
  pageRequest?: PageRequest;
  requiredPermission?: string;
  sort?: string;
  sortList?: CriteriaFieldSort[];
  userId?: string;
}

export interface FindTaskCriteria {
  ancestorIdSet?: TaskId[];
  idSet?: TaskId[];
  sessionId?: string;
}

export interface FindTaskProgressCriteria {
  expandedTasks?: TaskProgress[];
  nameFilter?: string;
  pageRequest?: PageRequest;
  sessionId?: string;
  sort?: string;
  sortList?: CriteriaFieldSort[];
}

export interface FindTaskProgressRequest {
  criteria?: FindTaskProgressCriteria;
}

export interface FindUserCriteria {
  group?: boolean;
  pageRequest?: PageRequest;
  quickFilterInput?: string;
  relatedUser?: User;
  sort?: string;
  sortList?: CriteriaFieldSort[];
}

export type FlatResult = Result & { size?: number; structure?: Field[]; values?: object[][] };

export type FloatField = AbstractField;

export interface Format {
  settings?: FormatSettings;
  type?: "GENERAL" | "NUMBER" | "DATE_TIME" | "TEXT";
  wrap?: boolean;
}

export interface FormatSettings {
  default?: boolean;
  type: string;
}

export interface FsVolume {
  /** @format int64 */
  byteLimit?: number;

  /** @format int64 */
  createTimeMs?: number;
  createUser?: string;

  /** @format int32 */
  id?: number;
  path?: string;
  status?: "ACTIVE" | "INACTIVE" | "CLOSED";

  /** @format int64 */
  updateTimeMs?: number;
  updateUser?: string;

  /** @format int32 */
  version?: number;
  volumeState?: FsVolumeState;
}

export interface FsVolumeState {
  /** @format int64 */
  bytesFree?: number;

  /** @format int64 */
  bytesTotal?: number;

  /** @format int64 */
  bytesUsed?: number;

  /** @format int32 */
  id?: number;

  /** @format int64 */
  updateTimeMs?: number;

  /** @format int32 */
  version?: number;
}

export interface FunctionSignature {
  aliases?: string[];
  args?: Arg[];
  categoryPath?: string[];
  description?: string;
  name?: string;
  returnDescription?: string;
  returnType?: "UNKNOWN" | "BOOLEAN" | "DOUBLE" | "ERROR" | "INTEGER" | "LONG" | "NULL" | "NUMBER" | "STRING";
}

export interface GetFeedStatusRequest {
  feedName?: string;
  senderDn?: string;
}

export interface GetFeedStatusResponse {
  message?: string;
  status?: "Receive" | "Reject" | "Drop";
  stroomStatusCode?:
    | "200 - 0 - OK"
    | "406 - 100 - Feed must be specified"
    | "406 - 101 - Feed is not defined"
    | "406 - 110 - Feed is not set to receive data"
    | "406 - 120 - Unexpected data type"
    | "406 - 200 - Unknown compression"
    | "401 - 300 - Client Certificate Required"
    | "403 - 310 - Client Certificate not authorised"
    | "500 - 400 - Compressed stream invalid"
    | "500 - 999 - Unknown error";
}

export interface GetPipelineForMetaRequest {
  /** @format int64 */
  childMetaId?: number;

  /** @format int64 */
  metaId?: number;
}

export interface GetScheduledTimesRequest {
  jobType?: "UNKNOWN" | "CRON" | "FREQUENCY" | "DISTRIBUTED";

  /** @format int64 */
  lastExecutedTime?: number;
  schedule?: string;

  /** @format int64 */
  scheduleReferenceTime?: number;
}

export interface GlobalConfigCriteria {
  pageRequest?: PageRequest;
  quickFilterInput?: string;
  sort?: string;
  sortList?: CriteriaFieldSort[];
}

export type IdField = AbstractField;

export interface ImportConfigRequest {
  confirmList?: ImportState[];
  resourceKey?: ResourceKey;
}

export interface ImportState {
  action?: boolean;
  destPath?: string;
  docRef?: DocRef;
  enable?: boolean;

  /** @format int64 */
  enableTime?: number;
  messageList?: Message[];
  sourcePath?: string;
  state?: "NEW" | "UPDATE" | "EQUAL";
  updatedFieldList?: string[];
}

export interface IndexDoc {
  /** @format int64 */
  createTime?: number;

  /** @format int64 */
  createTimeMs?: number;
  createUser?: string;
  description?: string;
  fields?: IndexField[];

  /** @format int32 */
  maxDocsPerShard?: number;
  name?: string;
  partitionBy?: "DAY" | "WEEK" | "MONTH" | "YEAR";

  /** @format int32 */
  partitionSize?: number;

  /** @format int32 */
  retentionDayAge?: number;

  /** @format int32 */
  shardsPerPartition?: number;
  type?: string;

  /** @format int64 */
  updateTime?: number;

  /** @format int64 */
  updateTimeMs?: number;
  updateUser?: string;
  uuid?: string;
  version?: string;
  volumeGroupName?: string;
}

export interface IndexField {
  analyzerType?: "KEYWORD" | "ALPHA" | "NUMERIC" | "ALPHA_NUMERIC" | "WHITESPACE" | "STOP" | "STANDARD";
  caseSensitive?: boolean;
  fieldName?: string;
  fieldType?:
    | "ID"
    | "BOOLEAN_FIELD"
    | "INTEGER_FIELD"
    | "LONG_FIELD"
    | "FLOAT_FIELD"
    | "DOUBLE_FIELD"
    | "DATE_FIELD"
    | "FIELD"
    | "NUMERIC_FIELD";
  indexed?: boolean;
  stored?: boolean;
  termPositions?: boolean;
}

export interface IndexShard {
  /** @format int32 */
  commitDocumentCount?: number;

  /** @format int64 */
  commitDurationMs?: number;

  /** @format int64 */
  commitMs?: number;

  /** @format int32 */
  documentCount?: number;

  /** @format int64 */
  fileSize?: number;

  /** @format int64 */
  id?: number;
  indexUuid?: string;
  indexVersion?: string;
  nodeName?: string;
  partition?: string;

  /** @format int64 */
  partitionFromTime?: number;

  /** @format int64 */
  partitionToTime?: number;
  status?: "CLOSED" | "OPEN" | "CLOSING" | "OPENING" | "NEW" | "DELETED" | "CORRUPT";
  volume?: IndexVolume;
}

export interface IndexVolume {
  /** @format int64 */
  bytesFree?: number;

  /** @format int64 */
  bytesLimit?: number;

  /** @format int64 */
  bytesTotal?: number;

  /** @format int64 */
  bytesUsed?: number;

  /** @format int64 */
  createTimeMs?: number;
  createUser?: string;

  /** @format int32 */
  id?: number;

  /** @format int32 */
  indexVolumeGroupId?: number;
  nodeName?: string;
  path?: string;
  state?: "ACTIVE" | "INACTIVE" | "CLOSED";

  /** @format int64 */
  statusMs?: number;

  /** @format int64 */
  updateTimeMs?: number;
  updateUser?: string;

  /** @format int32 */
  version?: number;
}

export interface IndexVolumeGroup {
  /** @format int64 */
  createTimeMs?: number;
  createUser?: string;

  /** @format int32 */
  id?: number;
  name?: string;

  /** @format int64 */
  updateTimeMs?: number;
  updateUser?: string;

  /** @format int32 */
  version?: number;
}

export interface Indicators {
  errorCount?: Record<string, number>;
  errorList?: StoredError[];
  uniqueErrorSet?: StoredError[];
}

export interface InfoPopupConfig {
  enabled?: boolean;
  title?: string;
  validationRegex?: string;
}

export type IntegerField = AbstractField;

export interface Job {
  advanced?: boolean;

  /** @format int64 */
  createTimeMs?: number;
  createUser?: string;
  description?: string;
  enabled?: boolean;

  /** @format int32 */
  id?: number;
  name?: string;

  /** @format int64 */
  updateTimeMs?: number;
  updateUser?: string;

  /** @format int32 */
  version?: number;
}

export interface JobNode {
  /** @format int64 */
  createTimeMs?: number;
  createUser?: string;
  enabled?: boolean;

  /** @format int32 */
  id?: number;
  job?: Job;
  jobType?: "UNKNOWN" | "CRON" | "FREQUENCY" | "DISTRIBUTED";
  nodeName?: string;
  schedule?: string;

  /** @format int32 */
  taskLimit?: number;

  /** @format int64 */
  updateTimeMs?: number;
  updateUser?: string;

  /** @format int32 */
  version?: number;
}

export interface JobNodeInfo {
  /** @format int32 */
  currentTaskCount?: number;

  /** @format int64 */
  lastExecutedTime?: number;

  /** @format int64 */
  scheduleReferenceTime?: number;
}

export interface KafkaConfigDoc {
  /** @format int64 */
  createTime?: number;

  /** @format int64 */
  createTimeMs?: number;
  createUser?: string;
  data?: string;
  description?: string;
  name?: string;
  type?: string;

  /** @format int64 */
  updateTime?: number;

  /** @format int64 */
  updateTimeMs?: number;
  updateUser?: string;
  uuid?: string;
  version?: string;
}

export interface LayoutConfig {
  preferredSize?: Size;
  type: string;
}

export interface Limits {
  /** @format int64 */
  durationMs?: number;

  /** @format int64 */
  eventCount?: number;

  /** @format int64 */
  streamCount?: number;
}

export interface ListConfigResponse {
  pageResponse?: PageResponse;
  values?: ConfigProperty[];
}

export interface Location {
  /** @format int32 */
  colNo?: number;

  /** @format int32 */
  lineNo?: number;
  type: string;
}

export interface LoginRequest {
  password?: string;
  userId?: string;
}

export interface LoginResponse {
  authenticated?: boolean;
  redirectUri?: string;
}

export type LongField = AbstractField;

export interface MapDefinition {
  mapName?: string;
  refStreamDefinition?: RefStreamDefinition;
}

export interface Marker {
  severity?: "INFO" | "WARN" | "ERROR" | "FATAL";
  type: string;
}

export interface Message {
  message?: string;
  severity?: "INFO" | "WARN" | "ERROR" | "FATAL";
}

export interface Meta {
  /** @format int64 */
  createMs?: number;

  /** @format int64 */
  effectiveMs?: number;
  feedName?: string;

  /** @format int64 */
  id?: number;

  /** @format int64 */
  parentMetaId?: number;
  pipelineUuid?: string;
  processorUuid?: string;
  status?: "UNLOCKED" | "LOCKED" | "DELETED";

  /** @format int64 */
  statusMs?: number;
  typeName?: string;
}

export interface MetaRow {
  attributes?: Record<string, string>;
  meta?: Meta;
  pipelineName?: string;
}

export interface MoveOp {
  destinationFolderRef?: DocRef;
  docRefs?: DocRef[];
  permissionInheritance?: "NONE" | "SOURCE" | "DESTINATION" | "COMBINED";
}

export interface Node {
  /** @format int64 */
  createTimeMs?: number;
  createUser?: string;
  enabled?: boolean;

  /** @format int32 */
  id?: number;
  name?: string;

  /** @format int32 */
  priority?: number;

  /** @format int64 */
  updateTimeMs?: number;
  updateUser?: string;
  url?: string;

  /** @format int32 */
  version?: number;
}

export interface NodeStatusResult {
  master?: boolean;
  node?: Node;
}

export type NumberFormatSettings = FormatSettings & { decimalPlaces?: number; useSeparator?: boolean };

export interface OffsetRange {
  /** @format int64 */
  length?: number;

  /** @format int64 */
  offset?: number;
}

export interface OverrideValueString {
  hasOverride?: boolean;
  value?: string;
}

export interface PageRequest {
  /** @format int32 */
  length?: number;

  /** @format int64 */
  offset?: number;
}

export interface PageResponse {
  exact?: boolean;

  /** @format int32 */
  length?: number;

  /** @format int64 */
  offset?: number;

  /** @format int64 */
  total?: number;
}

export interface Param {
  key?: string;
  value?: string;
}

export interface PasswordPolicyConfig {
  allowPasswordResets?: boolean;
  forcePasswordChangeOnFirstLogin?: boolean;
  mandatoryPasswordChangeDuration: string;

  /**
   * @format int32
   * @min 0
   */
  minimumPasswordLength: number;

  /**
   * @format int32
   * @min 0
   * @max 5
   */
  minimumPasswordStrength: number;
  neverUsedAccountDeactivationThreshold: string;
  passwordComplexityRegex?: string;
  passwordPolicyMessage?: string;
  unusedAccountDeactivationThreshold: string;
}

export interface PermissionChangeEvent {
  type: string;
}

export interface PermissionChangeRequest {
  event?: PermissionChangeEvent;
}

export interface PipelineDTO {
  configStack?: PipelineData[];
  description?: string;
  docRef?: DocRef;
  merged?: PipelineData;
  parentPipeline?: DocRef;
}

export interface PipelineData {
  elements?: PipelineElements;
  links?: PipelineLinks;
  pipelineReferences?: PipelineReferences;
  processors?: Processor[];
  properties?: PipelineProperties;
}

export interface PipelineDoc {
  /** @format int64 */
  createTime?: number;

  /** @format int64 */
  createTimeMs?: number;
  createUser?: string;
  description?: string;
  name?: string;
  parentPipeline?: DocRef;
  pipelineData?: PipelineData;
  type?: string;

  /** @format int64 */
  updateTime?: number;

  /** @format int64 */
  updateTimeMs?: number;
  updateUser?: string;
  uuid?: string;
  version?: string;
}

export interface PipelineElement {
  elementType?: PipelineElementType;
  id: string;
  type: string;
}

export interface PipelineElementType {
  category?: "INTERNAL" | "READER" | "PARSER" | "FILTER" | "WRITER" | "DESTINATION";
  icon?: string;
  roles?: string[];
  type?: string;
}

export interface PipelineElements {
  add?: PipelineElement[];
  remove?: PipelineElement[];
}

export interface PipelineLink {
  from: string;
  sourcePipeline?: DocRef;
  to: string;
}

export interface PipelineLinks {
  add?: PipelineLink[];
  remove?: PipelineLink[];
}

export interface PipelineProperties {
  add?: PipelineProperty[];
  remove?: PipelineProperty[];
}

export interface PipelineProperty {
  element: string;
  name: string;
  propertyType?: PipelinePropertyType;
  sourcePipeline?: DocRef;
  value?: PipelinePropertyValue;
}

export interface PipelinePropertyType {
  defaultValue?: string;
  description?: string;

  /** @format int32 */
  displayPriority?: number;
  docRefTypes?: string[];
  elementType?: PipelineElementType;
  name?: string;
  pipelineReference?: boolean;
  type?: string;
}

export interface PipelinePropertyValue {
  boolean?: boolean;
  entity?: DocRef;

  /** @format int32 */
  integer?: number;

  /** @format int64 */
  long?: number;
  string?: string;
}

export interface PipelineReference {
  element: string;
  feed: DocRef;
  name: string;
  pipeline: DocRef;
  sourcePipeline?: DocRef;
  streamType: string;
}

export interface PipelineReferences {
  add?: PipelineReference[];
  remove?: PipelineReference[];
}

export interface PipelineStepRequest {
  childStreamType?: string;
  code?: Record<string, string>;
  criteria?: FindMetaCriteria;
  pipeline?: DocRef;
  stepFilterMap?: Record<string, SteppingFilterSettings>;
  stepLocation?: StepLocation;

  /** @format int32 */
  stepSize?: number;
  stepType?: "FIRST" | "FORWARD" | "BACKWARD" | "LAST" | "REFRESH";
}

export interface ProcessConfig {
  /** @format int64 */
  defaultRecordLimit?: number;

  /** @format int64 */
  defaultTimeLimit?: number;
}

export interface Processor {
  /** @format int64 */
  createTimeMs?: number;
  createUser?: string;
  deleted?: boolean;
  enabled?: boolean;

  /** @format int32 */
  id?: number;
  pipelineName?: string;
  pipelineUuid?: string;
  taskType?: string;

  /** @format int64 */
  updateTimeMs?: number;
  updateUser?: string;
  uuid?: string;

  /** @format int32 */
  version?: number;
}

export interface ProcessorFilter {
  /** @format int64 */
  createTimeMs?: number;
  createUser?: string;
  data?: string;
  deleted?: boolean;
  enabled?: boolean;

  /** @format int32 */
  id?: number;
  pipelineName?: string;
  pipelineUuid?: string;

  /** @format int32 */
  priority?: number;
  processor?: Processor;
  processorFilterTracker?: ProcessorFilterTracker;
  processorUuid?: string;
  queryData?: QueryData;
  reprocess?: boolean;

  /** @format int64 */
  updateTimeMs?: number;
  updateUser?: string;
  uuid?: string;

  /** @format int32 */
  version?: number;
}

export type ProcessorFilterRow = ProcessorListRow & { processorFilter?: ProcessorFilter };

export interface ProcessorFilterTracker {
  /** @format int64 */
  eventCount?: number;

  /** @format int32 */
  id?: number;

  /** @format int64 */
  lastPollMs?: number;

  /** @format int32 */
  lastPollTaskCount?: number;

  /** @format int64 */
  maxMetaCreateMs?: number;

  /** @format int64 */
  metaCount?: number;

  /** @format int64 */
  metaCreateMs?: number;

  /** @format int64 */
  minEventId?: number;

  /** @format int64 */
  minMetaCreateMs?: number;

  /** @format int64 */
  minMetaId?: number;
  status?: string;

  /** @format int32 */
  version?: number;
}

export interface ProcessorListRow {
  expander?: Expander;
  type: string;
}

export interface ProcessorListRowResultPage {
  pageResponse?: PageResponse;
  values?: ProcessorListRow[];
}

export type ProcessorRow = ProcessorListRow & { processor?: Processor };

export interface ProcessorTask {
  /** @format int64 */
  createTimeMs?: number;
  data?: string;

  /** @format int64 */
  endTimeMs?: number;
  feedName?: string;

  /** @format int64 */
  id?: number;

  /** @format int64 */
  metaId?: number;
  nodeName?: string;
  processorFilter?: ProcessorFilter;

  /** @format int64 */
  startTimeMs?: number;
  status?: "UNPROCESSED" | "ASSIGNED" | "PROCESSING" | "COMPLETE" | "FAILED" | "DELETED";

  /** @format int64 */
  statusTimeMs?: number;

  /** @format int32 */
  version?: number;
}

export interface ProcessorTaskList {
  list?: ProcessorTask[];
  nodeName?: string;
}

export interface ProcessorTaskSummary {
  /** @format int64 */
  count?: number;
  feed?: string;
  pipeline?: DocRef;

  /** @format int32 */
  priority?: number;
  status?: "UNPROCESSED" | "ASSIGNED" | "PROCESSING" | "COMPLETE" | "FAILED" | "DELETED";
}

export interface Prop {
  id?: string;
  name?: string;
  showInList?: boolean;
  showInSelection?: boolean;
  validation?: string;
  validationMessage?: string;
  value?: string;
}

export interface PropertyPath {
  parts?: string[];
}

export interface Query {
  dataSource?: DocRef;
  expression?: ExpressionOperator;
  params?: Param[];
}

export type QueryComponentSettings = ComponentSettings & {
  automate?: Automate;
  dataSource?: DocRef;
  expression?: ExpressionOperator;
};

export interface QueryConfig {
  infoPopup?: InfoPopupConfig;
}

export interface QueryData {
  dataSource?: DocRef;
  expression?: ExpressionOperator;
  limits?: Limits;
}

export interface QueryKey {
  uuid?: string;
}

export interface RangeInteger {
  /** @format int32 */
  from?: number;
  matchNull?: boolean;

  /** @format int32 */
  to?: number;
}

export interface RangeLong {
  /** @format int64 */
  from?: number;
  matchNull?: boolean;

  /** @format int64 */
  to?: number;
}

export interface Rec {
  /** @format int64 */
  recordNo?: number;

  /** @format int64 */
  streamId?: number;
}

export interface ReceiveDataRule {
  action?: "RECEIVE" | "REJECT" | "DROP";

  /** @format int64 */
  creationTime?: number;
  enabled?: boolean;
  expression?: ExpressionOperator;
  name?: string;

  /** @format int32 */
  ruleNumber?: number;
}

export interface ReceiveDataRules {
  /** @format int64 */
  createTime?: number;

  /** @format int64 */
  createTimeMs?: number;
  createUser?: string;
  fields?: AbstractField[];
  name?: string;
  rules?: ReceiveDataRule[];
  type?: string;

  /** @format int64 */
  updateTime?: number;

  /** @format int64 */
  updateTimeMs?: number;
  updateUser?: string;
  uuid?: string;
  version?: string;
}

export interface RefDataLookupRequest {
  effectiveTime?: string;
  key: string;
  mapName: string;
  referenceLoaders: ReferenceLoader[];
}

export interface RefDataProcessingInfo {
  /** @format int64 */
  createTimeEpochMs?: number;

  /** @format int64 */
  effectiveTimeEpochMs?: number;

  /** @format int64 */
  lastAccessedTimeEpochMs?: number;
  processingState?: "LOAD_IN_PROGRESS" | "PURGE_IN_PROGRESS" | "COMPLETE";
}

export interface RefStoreEntry {
  key?: string;
  mapDefinition?: MapDefinition;
  refDataProcessingInfo?: RefDataProcessingInfo;
  value?: string;

  /** @format int32 */
  valueReferenceCount?: number;
}

export interface RefStreamDefinition {
  pipelineDocRef?: DocRef;
  pipelineVersion?: string;

  /** @format int64 */
  streamId?: number;

  /** @format int64 */
  streamNo?: number;
}

export interface ReferenceLoader {
  loaderPipeline: DocRef;
  referenceFeed: DocRef;
}

export type RemovePermissionEvent = PermissionChangeEvent & {
  documentUuid?: string;
  permission?: string;
  userUuid?: string;
};

export interface RenameOp {
  docRef?: DocRef;
  name?: string;
}

export interface ReprocessDataInfo {
  details?: string;
  message?: string;
  severity?: "INFO" | "WARN" | "ERROR" | "FATAL";
}

export interface ResetPasswordRequest {
  confirmNewPassword?: string;
  newPassword?: string;
}

export interface ResourceGeneration {
  messageList?: Message[];
  resourceKey?: ResourceKey;
}

export interface ResourceKey {
  key?: string;
  name?: string;
}

export interface Result {
  componentId?: string;
  error?: string;
  type: string;
}

export interface ResultPageActivity {
  pageResponse?: PageResponse;
  values?: Activity[];
}

export interface ResultPageCustomRollUpMask {
  pageResponse?: PageResponse;
  values?: CustomRollUpMask[];
}

export interface ResultPageCustomRollUpMaskFields {
  pageResponse?: PageResponse;
  values?: CustomRollUpMaskFields[];
}

export interface ResultPageDBTableStatus {
  pageResponse?: PageResponse;
  values?: DBTableStatus[];
}

export interface ResultPageDependency {
  pageResponse?: PageResponse;
  values?: Dependency[];
}

export interface ResultPageFsVolume {
  pageResponse?: PageResponse;
  values?: FsVolume[];
}

export interface ResultPageIndexShard {
  pageResponse?: PageResponse;
  values?: IndexShard[];
}

export interface ResultPageIndexVolume {
  pageResponse?: PageResponse;
  values?: IndexVolume[];
}

export interface ResultPageIndexVolumeGroup {
  pageResponse?: PageResponse;
  values?: IndexVolumeGroup[];
}

export interface ResultPageJob {
  pageResponse?: PageResponse;
  values?: Job[];
}

export interface ResultPageJobNode {
  pageResponse?: PageResponse;
  values?: JobNode[];
}

export interface ResultPageMetaRow {
  pageResponse?: PageResponse;
  values?: MetaRow[];
}

export interface ResultPageProcessorTask {
  pageResponse?: PageResponse;
  values?: ProcessorTask[];
}

export interface ResultPageProcessorTaskSummary {
  pageResponse?: PageResponse;
  values?: ProcessorTaskSummary[];
}

export interface ResultPageStoredQuery {
  pageResponse?: PageResponse;
  values?: StoredQuery[];
}

export interface ResultPageUser {
  pageResponse?: PageResponse;
  values?: User[];
}

export interface ResultRequest {
  componentId?: string;
  fetch?: "NONE" | "CHANGES" | "ALL";
  mappings?: TableSettings[];
  openGroups?: string[];
  requestedRange?: OffsetRange;
  resultStyle?: "FLAT" | "TABLE";
}

export interface Row {
  backgroundColor?: string;

  /** @format int32 */
  depth?: number;
  groupKey?: string;
  textColor?: string;
  values?: string[];
}

export interface SavePipelineXmlRequest {
  pipeline?: DocRef;
  xml?: string;
}

export interface ScheduledTimes {
  lastExecutedTime?: string;
  nextScheduledTime?: string;
}

export interface ScriptDoc {
  /** @format int64 */
  createTime?: number;

  /** @format int64 */
  createTimeMs?: number;
  createUser?: string;
  data?: string;
  dependencies?: DocRef[];
  description?: string;
  name?: string;
  type?: string;

  /** @format int64 */
  updateTime?: number;

  /** @format int64 */
  updateTimeMs?: number;
  updateUser?: string;
  uuid?: string;
  version?: string;
}

export interface Search {
  componentSettingsMap?: Record<string, ComponentSettings>;
  dataSourceRef?: DocRef;
  expression?: ExpressionOperator;
  incremental?: boolean;
  params?: Param[];
  queryInfo?: string;
  storeHistory?: boolean;
}

export interface SearchAccountRequest {
  pageRequest?: PageRequest;
  quickFilter?: string;
  sort?: string;
  sortList?: CriteriaFieldSort[];
}

export interface SearchBusPollRequest {
  applicationInstanceId?: string;
  searchRequests?: SearchRequest[];
}

export interface SearchRequest {
  dateTimeLocale?: string;
  incremental?: boolean;
  key?: QueryKey;
  query?: Query;
  resultRequests?: ResultRequest[];

  /** @format int64 */
  timeout?: number;
}

export interface SearchResponse {
  complete?: boolean;
  errors?: string[];
  highlights?: string[];
  results?: Result[];
}

export interface SearchTokenRequest {
  pageRequest?: PageRequest;
  quickFilter?: string;
  sort?: string;
  sortList?: CriteriaFieldSort[];
}

export interface SelectionIndexShardStatus {
  matchAll?: boolean;
  set?: ("CLOSED" | "OPEN" | "CLOSING" | "OPENING" | "NEW" | "DELETED" | "CORRUPT")[];
}

export interface SelectionInteger {
  matchAll?: boolean;
  set?: number[];
}

export interface SelectionLong {
  matchAll?: boolean;
  set?: number[];
}

export interface SelectionString {
  matchAll?: boolean;
  set?: string[];
}

export interface SelectionSummary {
  ageRange?: RangeLong;

  /** @format int64 */
  feedCount?: number;

  /** @format int64 */
  itemCount?: number;

  /** @format int64 */
  pipelineCount?: number;

  /** @format int64 */
  processorCount?: number;

  /** @format int64 */
  statusCount?: number;

  /** @format int64 */
  typeCount?: number;
}

export interface SelectionVolumeUseStatus {
  matchAll?: boolean;
  set?: ("ACTIVE" | "INACTIVE" | "CLOSED")[];
}

export interface SessionDetails {
  /** @format int64 */
  createMs?: number;
  lastAccessedAgent?: string;

  /** @format int64 */
  lastAccessedMs?: number;
  nodeName?: string;
  userName?: string;
}

export interface SessionInfo {
  buildInfo?: BuildInfo;
  nodeName?: string;
  userName?: string;
}

export interface SessionListResponse {
  pageResponse?: PageResponse;
  values?: SessionDetails[];
}

export interface SetAssignedToRequest {
  annotationIdList?: number[];
  assignedTo?: string;
}

export interface SetStatusRequest {
  annotationIdList?: number[];
  status?: string;
}

export interface SharedElementData {
  codeIndicators?: Indicators;
  formatInput?: boolean;
  formatOutput?: boolean;
  input?: string;
  output?: string;
  outputIndicators?: Indicators;
}

export interface SharedStepData {
  elementMap?: Record<string, SharedElementData>;
  sourceLocation?: SourceLocation;
}

export interface Size {
  /** @format int32 */
  height?: number;

  /** @format int32 */
  width?: number;
}

export interface SolrConnectionConfig {
  instanceType?: "SINGLE_NOOE" | "SOLR_CLOUD";
  solrUrls?: string[];
  useZk?: boolean;
  zkHosts?: string[];
  zkPath?: string;
}

export interface SolrConnectionTestResponse {
  message?: string;
  ok?: boolean;
}

export interface SolrIndexDoc {
  collection?: string;

  /** @format int64 */
  createTime?: number;

  /** @format int64 */
  createTimeMs?: number;
  createUser?: string;
  deletedFields?: SolrIndexField[];
  description?: string;
  fields?: SolrIndexField[];
  name?: string;
  retentionExpression?: ExpressionOperator;
  solrConnectionConfig?: SolrConnectionConfig;
  solrSynchState?: SolrSynchState;
  type?: string;

  /** @format int64 */
  updateTime?: number;

  /** @format int64 */
  updateTimeMs?: number;
  updateUser?: string;
  uuid?: string;
  version?: string;
}

export interface SolrIndexField {
  defaultValue?: string;
  docValues?: boolean;
  fieldName?: string;
  fieldType?: string;
  fieldUse?:
    | "ID"
    | "BOOLEAN_FIELD"
    | "INTEGER_FIELD"
    | "LONG_FIELD"
    | "FLOAT_FIELD"
    | "DOUBLE_FIELD"
    | "DATE_FIELD"
    | "FIELD"
    | "NUMERIC_FIELD";
  indexed?: boolean;
  multiValued?: boolean;
  omitNorms?: boolean;
  omitPositions?: boolean;
  omitTermFreqAndPositions?: boolean;
  required?: boolean;
  sortMissingFirst?: boolean;
  sortMissingLast?: boolean;
  stored?: boolean;
  supportedConditions?: (
    | "CONTAINS"
    | "EQUALS"
    | "GREATER_THAN"
    | "GREATER_THAN_OR_EQUAL_TO"
    | "LESS_THAN"
    | "LESS_THAN_OR_EQUAL_TO"
    | "BETWEEN"
    | "IN"
    | "IN_DICTIONARY"
    | "IN_FOLDER"
    | "IS_DOC_REF"
    | "IS_NULL"
    | "IS_NOT_NULL"
  )[];
  termOffsets?: boolean;
  termPayloads?: boolean;
  termPositions?: boolean;
  termVectors?: boolean;
  uninvertible?: boolean;
}

export interface SolrSynchState {
  /** @format int64 */
  lastSynchronized?: number;
  messages?: string[];
}

export interface Sort {
  direction?: "ASCENDING" | "DESCENDING";

  /** @format int32 */
  order?: number;
}

export interface SourceConfig {
  /**
   * @format int64
   * @min 1
   */
  maxCharactersInPreviewFetch?: number;

  /**
   * @format int64
   * @min 1
   */
  maxCharactersPerFetch?: number;

  /**
   * @format int64
   * @min 0
   */
  maxCharactersToCompleteLine?: number;
}

export interface SourceLocation {
  childType?: string;
  dataRange?: DataRange;
  highlight?: TextRange;

  /** @format int64 */
  id?: number;

  /** @format int64 */
  partNo?: number;

  /** @format int64 */
  segmentNo?: number;
  truncateToWholeLines?: boolean;
}

export interface SplashConfig {
  body?: string;
  enabled?: boolean;
  title?: string;
  version?: string;
}

export type SplitLayoutConfig = LayoutConfig & { children?: LayoutConfig[]; dimension?: number };

export interface StatisticField {
  fieldName?: string;
}

export interface StatisticStoreDoc {
  config?: StatisticsDataSourceData;

  /** @format int64 */
  createTime?: number;

  /** @format int64 */
  createTimeMs?: number;
  createUser?: string;
  description?: string;
  enabled?: boolean;
  name?: string;

  /** @format int64 */
  precision?: number;
  rollUpType?: "NONE" | "ALL" | "CUSTOM";
  statisticType?: "COUNT" | "VALUE";
  type?: string;

  /** @format int64 */
  updateTime?: number;

  /** @format int64 */
  updateTimeMs?: number;
  updateUser?: string;
  uuid?: string;
  version?: string;
}

export interface StatisticsDataSourceData {
  customRollUpMasks?: CustomRollUpMask[];
  fields?: StatisticField[];
}

export interface StatisticsDataSourceFieldChangeRequest {
  newStatisticsDataSourceData?: StatisticsDataSourceData;
  oldStatisticsDataSourceData?: StatisticsDataSourceData;
}

export interface StepLocation {
  /** @format int64 */
  id?: number;

  /** @format int64 */
  partNo?: number;

  /** @format int64 */
  recordNo?: number;
}

export interface SteppingFilterSettings {
  filters?: XPathFilter[];
  skipToOutput?: "NOT_EMPTY" | "EMPTY";
  skipToSeverity?: "INFO" | "WARN" | "ERROR" | "FATAL";
}

export interface SteppingResult {
  /** @format int32 */
  currentStreamOffset?: number;
  foundRecord?: boolean;
  generalErrors?: string[];
  stepData?: SharedStepData;
  stepFilterMap?: Record<string, SteppingFilterSettings>;
  stepLocation?: StepLocation;
}

export interface StoredError {
  elementId?: string;
  location?: Location;
  message?: string;
  severity?: "INFO" | "WARN" | "ERROR" | "FATAL";
}

export interface StoredQuery {
  componentId?: string;

  /** @format int64 */
  createTimeMs?: number;
  createUser?: string;
  dashboardUuid?: string;
  data?: string;
  favourite?: boolean;

  /** @format int32 */
  id?: number;
  name?: string;
  query?: Query;

  /** @format int64 */
  updateTimeMs?: number;
  updateUser?: string;

  /** @format int32 */
  version?: number;
}

export type StreamLocation = Location & { streamNo?: number };

export interface StreamTaskPatch {
  op?: string;
  path?: string;
  value?: string;
}

export interface StringCriteria {
  caseInsensitive?: boolean;
  matchNull?: boolean;
  matchStyle?: "Wild" | "WildStart" | "WildEnd" | "WildStartAndEnd";
  string?: string;
  stringUpper?: string;
}

export interface StroomStatsStoreDoc {
  config?: StroomStatsStoreEntityData;

  /** @format int64 */
  createTime?: number;

  /** @format int64 */
  createTimeMs?: number;
  createUser?: string;
  description?: string;
  enabled?: boolean;
  name?: string;
  precision?: "SECOND" | "MINUTE" | "HOUR" | "DAY" | "FOREVER";
  rollUpType?: "NONE" | "ALL" | "CUSTOM";
  statisticType?: "COUNT" | "VALUE";
  type?: string;

  /** @format int64 */
  updateTime?: number;

  /** @format int64 */
  updateTimeMs?: number;
  updateUser?: string;
  uuid?: string;
  version?: string;
}

export interface StroomStatsStoreEntityData {
  customRollUpMasks?: CustomRollUpMask[];
  fields?: StatisticField[];
}

export interface StroomStatsStoreFieldChangeRequest {
  newEntityData?: StroomStatsStoreEntityData;
  oldEntityData?: StroomStatsStoreEntityData;
}

export type Summary = Marker & { count?: number; expander?: Expander; total?: number };

export interface SystemInfoResult {
  description?: string;
  details?: Record<string, object>;
  name: string;
}

export interface SystemInfoResultList {
  results?: SystemInfoResult[];
}

export interface TabConfig {
  id?: string;
  visible?: boolean;
}

export type TabLayoutConfig = LayoutConfig & { selected?: number; tabs?: TabConfig[] };

export interface TableComponentSettings {
  conditionalFormattingRules?: ConditionalFormattingRule[];
  extractValues?: boolean;
  extractionPipeline?: DocRef;
  fields?: Field[];
  maxResults?: number[];
  modelVersion?: string;
  queryId?: string;
  showDetail?: boolean;
}

export type TableCoprocessorSettings = CoprocessorSettings & { componentIds?: string[]; tableSettings?: TableSettings };

export type TableResult = Result & { fields?: Field[]; resultRange?: OffsetRange; rows?: Row[]; totalResults?: number };

export type TableResultRequest = ComponentResultRequest & {
  openGroups?: string[];
  requestedRange?: OffsetRange;
  tableSettings?: TableSettings;
};

export interface TableSettings {
  conditionalFormattingRules?: ConditionalFormattingRule[];
  extractValues?: boolean;
  extractionPipeline?: DocRef;
  fields?: Field[];
  maxResults?: number[];
  modelVersion?: string;
  queryId?: string;
  showDetail?: boolean;
}

export interface TaskId {
  id?: string;
  parentId?: TaskId;
}

export interface TaskProgress {
  expander?: Expander;
  filterMatchState?: "MATCHED" | "NOT_MATCHED";
  id?: TaskId;
  nodeName?: string;

  /** @format int64 */
  submitTimeMs?: number;
  taskInfo?: string;
  taskName?: string;
  threadName?: string;

  /** @format int64 */
  timeNowMs?: number;
  userName?: string;
}

export interface TaskProgressResponse {
  pageResponse?: PageResponse;
  values?: TaskProgress[];
}

export interface TerminateTaskProgressRequest {
  criteria?: FindTaskCriteria;
  kill?: boolean;
}

export type TextComponentSettings = ComponentSettings & {
  colFromField?: Field;
  colToField?: Field;
  lineFromField?: Field;
  lineToField?: Field;
  modelVersion?: string;
  partNoField?: Field;
  pipeline?: DocRef;
  recordNoField?: Field;
  showAsHtml?: boolean;
  showStepping?: boolean;
  streamIdField?: Field;
  tableId?: string;
};

export interface TextConverterDoc {
  converterType?: "NONE" | "DATA_SPLITTER" | "XML_FRAGMENT";

  /** @format int64 */
  createTime?: number;

  /** @format int64 */
  createTimeMs?: number;
  createUser?: string;
  data?: string;
  description?: string;
  name?: string;
  type?: string;

  /** @format int64 */
  updateTime?: number;

  /** @format int64 */
  updateTimeMs?: number;
  updateUser?: string;
  uuid?: string;
  version?: string;
}

export type TextField = AbstractField;

export interface TextRange {
  from?: Location;
  to?: Location;
}

export interface ThemeConfig {
  backgroundAttachment?: string;
  backgroundColor?: string;
  backgroundImage?: string;
  backgroundOpacity?: string;
  backgroundPosition?: string;
  backgroundRepeat?: string;
  labelColours?: string;
  tubeOpacity?: string;
  tubeVisible?: string;
}

export interface TimeZone {
  id?: string;

  /** @format int32 */
  offsetHours?: number;

  /** @format int32 */
  offsetMinutes?: number;
  use?: "Local" | "UTC" | "Id" | "Offset";
}

export interface Token {
  comments?: string;

  /** @format int64 */
  createTimeMs?: number;
  createUser?: string;
  data?: string;
  enabled?: boolean;

  /** @format int64 */
  expiresOnMs?: number;

  /** @format int32 */
  id?: number;
  tokenType?: string;

  /** @format int64 */
  updateTimeMs?: number;
  updateUser?: string;
  userEmail?: string;
  userId?: string;

  /** @format int32 */
  version?: number;
}

export interface TokenConfig {
  algorithm: string;

  /** @format int64 */
  defaultApiKeyExpiryInMinutes?: number;
  jwsIssuer: string;
  timeUntilExpirationForEmailResetToken: string;
  timeUntilExpirationForUserToken: string;
}

export interface TokenRequest {
  client_id?: string;
  client_secret?: string;
  code?: string;
  grant_type?: string;
  redirect_uri?: string;
}

export interface TokenResponse {
  access_token?: string;

  /** @format int32 */
  expires_in?: number;
  id_token?: string;
  refresh_token?: string;
  token_type?: string;
}

export interface TokenResultPage {
  pageResponse?: PageResponse;
  values?: Token[];
}

export interface UiConfig {
  aboutHtml?: string;
  activity?: ActivityConfig;
  defaultMaxResults?: string;
  helpUrl?: string;
  htmlTitle?: string;
  maintenanceMessage?: string;
  namePattern?: string;

  /** @pattern ^return (true|false);$ */
  oncontextmenu?: string;
  process?: ProcessConfig;
  query?: QueryConfig;
  source?: SourceConfig;
  splash?: SplashConfig;
  theme?: ThemeConfig;
  uiPreferences?: UiPreferences;
  url?: UrlConfig;
  welcomeHtml?: string;
}

export interface UiPreferences {
  dateFormat?: string;
}

export interface UpdateAccountRequest {
  account?: Account;
  confirmPassword?: string;
  password?: string;
}

export interface UpdateStatusRequest {
  criteria?: FindMetaCriteria;
  currentStatus?: "UNLOCKED" | "LOCKED" | "DELETED";
  newStatus?: "UNLOCKED" | "LOCKED" | "DELETED";
}

export interface UploadDataRequest {
  /** @format int64 */
  effectiveMs?: number;
  feedName?: string;
  fileName?: string;
  key?: ResourceKey;
  metaData?: string;
  streamTypeName?: string;
}

export interface UrlConfig {
  apiKeys?: string;
  changepassword?: string;
  users?: string;
}

export interface User {
  /** @format int64 */
  createTimeMs?: number;
  createUser?: string;
  enabled?: boolean;
  group?: boolean;

  /** @format int32 */
  id?: number;
  name?: string;

  /** @format int64 */
  updateTimeMs?: number;
  updateUser?: string;
  uuid?: string;

  /** @format int32 */
  version?: number;
}

export interface UserAndPermissions {
  permissions?: string[];
  userId?: string;
}

export interface UserPermissionRequest {
  permission?: string;
}

export interface ValidateExpressionResult {
  ok?: boolean;
  string?: string;
}

export interface ValidateSessionResponse {
  redirectUri?: string;
  userId?: string;
  valid?: boolean;
}

export type VisComponentSettings = ComponentSettings & {
  json?: string;
  tableId?: string;
  tableSettings?: TableComponentSettings;
  visualisation?: DocRef;
};

export type VisResult = Result & { dataPoints?: number; jsonData?: string };

export type VisResultRequest = ComponentResultRequest & {
  requestedRange?: OffsetRange;
  visDashboardSettings?: VisComponentSettings;
};

export interface VisualisationDoc {
  /** @format int64 */
  createTime?: number;

  /** @format int64 */
  createTimeMs?: number;
  createUser?: string;
  description?: string;
  functionName?: string;
  name?: string;
  scriptRef?: DocRef;
  settings?: string;
  type?: string;

  /** @format int64 */
  updateTime?: number;

  /** @format int64 */
  updateTimeMs?: number;
  updateUser?: string;
  uuid?: string;
  version?: string;
}

export interface XPathFilter {
  ignoreCase?: boolean;
  matchType?: "EXISTS" | "CONTAINS" | "EQUALS" | "UNIQUE";
  path?: string;
  uniqueValues?: Record<string, Rec>;
  value?: string;
}

export interface XmlSchemaDoc {
  /** @format int64 */
  createTime?: number;

  /** @format int64 */
  createTimeMs?: number;
  createUser?: string;
  data?: string;
  deprecated?: boolean;
  description?: string;
  name?: string;
  namespaceURI?: string;
  schemaGroup?: string;
  systemId?: string;
  type?: string;

  /** @format int64 */
  updateTime?: number;

  /** @format int64 */
  updateTimeMs?: number;
  updateUser?: string;
  uuid?: string;
  version?: string;
}

export interface XsltDTO {
  data?: string;
  description?: string;
  name?: string;
  type?: string;
  uuid?: string;
}

export interface XsltDoc {
  /** @format int64 */
  createTime?: number;

  /** @format int64 */
  createTimeMs?: number;
  createUser?: string;
  data?: string;
  description?: string;
  name?: string;
  type?: string;

  /** @format int64 */
  updateTime?: number;

  /** @format int64 */
  updateTimeMs?: number;
  updateUser?: string;
  uuid?: string;
  version?: string;
}

export type RequestParams = Omit<RequestInit, "body" | "method"> & {
  secure?: boolean;
};

export type RequestQueryParamsType = Record<string | number, any>;

interface ApiConfig<SecurityDataType> {
  baseUrl?: string;
  baseApiParams?: RequestParams;
  securityWorker?: (securityData: SecurityDataType) => RequestParams;
}

interface HttpResponse<D extends unknown, E extends unknown = unknown> extends Response {
  data: D;
  error: E;
}

enum BodyType {
  Json,
  FormData,
  UrlEncoded,
}

export class HttpClient<SecurityDataType = unknown> {
  public baseUrl: string = "";
  private securityData: SecurityDataType = null as any;
  private securityWorker: null | ApiConfig<SecurityDataType>["securityWorker"] = null;

  private baseApiParams: RequestParams = {
    credentials: "same-origin",
    headers: {
      "Content-Type": "application/json",
    },
    redirect: "follow",
    referrerPolicy: "no-referrer",
  };

  constructor(apiConfig: ApiConfig<SecurityDataType> = {}) {
    Object.assign(this, apiConfig);
  }

  public setSecurityData = (data: SecurityDataType) => {
    this.securityData = data;
  };

  private addQueryParam(query: RequestQueryParamsType, key: string) {
    return (
      encodeURIComponent(key) + "=" + encodeURIComponent(Array.isArray(query[key]) ? query[key].join(",") : query[key])
    );
  }

  protected toQueryString(rawQuery?: RequestQueryParamsType): string {
    const query = rawQuery || {};
    const keys = Object.keys(query).filter((key) => "undefined" !== typeof query[key]);
    return keys
      .map((key) =>
        typeof query[key] === "object" && !Array.isArray(query[key])
          ? this.toQueryString(query[key] as object)
          : this.addQueryParam(query, key),
      )
      .join("&");
  }

  protected addQueryParams(rawQuery?: RequestQueryParamsType): string {
    const queryString = this.toQueryString(rawQuery);
    return queryString ? `?${queryString}` : "";
  }

  private bodyFormatters: Record<BodyType, (input: any) => any> = {
    [BodyType.Json]: JSON.stringify,
    [BodyType.FormData]: (input: any) =>
      Object.keys(input).reduce((data, key) => {
        data.append(key, input[key]);
        return data;
      }, new FormData()),
    [BodyType.UrlEncoded]: (input: any) => this.toQueryString(input),
  };

  private mergeRequestOptions(params: RequestParams, securityParams?: RequestParams): RequestParams {
    return {
      ...this.baseApiParams,
      ...params,
      ...(securityParams || {}),
      headers: {
        ...(this.baseApiParams.headers || {}),
        ...(params.headers || {}),
        ...((securityParams && securityParams.headers) || {}),
      },
    };
  }

  private safeParseResponse = <T = any, E = any>(response: Response): Promise<HttpResponse<T, E>> => {
    const r = response as HttpResponse<T, E>;
    r.data = (null as unknown) as T;
    r.error = (null as unknown) as E;

    return response
      .json()
      .then((data) => {
        if (r.ok) {
          r.data = data;
        } else {
          r.error = data;
        }
        return r;
      })
      .catch((e) => {
        r.error = e;
        return r;
      });
  };

  public request = <T = any, E = any>(
    path: string,
    method: string,
    { secure, ...params }: RequestParams = {},
    body?: any,
    bodyType?: BodyType,
    secureByDefault?: boolean,
  ): Promise<HttpResponse<T>> => {
    const requestUrl = `${this.baseUrl}${path}`;
    const secureOptions =
      (secureByDefault || secure) && this.securityWorker ? this.securityWorker(this.securityData) : {};
    const requestOptions = {
      ...this.mergeRequestOptions(params, secureOptions),
      method,
      body: body ? this.bodyFormatters[bodyType || BodyType.Json](body) : null,
    };

    return fetch(requestUrl, requestOptions).then(async (response) => {
      const data = await this.safeParseResponse<T, E>(response);
      if (!response.ok) throw data;
      return data;
    });
  };
}

/**
 * @title No title
 */
export class Api<SecurityDataType = any> extends HttpClient<SecurityDataType> {
  account = {
    /**
     * No description
     *
     * @name List5
     * @request GET:/account/v1
     */
    list5: (params?: RequestParams) => this.request<any, AccountResultPage>(`/account/v1`, "GET", params),

    /**
     * No description
     *
     * @name Create10
     * @request POST:/account/v1
     */
    create10: (data: CreateAccountRequest, params?: RequestParams) =>
      this.request<any, number>(`/account/v1`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Search7
     * @request POST:/account/v1/search
     */
    search7: (data: SearchAccountRequest, params?: RequestParams) =>
      this.request<any, AccountResultPage>(`/account/v1/search`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Delete10
     * @request DELETE:/account/v1/{id}
     */
    delete10: (id: number, params?: RequestParams) => this.request<any, boolean>(`/account/v1/${id}`, "DELETE", params),

    /**
     * No description
     *
     * @name Read18
     * @request GET:/account/v1/{id}
     */
    read18: (id: number, params?: RequestParams) => this.request<any, Account>(`/account/v1/${id}`, "GET", params),

    /**
     * No description
     *
     * @name Update21
     * @request PUT:/account/v1/{id}
     */
    update21: (id: number, data: UpdateAccountRequest, params?: RequestParams) =>
      this.request<any, boolean>(`/account/v1/${id}`, "PUT", params, data, BodyType.Json),
  };
  activity = {
    /**
     * No description
     *
     * @name List
     * @request GET:/activity/v1
     */
    list: (query?: { filter?: string }, params?: RequestParams) =>
      this.request<any, ResultPageActivity>(`/activity/v1${this.addQueryParams(query)}`, "GET", params),

    /**
     * No description
     *
     * @name Create
     * @request POST:/activity/v1
     */
    create: (params?: RequestParams) => this.request<any, Activity>(`/activity/v1`, "POST", params),

    /**
     * No description
     *
     * @name AcknowledgeSplash
     * @request POST:/activity/v1/acknowledge
     */
    acknowledgeSplash: (data: AcknowledgeSplashRequest, params?: RequestParams) =>
      this.request<any, boolean>(`/activity/v1/acknowledge`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name GetCurrentActivity
     * @request GET:/activity/v1/current
     */
    getCurrentActivity: (params?: RequestParams) => this.request<any, Activity>(`/activity/v1/current`, "GET", params),

    /**
     * No description
     *
     * @name SetCurrentActivity
     * @request PUT:/activity/v1/current
     */
    setCurrentActivity: (data: Activity, params?: RequestParams) =>
      this.request<any, Activity>(`/activity/v1/current`, "PUT", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name ListFieldDefinitions
     * @request GET:/activity/v1/fields
     */
    listFieldDefinitions: (params?: RequestParams) =>
      this.request<any, FilterFieldDefinition[]>(`/activity/v1/fields`, "GET", params),

    /**
     * No description
     *
     * @name Validate
     * @request POST:/activity/v1/validate
     */
    validate: (data: Activity, params?: RequestParams) =>
      this.request<any, ActivityValidationResult>(`/activity/v1/validate`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Delete
     * @request DELETE:/activity/v1/{id}
     */
    delete: (id: number, params?: RequestParams) => this.request<any, boolean>(`/activity/v1/${id}`, "DELETE", params),

    /**
     * No description
     *
     * @name Read
     * @request GET:/activity/v1/{id}
     */
    read: (id: number, params?: RequestParams) => this.request<any, Activity>(`/activity/v1/${id}`, "GET", params),

    /**
     * No description
     *
     * @name Update
     * @request PUT:/activity/v1/{id}
     */
    update: (id: number, data: Activity, params?: RequestParams) =>
      this.request<any, Activity>(`/activity/v1/${id}`, "PUT", params, data, BodyType.Json),
  };
  annotation = {
    /**
     * No description
     *
     * @name Get
     * @request GET:/annotation/v1
     */
    get: (query?: { annotationId?: number }, params?: RequestParams) =>
      this.request<any, AnnotationDetail>(`/annotation/v1${this.addQueryParams(query)}`, "GET", params),

    /**
     * No description
     *
     * @name CreateEntry
     * @request POST:/annotation/v1
     */
    createEntry: (data: CreateEntryRequest, params?: RequestParams) =>
      this.request<any, AnnotationDetail>(`/annotation/v1`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name GetComment
     * @request GET:/annotation/v1/comment
     */
    getComment: (query?: { filter?: string }, params?: RequestParams) =>
      this.request<any, string[]>(`/annotation/v1/comment${this.addQueryParams(query)}`, "GET", params),

    /**
     * No description
     *
     * @name Link
     * @request POST:/annotation/v1/link
     */
    link: (data: EventLink, params?: RequestParams) =>
      this.request<any, EventId[]>(`/annotation/v1/link`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name GetLinkedEvents
     * @request GET:/annotation/v1/linkedEvents
     */
    getLinkedEvents: (query?: { annotationId?: number }, params?: RequestParams) =>
      this.request<any, EventId[]>(`/annotation/v1/linkedEvents${this.addQueryParams(query)}`, "GET", params),

    /**
     * No description
     *
     * @name SetAssignedTo
     * @request POST:/annotation/v1/setAssignedTo
     */
    setAssignedTo: (data: SetAssignedToRequest, params?: RequestParams) =>
      this.request<any, number>(`/annotation/v1/setAssignedTo`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name SetStatus
     * @request POST:/annotation/v1/setStatus
     */
    setStatus: (data: SetStatusRequest, params?: RequestParams) =>
      this.request<any, number>(`/annotation/v1/setStatus`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name GetStatus
     * @request GET:/annotation/v1/status
     */
    getStatus: (query?: { filter?: string }, params?: RequestParams) =>
      this.request<any, string[]>(`/annotation/v1/status${this.addQueryParams(query)}`, "GET", params),

    /**
     * No description
     *
     * @name Unlink
     * @request POST:/annotation/v1/unlink
     */
    unlink: (data: EventLink, params?: RequestParams) =>
      this.request<any, EventId[]>(`/annotation/v1/unlink`, "POST", params, data, BodyType.Json),
  };
  appPermissions = {
    /**
     * No description
     *
     * @name GetAllPermissionNames
     * @request GET:/appPermissions/v1
     */
    getAllPermissionNames: (params?: RequestParams) => this.request<any, any>(`/appPermissions/v1`, "GET", params),

    /**
     * No description
     *
     * @name GetPermissionNamesForUserName
     * @request GET:/appPermissions/v1/byName/{userName}
     */
    getPermissionNamesForUserName: (userName: string, params?: RequestParams) =>
      this.request<any, any>(`/appPermissions/v1/byName/${userName}`, "GET", params),

    /**
     * No description
     *
     * @name GetPermissionNamesForUser
     * @request GET:/appPermissions/v1/{userUuid}
     */
    getPermissionNamesForUser: (userUuid: string, params?: RequestParams) =>
      this.request<any, any>(`/appPermissions/v1/${userUuid}`, "GET", params),

    /**
     * No description
     *
     * @name RemovePermission1
     * @request DELETE:/appPermissions/v1/{userUuid}/{permission}
     */
    removePermission1: (userUuid: string, permission: string, params?: RequestParams) =>
      this.request<any, any>(`/appPermissions/v1/${userUuid}/${permission}`, "DELETE", params),

    /**
     * No description
     *
     * @name AddPermission1
     * @request POST:/appPermissions/v1/{userUuid}/{permission}
     */
    addPermission1: (userUuid: string, permission: string, params?: RequestParams) =>
      this.request<any, any>(`/appPermissions/v1/${userUuid}/${permission}`, "POST", params),
  };
  authentication = {
    /**
     * No description
     *
     * @name Logout
     * @request GET:/authentication/v1/logout
     */
    logout: (query: { redirect_uri: string }, params?: RequestParams) =>
      this.request<any, boolean>(`/authentication/v1/logout${this.addQueryParams(query)}`, "GET", params),

    /**
     * No description
     *
     * @name NeedsPasswordChange
     * @request GET:/authentication/v1/needsPasswordChange
     */
    needsPasswordChange: (query?: { email?: string }, params?: RequestParams) =>
      this.request<any, boolean>(`/authentication/v1/needsPasswordChange${this.addQueryParams(query)}`, "GET", params),

    /**
     * No description
     *
     * @name ChangePassword
     * @request POST:/authentication/v1/noauth/changePassword
     */
    changePassword: (data: ChangePasswordRequest, params?: RequestParams) =>
      this.request<any, ChangePasswordResponse>(
        `/authentication/v1/noauth/changePassword`,
        "POST",
        params,
        data,
        BodyType.Json,
      ),

    /**
     * No description
     *
     * @name ConfirmPassword
     * @request POST:/authentication/v1/noauth/confirmPassword
     */
    confirmPassword: (data: ConfirmPasswordRequest, params?: RequestParams) =>
      this.request<any, ConfirmPasswordResponse>(
        `/authentication/v1/noauth/confirmPassword`,
        "POST",
        params,
        data,
        BodyType.Json,
      ),

    /**
     * No description
     *
     * @name FetchPasswordPolicy
     * @request GET:/authentication/v1/noauth/fetchPasswordPolicy
     */
    fetchPasswordPolicy: (params?: RequestParams) =>
      this.request<any, PasswordPolicyConfig>(`/authentication/v1/noauth/fetchPasswordPolicy`, "GET", params),

    /**
     * No description
     *
     * @name GetAuthenticationState
     * @request GET:/authentication/v1/noauth/getAuthenticationState
     */
    getAuthenticationState: (params?: RequestParams) =>
      this.request<any, AuthenticationState>(`/authentication/v1/noauth/getAuthenticationState`, "GET", params),

    /**
     * No description
     *
     * @name Login
     * @request POST:/authentication/v1/noauth/login
     */
    login: (data: LoginRequest, params?: RequestParams) =>
      this.request<any, LoginResponse>(`/authentication/v1/noauth/login`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name ResetEmail
     * @request GET:/authentication/v1/noauth/reset/{email}
     */
    resetEmail: (email: string, params?: RequestParams) =>
      this.request<any, boolean>(`/authentication/v1/noauth/reset/${email}`, "GET", params),

    /**
     * No description
     *
     * @name ResetPassword
     * @request POST:/authentication/v1/resetPassword
     */
    resetPassword: (data: ResetPasswordRequest, params?: RequestParams) =>
      this.request<any, ChangePasswordResponse>(
        `/authentication/v1/resetPassword`,
        "POST",
        params,
        data,
        BodyType.Json,
      ),
  };
  authorisation = {
    /**
     * No description
     *
     * @name CreateUser
     * @request POST:/authorisation/v1/createUser
     */
    createUser: (query?: { id?: string }, params?: RequestParams) =>
      this.request<any, any>(`/authorisation/v1/createUser${this.addQueryParams(query)}`, "POST", params),

    /**
     * No description
     *
     * @name HasPermission
     * @request POST:/authorisation/v1/hasPermission
     */
    hasPermission: (data: UserPermissionRequest, params?: RequestParams) =>
      this.request<any, any>(`/authorisation/v1/hasPermission`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name IsAuthorised
     * @request POST:/authorisation/v1/isAuthorised
     */
    isAuthorised: (data: string, params?: RequestParams) =>
      this.request<any, any>(`/authorisation/v1/isAuthorised`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name SetUserStatus
     * @request GET:/authorisation/v1/setUserStatus
     */
    setUserStatus: (query?: { userId?: string; status?: string }, params?: RequestParams) =>
      this.request<any, any>(`/authorisation/v1/setUserStatus${this.addQueryParams(query)}`, "GET", params),
  };
  cache = {
    /**
     * No description
     *
     * @name Clear
     * @request DELETE:/cache/v1
     */
    clear: (query?: { cacheName?: string; nodeName?: string }, params?: RequestParams) =>
      this.request<any, number>(`/cache/v1${this.addQueryParams(query)}`, "DELETE", params),

    /**
     * No description
     *
     * @name List1
     * @request GET:/cache/v1
     */
    list1: (params?: RequestParams) => this.request<any, string[]>(`/cache/v1`, "GET", params),

    /**
     * No description
     *
     * @name Info
     * @request GET:/cache/v1/info
     */
    info: (query?: { cacheName?: string; nodeName?: string }, params?: RequestParams) =>
      this.request<any, CacheInfoResponse>(`/cache/v1/info${this.addQueryParams(query)}`, "GET", params),
  };
  cluster = {
    /**
     * No description
     *
     * @name KeepLockAlive
     * @request PUT:/cluster/lock/v1/keepALive/{nodeName}
     */
    keepLockAlive: (nodeName: string, data: ClusterLockKey, params?: RequestParams) =>
      this.request<any, boolean>(`/cluster/lock/v1/keepALive/${nodeName}`, "PUT", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name ReleaseLock
     * @request PUT:/cluster/lock/v1/release/{nodeName}
     */
    releaseLock: (nodeName: string, data: ClusterLockKey, params?: RequestParams) =>
      this.request<any, boolean>(`/cluster/lock/v1/release/${nodeName}`, "PUT", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name TryLock
     * @request PUT:/cluster/lock/v1/try/{nodeName}
     */
    tryLock: (nodeName: string, data: ClusterLockKey, params?: RequestParams) =>
      this.request<any, boolean>(`/cluster/lock/v1/try/${nodeName}`, "PUT", params, data, BodyType.Json),
  };
  config = {
    /**
     * No description
     *
     * @name Create1
     * @request POST:/config/v1
     */
    create1: (data: ConfigProperty, params?: RequestParams) =>
      this.request<any, ConfigProperty>(`/config/v1`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Update1
     * @request PUT:/config/v1/clusterProperties/{propertyName}/dbOverrideValue
     */
    update1: (propertyName: string, data: ConfigProperty, params?: RequestParams) =>
      this.request<any, ConfigProperty>(
        `/config/v1/clusterProperties/${propertyName}/dbOverrideValue`,
        "PUT",
        params,
        data,
        BodyType.Json,
      ),

    /**
     * No description
     *
     * @name GetYamlValueByNodeAndName
     * @request GET:/config/v1/clusterProperties/{propertyName}/yamlOverrideValue/{nodeName}
     */
    getYamlValueByNodeAndName: (propertyName: string, nodeName: string, params?: RequestParams) =>
      this.request<any, OverrideValueString>(
        `/config/v1/clusterProperties/${propertyName}/yamlOverrideValue/${nodeName}`,
        "GET",
        params,
      ),

    /**
     * No description
     *
     * @name FetchUiConfig
     * @request GET:/config/v1/noauth/fetchUiConfig
     */
    fetchUiConfig: (params?: RequestParams) =>
      this.request<any, UiConfig>(`/config/v1/noauth/fetchUiConfig`, "GET", params),

    /**
     * No description
     *
     * @name ListByNode
     * @request POST:/config/v1/nodeProperties/{nodeName}
     */
    listByNode: (nodeName: string, data: GlobalConfigCriteria, params?: RequestParams) =>
      this.request<any, ListConfigResponse>(
        `/config/v1/nodeProperties/${nodeName}`,
        "POST",
        params,
        data,
        BodyType.Json,
      ),

    /**
     * No description
     *
     * @name List2
     * @request POST:/config/v1/properties
     */
    list2: (data: GlobalConfigCriteria, params?: RequestParams) =>
      this.request<any, ListConfigResponse>(`/config/v1/properties`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name GetPropertyByName
     * @request GET:/config/v1/properties/{propertyName}
     */
    getPropertyByName: (propertyName: string, params?: RequestParams) =>
      this.request<any, ConfigProperty>(`/config/v1/properties/${propertyName}`, "GET", params),
  };
  content = {
    /**
     * No description
     *
     * @name ConfirmImport
     * @request POST:/content/v1/confirmImport
     */
    confirmImport: (data: ResourceKey, params?: RequestParams) =>
      this.request<any, ImportState[]>(`/content/v1/confirmImport`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name ExportContent
     * @request POST:/content/v1/export
     */
    exportContent: (data: DocRefs, params?: RequestParams) =>
      this.request<any, ResourceGeneration>(`/content/v1/export`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name FetchDependencies
     * @request POST:/content/v1/fetchDependencies
     */
    fetchDependencies: (data: DependencyCriteria, params?: RequestParams) =>
      this.request<any, ResultPageDependency>(`/content/v1/fetchDependencies`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name ImportContent
     * @request POST:/content/v1/import
     */
    importContent: (data: ImportConfigRequest, params?: RequestParams) =>
      this.request<any, ResourceKey>(`/content/v1/import`, "POST", params, data, BodyType.Json),
  };
  dashboard = {
    /**
     * No description
     *
     * @name DownloadQuery
     * @request POST:/dashboard/v1/downloadQuery
     */
    downloadQuery: (data: DownloadQueryRequest, params?: RequestParams) =>
      this.request<any, ResourceGeneration>(`/dashboard/v1/downloadQuery`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name DownloadSearchResults
     * @request POST:/dashboard/v1/downloadSearchResults
     */
    downloadSearchResults: (data: DownloadSearchResultsRequest, params?: RequestParams) =>
      this.request<any, ResourceGeneration>(`/dashboard/v1/downloadSearchResults`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name FetchTimeZones
     * @request GET:/dashboard/v1/fetchTimeZones
     */
    fetchTimeZones: (params?: RequestParams) =>
      this.request<any, string[]>(`/dashboard/v1/fetchTimeZones`, "GET", params),

    /**
     * No description
     *
     * @name FetchFunctions
     * @request GET:/dashboard/v1/functions
     */
    fetchFunctions: (params?: RequestParams) =>
      this.request<any, FunctionSignature[]>(`/dashboard/v1/functions`, "GET", params),

    /**
     * No description
     *
     * @name Poll
     * @request POST:/dashboard/v1/poll
     */
    poll: (data: SearchBusPollRequest, params?: RequestParams) =>
      this.request<any, SearchResponse[]>(`/dashboard/v1/poll`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Read1
     * @request POST:/dashboard/v1/read
     */
    read1: (data: DocRef, params?: RequestParams) =>
      this.request<any, DashboardDoc>(`/dashboard/v1/read`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Update2
     * @request PUT:/dashboard/v1/update
     */
    update2: (data: DashboardDoc, params?: RequestParams) =>
      this.request<any, DashboardDoc>(`/dashboard/v1/update`, "PUT", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name ValidateExpression
     * @request POST:/dashboard/v1/validateExpression
     */
    validateExpression: (data: string, params?: RequestParams) =>
      this.request<any, ValidateExpressionResult>(
        `/dashboard/v1/validateExpression`,
        "POST",
        params,
        data,
        BodyType.Json,
      ),
  };
  data = {
    /**
     * No description
     *
     * @name Download
     * @request POST:/data/v1/download
     */
    download: (data: FindMetaCriteria, params?: RequestParams) =>
      this.request<any, ResourceGeneration>(`/data/v1/download`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Info1
     * @request GET:/data/v1/info/{id}
     */
    info1: (id: number, params?: RequestParams) =>
      this.request<any, DataInfoSection[]>(`/data/v1/info/${id}`, "GET", params),

    /**
     * No description
     *
     * @name Upload
     * @request POST:/data/v1/upload
     */
    upload: (data: UploadDataRequest, params?: RequestParams) =>
      this.request<any, ResourceKey>(`/data/v1/upload`, "POST", params, data, BodyType.Json),
  };
  dataRetentionRules = {
    /**
     * No description
     *
     * @name GetRetentionDeletionSummary
     * @request POST:/dataRetentionRules/v1/impactSummary
     */
    getRetentionDeletionSummary: (data: DataRetentionDeleteSummaryRequest, params?: RequestParams) =>
      this.request<any, DataRetentionDeleteSummaryResponse>(
        `/dataRetentionRules/v1/impactSummary`,
        "POST",
        params,
        data,
        BodyType.Json,
      ),

    /**
     * No description
     *
     * @name CancelQuery
     * @request DELETE:/dataRetentionRules/v1/impactSummary/{queryId}
     */
    cancelQuery: (queryId: string, params?: RequestParams) =>
      this.request<any, boolean>(`/dataRetentionRules/v1/impactSummary/${queryId}`, "DELETE", params),

    /**
     * No description
     *
     * @name Read3
     * @request POST:/dataRetentionRules/v1/read
     */
    read3: (params?: RequestParams) =>
      this.request<any, DataRetentionRules>(`/dataRetentionRules/v1/read`, "POST", params),

    /**
     * No description
     *
     * @name Update4
     * @request PUT:/dataRetentionRules/v1/update
     */
    update4: (data: DataRetentionRules, params?: RequestParams) =>
      this.request<any, DataRetentionRules>(`/dataRetentionRules/v1/update`, "PUT", params, data, BodyType.Json),
  };
  dataSource = {
    /**
     * No description
     *
     * @name FetchFields
     * @request POST:/dataSource/v1/fetchFields
     */
    fetchFields: (data: DocRef, params?: RequestParams) =>
      this.request<any, AbstractField[]>(`/dataSource/v1/fetchFields`, "POST", params, data, BodyType.Json),
  };
  dbStatus = {
    /**
     * No description
     *
     * @name GetSystemTableStatus
     * @request GET:/dbStatus/v1
     */
    getSystemTableStatus: (params?: RequestParams) =>
      this.request<any, ResultPageDBTableStatus>(`/dbStatus/v1`, "GET", params),

    /**
     * No description
     *
     * @name FindSystemTableStatus
     * @request POST:/dbStatus/v1
     */
    findSystemTableStatus: (data: FindDBTableCriteria, params?: RequestParams) =>
      this.request<any, ResultPageDBTableStatus>(`/dbStatus/v1`, "POST", params, data, BodyType.Json),
  };
  dictionary = {
    /**
     * No description
     *
     * @name Download1
     * @request POST:/dictionary/v1/download
     */
    download1: (data: DocRef, params?: RequestParams) =>
      this.request<any, ResourceGeneration>(`/dictionary/v1/download`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name ExportDocument1
     * @request POST:/dictionary/v1/export
     */
    exportDocument1: (data: DocRef, params?: RequestParams) =>
      this.request<any, Base64EncodedDocumentData>(`/dictionary/v1/export`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name ImportDocument1
     * @request POST:/dictionary/v1/import
     */
    importDocument1: (data: Base64EncodedDocumentData, params?: RequestParams) =>
      this.request<any, DocRef>(`/dictionary/v1/import`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name ListDocuments1
     * @request GET:/dictionary/v1/list
     */
    listDocuments1: (params?: RequestParams) => this.request<any, DocRef[]>(`/dictionary/v1/list`, "GET", params),

    /**
     * No description
     *
     * @name Read5
     * @request POST:/dictionary/v1/read
     */
    read5: (data: DocRef, params?: RequestParams) =>
      this.request<any, DictionaryDoc>(`/dictionary/v1/read`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Update6
     * @request PUT:/dictionary/v1/update
     */
    update6: (data: DictionaryDoc, params?: RequestParams) =>
      this.request<any, DictionaryDoc>(`/dictionary/v1/update`, "PUT", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Fetch
     * @request GET:/dictionary/v1/{dictionaryUuid}
     */
    fetch: (dictionaryUuid: string, params?: RequestParams) =>
      this.request<any, DictionaryDTO>(`/dictionary/v1/${dictionaryUuid}`, "GET", params),

    /**
     * No description
     *
     * @name Save
     * @request POST:/dictionary/v1/{dictionaryUuid}
     */
    save: (dictionaryUuid: string, data: DictionaryDTO, params?: RequestParams) =>
      this.request<any, any>(`/dictionary/v1/${dictionaryUuid}`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name ExportDocument
     * @request POST:/dictionary/v2/export
     */
    exportDocument: (data: DocRef, params?: RequestParams) =>
      this.request<any, Base64EncodedDocumentData>(`/dictionary/v2/export`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name ImportDocument
     * @request POST:/dictionary/v2/import
     */
    importDocument: (data: Base64EncodedDocumentData, params?: RequestParams) =>
      this.request<any, DocRef>(`/dictionary/v2/import`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name ListDocuments
     * @request GET:/dictionary/v2/list
     */
    listDocuments: (params?: RequestParams) => this.request<any, DocRef[]>(`/dictionary/v2/list`, "GET", params),
  };
  docPermissions = {
    /**
     * No description
     *
     * @name ClearDocumentPermissions
     * @request DELETE:/docPermissions/v1/forDoc/{docUuid}
     */
    clearDocumentPermissions: (docUuid: string, params?: RequestParams) =>
      this.request<any, any>(`/docPermissions/v1/forDoc/${docUuid}`, "DELETE", params),

    /**
     * No description
     *
     * @name GetPermissionsForDocument
     * @request GET:/docPermissions/v1/forDoc/{docUuid}
     */
    getPermissionsForDocument: (docUuid: string, params?: RequestParams) =>
      this.request<any, any>(`/docPermissions/v1/forDoc/${docUuid}`, "GET", params),

    /**
     * No description
     *
     * @name RemovePermissionForDocumentForUser
     * @request DELETE:/docPermissions/v1/forDocForUser/{docUuid}/{userUuid}
     */
    removePermissionForDocumentForUser: (docUuid: string, userUuid: string, params?: RequestParams) =>
      this.request<any, any>(`/docPermissions/v1/forDocForUser/${docUuid}/${userUuid}`, "DELETE", params),

    /**
     * No description
     *
     * @name GetPermissionsForDocumentForUser
     * @request GET:/docPermissions/v1/forDocForUser/{docUuid}/{userUuid}
     */
    getPermissionsForDocumentForUser: (docUuid: string, userUuid: string, params?: RequestParams) =>
      this.request<any, any>(`/docPermissions/v1/forDocForUser/${docUuid}/${userUuid}`, "GET", params),

    /**
     * No description
     *
     * @name RemovePermission
     * @request DELETE:/docPermissions/v1/forDocForUser/{docUuid}/{userUuid}/{permissionName}
     */
    removePermission: (docUuid: string, userUuid: string, permissionName: string, params?: RequestParams) =>
      this.request<any, any>(
        `/docPermissions/v1/forDocForUser/${docUuid}/${userUuid}/${permissionName}`,
        "DELETE",
        params,
      ),

    /**
     * No description
     *
     * @name AddPermission
     * @request POST:/docPermissions/v1/forDocForUser/{docUuid}/{userUuid}/{permissionName}
     */
    addPermission: (docUuid: string, userUuid: string, permissionName: string, params?: RequestParams) =>
      this.request<any, any>(
        `/docPermissions/v1/forDocForUser/${docUuid}/${userUuid}/${permissionName}`,
        "POST",
        params,
      ),

    /**
     * No description
     *
     * @name GetPermissionForDocType
     * @request GET:/docPermissions/v1/forDocType/{docType}
     */
    getPermissionForDocType: (docType: string, params?: RequestParams) =>
      this.request<any, any>(`/docPermissions/v1/forDocType/${docType}`, "GET", params),
  };
  elements = {
    /**
     * No description
     *
     * @name GetElementProperties
     * @request GET:/elements/v1/elementProperties
     */
    getElementProperties: (params?: RequestParams) =>
      this.request<any, any>(`/elements/v1/elementProperties`, "GET", params),

    /**
     * No description
     *
     * @name GetElements
     * @request GET:/elements/v1/elements
     */
    getElements: (params?: RequestParams) => this.request<any, any>(`/elements/v1/elements`, "GET", params),
  };
  entityEvent = {
    /**
     * No description
     *
     * @name FireEvent
     * @request PUT:/entityEvent/v1/{nodeName}
     */
    fireEvent: (nodeName: string, data: EntityEvent, params?: RequestParams) =>
      this.request<any, boolean>(`/entityEvent/v1/${nodeName}`, "PUT", params, data, BodyType.Json),
  };
  explorer = {
    /**
     * No description
     *
     * @name GetExplorerTree
     * @request GET:/explorer/v1/all
     */
    getExplorerTree: (params?: RequestParams) => this.request<any, any>(`/explorer/v1/all`, "GET", params),

    /**
     * No description
     *
     * @name CopyDocument
     * @request POST:/explorer/v1/copy
     */
    copyDocument: (data: CopyOp, params?: RequestParams) =>
      this.request<any, any>(`/explorer/v1/copy`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name CreateDocument
     * @request POST:/explorer/v1/create
     */
    createDocument: (data: CreateOp, params?: RequestParams) =>
      this.request<any, any>(`/explorer/v1/create`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name DeleteDocument
     * @request DELETE:/explorer/v1/delete
     */
    deleteDocument: (data: DocRef[], params?: RequestParams) =>
      this.request<any, any>(`/explorer/v1/delete`, "DELETE", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name GetDocRefTypes
     * @request GET:/explorer/v1/docRefTypes
     */
    getDocRefTypes: (params?: RequestParams) => this.request<any, any>(`/explorer/v1/docRefTypes`, "GET", params),

    /**
     * No description
     *
     * @name GetDocInfo
     * @request GET:/explorer/v1/info/{type}/{uuid}
     */
    getDocInfo: (type: string, uuid: string, params?: RequestParams) =>
      this.request<any, DocRefInfo>(`/explorer/v1/info/${type}/${uuid}`, "GET", params),

    /**
     * No description
     *
     * @name MoveDocument
     * @request PUT:/explorer/v1/move
     */
    moveDocument: (data: MoveOp, params?: RequestParams) =>
      this.request<any, any>(`/explorer/v1/move`, "PUT", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name RenameDocument
     * @request PUT:/explorer/v1/rename
     */
    renameDocument: (data: RenameOp, params?: RequestParams) =>
      this.request<any, any>(`/explorer/v1/rename`, "PUT", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Search
     * @request GET:/explorer/v1/search
     */
    search: (query?: { searchTerm?: string; pageOffset?: number; pageSize?: number }, params?: RequestParams) =>
      this.request<any, any>(`/explorer/v1/search${this.addQueryParams(query)}`, "GET", params),

    /**
     * No description
     *
     * @name Copy
     * @request POST:/explorer/v2/copy
     */
    copy: (data: ExplorerServiceCopyRequest, params?: RequestParams) =>
      this.request<any, BulkActionResult>(`/explorer/v2/copy`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Create4
     * @request POST:/explorer/v2/create
     */
    create4: (data: ExplorerServiceCreateRequest, params?: RequestParams) =>
      this.request<any, DocRef>(`/explorer/v2/create`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Delete3
     * @request DELETE:/explorer/v2/delete
     */
    delete3: (data: ExplorerServiceDeleteRequest, params?: RequestParams) =>
      this.request<any, BulkActionResult>(`/explorer/v2/delete`, "DELETE", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name FetchDocRefs
     * @request POST:/explorer/v2/fetchDocRefs
     */
    fetchDocRefs: (data: DocRef[], params?: RequestParams) =>
      this.request<any, DocRef[]>(`/explorer/v2/fetchDocRefs`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name FetchDocumentTypes
     * @request GET:/explorer/v2/fetchDocumentTypes
     */
    fetchDocumentTypes: (params?: RequestParams) =>
      this.request<any, DocumentTypes>(`/explorer/v2/fetchDocumentTypes`, "GET", params),

    /**
     * No description
     *
     * @name Fetch1
     * @request POST:/explorer/v2/fetchExplorerNodes
     */
    fetch1: (data: FindExplorerNodeCriteria, params?: RequestParams) =>
      this.request<any, FetchExplorerNodeResult>(
        `/explorer/v2/fetchExplorerNodes`,
        "POST",
        params,
        data,
        BodyType.Json,
      ),

    /**
     * No description
     *
     * @name FetchExplorerPermissions
     * @request POST:/explorer/v2/fetchExplorerPermissions
     */
    fetchExplorerPermissions: (data: ExplorerNode[], params?: RequestParams) =>
      this.request<any, ExplorerNodePermissions[]>(
        `/explorer/v2/fetchExplorerPermissions`,
        "POST",
        params,
        data,
        BodyType.Json,
      ),

    /**
     * No description
     *
     * @name Info2
     * @request POST:/explorer/v2/info
     */
    info2: (data: DocRef, params?: RequestParams) =>
      this.request<any, DocRefInfo>(`/explorer/v2/info`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Move
     * @request PUT:/explorer/v2/move
     */
    move: (data: ExplorerServiceMoveRequest, params?: RequestParams) =>
      this.request<any, BulkActionResult>(`/explorer/v2/move`, "PUT", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Rename
     * @request PUT:/explorer/v2/rename
     */
    rename: (data: ExplorerServiceRenameRequest, params?: RequestParams) =>
      this.request<any, DocRef>(`/explorer/v2/rename`, "PUT", params, data, BodyType.Json),
  };
  export = {
    /**
     * No description
     *
     * @name Export
     * @request GET:/export/v1
     */
    export: (params?: RequestParams) => this.request<any, any>(`/export/v1`, "GET", params),
  };
  feed = {
    /**
     * No description
     *
     * @name FetchSupportedEncodings
     * @request GET:/feed/v1/fetchSupportedEncodings
     */
    fetchSupportedEncodings: (params?: RequestParams) =>
      this.request<any, string[]>(`/feed/v1/fetchSupportedEncodings`, "GET", params),

    /**
     * No description
     *
     * @name Read6
     * @request POST:/feed/v1/read
     */
    read6: (data: DocRef, params?: RequestParams) =>
      this.request<any, FeedDoc>(`/feed/v1/read`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Update7
     * @request PUT:/feed/v1/update
     */
    update7: (data: FeedDoc, params?: RequestParams) =>
      this.request<any, FeedDoc>(`/feed/v1/update`, "PUT", params, data, BodyType.Json),
  };
  feedStatus = {
    /**
     * No description
     *
     * @name GetFeedStatus
     * @request POST:/feedStatus/v1/getFeedStatus
     */
    getFeedStatus: (data: GetFeedStatusRequest, params?: RequestParams) =>
      this.request<any, GetFeedStatusResponse>(`/feedStatus/v1/getFeedStatus`, "POST", params, data, BodyType.Json),
  };
  fsVolume = {
    /**
     * No description
     *
     * @name Create3
     * @request POST:/fsVolume/v1
     */
    create3: (data: FsVolume, params?: RequestParams) =>
      this.request<any, FsVolume>(`/fsVolume/v1`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Find1
     * @request POST:/fsVolume/v1/find
     */
    find1: (data: FindFsVolumeCriteria, params?: RequestParams) =>
      this.request<any, ResultPageFsVolume>(`/fsVolume/v1/find`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Rescan
     * @request GET:/fsVolume/v1/rescan
     */
    rescan: (params?: RequestParams) => this.request<any, boolean>(`/fsVolume/v1/rescan`, "GET", params),

    /**
     * No description
     *
     * @name Delete2
     * @request DELETE:/fsVolume/v1/{id}
     */
    delete2: (id: number, params?: RequestParams) => this.request<any, boolean>(`/fsVolume/v1/${id}`, "DELETE", params),

    /**
     * No description
     *
     * @name Read4
     * @request GET:/fsVolume/v1/{id}
     */
    read4: (id: number, params?: RequestParams) => this.request<any, FsVolume>(`/fsVolume/v1/${id}`, "GET", params),

    /**
     * No description
     *
     * @name Update5
     * @request PUT:/fsVolume/v1/{id}
     */
    update5: (id: number, data: FsVolume, params?: RequestParams) =>
      this.request<any, FsVolume>(`/fsVolume/v1/${id}`, "PUT", params, data, BodyType.Json),
  };
  index = {
    /**
     * No description
     *
     * @name ExportDocument2
     * @request POST:/index/v1/export
     */
    exportDocument2: (data: DocRef, params?: RequestParams) =>
      this.request<any, Base64EncodedDocumentData>(`/index/v1/export`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name ImportDocument2
     * @request POST:/index/v1/import
     */
    importDocument2: (data: Base64EncodedDocumentData, params?: RequestParams) =>
      this.request<any, DocRef>(`/index/v1/import`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name ListDocuments2
     * @request GET:/index/v1/list
     */
    listDocuments2: (params?: RequestParams) => this.request<any, DocRef[]>(`/index/v1/list`, "GET", params),

    /**
     * No description
     *
     * @name Fetch2
     * @request GET:/index/v1/{indexUuid}
     */
    fetch2: (indexUuid: string, params?: RequestParams) =>
      this.request<any, any>(`/index/v1/${indexUuid}`, "GET", params),

    /**
     * No description
     *
     * @name Save1
     * @request POST:/index/v1/{indexUuid}
     */
    save1: (indexUuid: string, data: IndexDoc, params?: RequestParams) =>
      this.request<any, any>(`/index/v1/${indexUuid}`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Read7
     * @request POST:/index/v2/read
     */
    read7: (data: DocRef, params?: RequestParams) =>
      this.request<any, IndexDoc>(`/index/v2/read`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name DeleteIndexShards
     * @request POST:/index/v2/shard/delete
     */
    deleteIndexShards: (data: FindIndexShardCriteria, query?: { nodeName?: string }, params?: RequestParams) =>
      this.request<any, number>(
        `/index/v2/shard/delete${this.addQueryParams(query)}`,
        "POST",
        params,
        data,
        BodyType.Json,
      ),

    /**
     * No description
     *
     * @name FindIndexShards
     * @request POST:/index/v2/shard/find
     */
    findIndexShards: (data: FindIndexShardCriteria, params?: RequestParams) =>
      this.request<any, ResultPageIndexShard>(`/index/v2/shard/find`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name FlushIndexShards
     * @request POST:/index/v2/shard/flush
     */
    flushIndexShards: (data: FindIndexShardCriteria, query?: { nodeName?: string }, params?: RequestParams) =>
      this.request<any, number>(
        `/index/v2/shard/flush${this.addQueryParams(query)}`,
        "POST",
        params,
        data,
        BodyType.Json,
      ),

    /**
     * No description
     *
     * @name Update10
     * @request PUT:/index/v2/update
     */
    update10: (data: IndexDoc, params?: RequestParams) =>
      this.request<any, IndexDoc>(`/index/v2/update`, "PUT", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Create8
     * @request POST:/index/volume/v2
     */
    create8: (data: IndexVolume, params?: RequestParams) =>
      this.request<any, IndexVolume>(`/index/volume/v2`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Find3
     * @request POST:/index/volume/v2/find
     */
    find3: (data: ExpressionCriteria, params?: RequestParams) =>
      this.request<any, ResultPageIndexVolume>(`/index/volume/v2/find`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Rescan1
     * @request DELETE:/index/volume/v2/rescan
     */
    rescan1: (query?: { nodeName?: string }, params?: RequestParams) =>
      this.request<any, boolean>(`/index/volume/v2/rescan${this.addQueryParams(query)}`, "DELETE", params),

    /**
     * No description
     *
     * @name Delete7
     * @request DELETE:/index/volume/v2/{id}
     */
    delete7: (id: number, params?: RequestParams) =>
      this.request<any, boolean>(`/index/volume/v2/${id}`, "DELETE", params),

    /**
     * No description
     *
     * @name Read9
     * @request GET:/index/volume/v2/{id}
     */
    read9: (id: number, params?: RequestParams) =>
      this.request<any, IndexVolume>(`/index/volume/v2/${id}`, "GET", params),

    /**
     * No description
     *
     * @name Update12
     * @request PUT:/index/volume/v2/{id}
     */
    update12: (id: number, data: IndexVolume, params?: RequestParams) =>
      this.request<any, IndexVolume>(`/index/volume/v2/${id}`, "PUT", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Create7
     * @request POST:/index/volumeGroup/v2
     */
    create7: (data: string, params?: RequestParams) =>
      this.request<any, IndexVolumeGroup>(`/index/volumeGroup/v2`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Find2
     * @request POST:/index/volumeGroup/v2/find
     */
    find2: (data: ExpressionCriteria, params?: RequestParams) =>
      this.request<any, ResultPageIndexVolumeGroup>(`/index/volumeGroup/v2/find`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Delete6
     * @request DELETE:/index/volumeGroup/v2/{id}
     */
    delete6: (id: number, params?: RequestParams) =>
      this.request<any, boolean>(`/index/volumeGroup/v2/${id}`, "DELETE", params),

    /**
     * No description
     *
     * @name Read8
     * @request GET:/index/volumeGroup/v2/{id}
     */
    read8: (id: number, params?: RequestParams) =>
      this.request<any, IndexVolumeGroup>(`/index/volumeGroup/v2/${id}`, "GET", params),

    /**
     * No description
     *
     * @name Update11
     * @request PUT:/index/volumeGroup/v2/{id}
     */
    update11: (id: number, data: IndexVolumeGroup, params?: RequestParams) =>
      this.request<any, IndexVolumeGroup>(`/index/volumeGroup/v2/${id}`, "PUT", params, data, BodyType.Json),
  };
  job = {
    /**
     * No description
     *
     * @name List4
     * @request GET:/job/v1
     */
    list4: (params?: RequestParams) => this.request<any, ResultPageJob>(`/job/v1`, "GET", params),

    /**
     * No description
     *
     * @name SetEnabled1
     * @request PUT:/job/v1/{id}/enabled
     */
    setEnabled1: (id: number, data: boolean, params?: RequestParams) =>
      this.request<any, any>(`/job/v1/${id}/enabled`, "PUT", params, data, BodyType.Json),
  };
  jobNode = {
    /**
     * No description
     *
     * @name List3
     * @request GET:/jobNode/v1
     */
    list3: (query?: { jobName?: string; nodeName?: string }, params?: RequestParams) =>
      this.request<any, ResultPageJobNode>(`/jobNode/v1${this.addQueryParams(query)}`, "GET", params),

    /**
     * No description
     *
     * @name Info3
     * @request GET:/jobNode/v1/info
     */
    info3: (query?: { jobName?: string; nodeName?: string }, params?: RequestParams) =>
      this.request<any, JobNodeInfo>(`/jobNode/v1/info${this.addQueryParams(query)}`, "GET", params),

    /**
     * No description
     *
     * @name SetEnabled
     * @request PUT:/jobNode/v1/{id}/enabled
     */
    setEnabled: (id: number, data: boolean, params?: RequestParams) =>
      this.request<any, any>(`/jobNode/v1/${id}/enabled`, "PUT", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name SetSchedule
     * @request PUT:/jobNode/v1/{id}/schedule
     */
    setSchedule: (id: number, data: string, params?: RequestParams) =>
      this.request<any, any>(`/jobNode/v1/${id}/schedule`, "PUT", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name SetTaskLimit
     * @request PUT:/jobNode/v1/{id}/taskLimit
     */
    setTaskLimit: (id: number, data: number, params?: RequestParams) =>
      this.request<any, any>(`/jobNode/v1/${id}/taskLimit`, "PUT", params, data, BodyType.Json),
  };
  kafkaConfig = {
    /**
     * No description
     *
     * @name Download2
     * @request POST:/kafkaConfig/v1/download
     */
    download2: (data: DocRef, params?: RequestParams) =>
      this.request<any, ResourceGeneration>(`/kafkaConfig/v1/download`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Read10
     * @request POST:/kafkaConfig/v1/read
     */
    read10: (data: DocRef, params?: RequestParams) =>
      this.request<any, KafkaConfigDoc>(`/kafkaConfig/v1/read`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Update13
     * @request PUT:/kafkaConfig/v1/update
     */
    update13: (data: KafkaConfigDoc, params?: RequestParams) =>
      this.request<any, KafkaConfigDoc>(`/kafkaConfig/v1/update`, "PUT", params, data, BodyType.Json),
  };
  meta = {
    /**
     * No description
     *
     * @name FindMetaRow
     * @request POST:/meta/v1/find
     */
    findMetaRow: (data: FindMetaCriteria, params?: RequestParams) =>
      this.request<any, ResultPageMetaRow>(`/meta/v1/find`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name GetReprocessSelectionSummary
     * @request POST:/meta/v1/getReprocessSelectionSummary
     */
    getReprocessSelectionSummary: (data: FindMetaCriteria, params?: RequestParams) =>
      this.request<any, SelectionSummary>(`/meta/v1/getReprocessSelectionSummary`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name GetSelectionSummary
     * @request POST:/meta/v1/getSelectionSummary
     */
    getSelectionSummary: (data: FindMetaCriteria, params?: RequestParams) =>
      this.request<any, SelectionSummary>(`/meta/v1/getSelectionSummary`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name GetTypes
     * @request GET:/meta/v1/getTypes
     */
    getTypes: (params?: RequestParams) => this.request<any, string[]>(`/meta/v1/getTypes`, "GET", params),

    /**
     * No description
     *
     * @name UpdateStatus
     * @request PUT:/meta/v1/update/status
     */
    updateStatus: (data: UpdateStatusRequest, params?: RequestParams) =>
      this.request<any, number>(`/meta/v1/update/status`, "PUT", params, data, BodyType.Json),
  };
  node = {
    /**
     * No description
     *
     * @name Find4
     * @request GET:/node/v1
     */
    find4: (params?: RequestParams) => this.request<any, FetchNodeStatusResponse>(`/node/v1`, "GET", params),

    /**
     * No description
     *
     * @name ListAllNodes
     * @request GET:/node/v1/all
     */
    listAllNodes: (params?: RequestParams) => this.request<any, string[]>(`/node/v1/all`, "GET", params),

    /**
     * No description
     *
     * @name ListEnabledNodes
     * @request GET:/node/v1/enabled
     */
    listEnabledNodes: (params?: RequestParams) => this.request<any, string[]>(`/node/v1/enabled`, "GET", params),

    /**
     * No description
     *
     * @name SetEnabled2
     * @request PUT:/node/v1/enabled/{nodeName}
     */
    setEnabled2: (nodeName: string, data: boolean, params?: RequestParams) =>
      this.request<any, any>(`/node/v1/enabled/${nodeName}`, "PUT", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Info4
     * @request GET:/node/v1/info/{nodeName}
     */
    info4: (nodeName: string, params?: RequestParams) =>
      this.request<any, ClusterNodeInfo>(`/node/v1/info/${nodeName}`, "GET", params),

    /**
     * No description
     *
     * @name Ping
     * @request GET:/node/v1/ping/{nodeName}
     */
    ping: (nodeName: string, params?: RequestParams) =>
      this.request<any, number>(`/node/v1/ping/${nodeName}`, "GET", params),

    /**
     * No description
     *
     * @name SetPriority
     * @request PUT:/node/v1/priority/{nodeName}
     */
    setPriority: (nodeName: string, data: number, params?: RequestParams) =>
      this.request<any, any>(`/node/v1/priority/${nodeName}`, "PUT", params, data, BodyType.Json),
  };
  oauth2 = {
    /**
     * No description
     *
     * @name OpenIdConfiguration
     * @request GET:/oauth2/v1/noauth/.well-known/openid-configuration
     */
    openIdConfiguration: (params?: RequestParams) =>
      this.request<any, string>(`/oauth2/v1/noauth/.well-known/openid-configuration`, "GET", params),

    /**
     * No description
     *
     * @name Auth
     * @request GET:/oauth2/v1/noauth/auth
     */
    auth: (
      query: {
        scope: string;
        response_type: string;
        client_id: string;
        redirect_uri: string;
        nonce?: string;
        state?: string;
        prompt?: string;
      },
      params?: RequestParams,
    ) => this.request<any, any>(`/oauth2/v1/noauth/auth${this.addQueryParams(query)}`, "GET", params),

    /**
     * No description
     *
     * @name Certs
     * @request GET:/oauth2/v1/noauth/certs
     */
    certs: (params?: RequestParams) =>
      this.request<any, Record<string, Record<string, object>[]>>(`/oauth2/v1/noauth/certs`, "GET", params),

    /**
     * No description
     *
     * @name Token
     * @request POST:/oauth2/v1/noauth/token
     */
    token: (data: TokenRequest, params?: RequestParams) =>
      this.request<any, TokenResponse>(`/oauth2/v1/noauth/token`, "POST", params, data, BodyType.Json),
  };
  permission = {
    /**
     * No description
     *
     * @name GetUserAndPermissions
     * @request GET:/permission/app/v1
     */
    getUserAndPermissions: (params?: RequestParams) =>
      this.request<any, UserAndPermissions>(`/permission/app/v1`, "GET", params),

    /**
     * No description
     *
     * @name ChangeUser
     * @request POST:/permission/app/v1/changeUser
     */
    changeUser: (data: ChangeUserRequest, params?: RequestParams) =>
      this.request<any, boolean>(`/permission/app/v1/changeUser`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name FetchAllPermissions
     * @request GET:/permission/app/v1/fetchAllPermissions
     */
    fetchAllPermissions: (params?: RequestParams) =>
      this.request<any, string[]>(`/permission/app/v1/fetchAllPermissions`, "GET", params),

    /**
     * No description
     *
     * @name FetchUserAppPermissions
     * @request POST:/permission/app/v1/fetchUserAppPermissions
     */
    fetchUserAppPermissions: (data: User, params?: RequestParams) =>
      this.request<any, UserAndPermissions>(
        `/permission/app/v1/fetchUserAppPermissions`,
        "POST",
        params,
        data,
        BodyType.Json,
      ),

    /**
     * No description
     *
     * @name FireChange
     * @request POST:/permission/changeEvent/v1/fireChange/{nodeName}
     */
    fireChange: (nodeName: string, data: PermissionChangeRequest, params?: RequestParams) =>
      this.request<any, boolean>(
        `/permission/changeEvent/v1/fireChange/${nodeName}`,
        "POST",
        params,
        data,
        BodyType.Json,
      ),

    /**
     * No description
     *
     * @name ChangeDocumentPermissions
     * @request POST:/permission/doc/v1/changeDocumentPermissions
     */
    changeDocumentPermissions: (data: ChangeDocumentPermissionsRequest, params?: RequestParams) =>
      this.request<any, boolean>(`/permission/doc/v1/changeDocumentPermissions`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name CheckDocumentPermission
     * @request POST:/permission/doc/v1/checkDocumentPermission
     */
    checkDocumentPermission: (data: CheckDocumentPermissionRequest, params?: RequestParams) =>
      this.request<any, boolean>(`/permission/doc/v1/checkDocumentPermission`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name CopyPermissionFromParent
     * @request POST:/permission/doc/v1/copyPermissionsFromParent
     */
    copyPermissionFromParent: (data: CopyPermissionsFromParentRequest, params?: RequestParams) =>
      this.request<any, DocumentPermissions>(
        `/permission/doc/v1/copyPermissionsFromParent`,
        "POST",
        params,
        data,
        BodyType.Json,
      ),

    /**
     * No description
     *
     * @name FetchAllDocumentPermissions
     * @request POST:/permission/doc/v1/fetchAllDocumentPermissions
     */
    fetchAllDocumentPermissions: (data: FetchAllDocumentPermissionsRequest, params?: RequestParams) =>
      this.request<any, DocumentPermissions>(
        `/permission/doc/v1/fetchAllDocumentPermissions`,
        "POST",
        params,
        data,
        BodyType.Json,
      ),

    /**
     * No description
     *
     * @name FilterUsers
     * @request POST:/permission/doc/v1/filterUsers
     */
    filterUsers: (data: FilterUsersRequest, params?: RequestParams) =>
      this.request<any, User[]>(`/permission/doc/v1/filterUsers`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name GetPermissionForDocType1
     * @request GET:/permission/doc/v1/getPermissionForDocType/${docType}
     */
    getPermissionForDocType1: (docType: string, params?: RequestParams) =>
      this.request<any, string[]>(`/permission/doc/v1/getPermissionForDocType/$${docType}`, "GET", params),
  };
  pipeline = {
    /**
     * No description
     *
     * @name FetchPipelineData
     * @request POST:/pipeline/v1/fetchPipelineData
     */
    fetchPipelineData: (data: DocRef, params?: RequestParams) =>
      this.request<any, PipelineData[]>(`/pipeline/v1/fetchPipelineData`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name FetchPipelineXml
     * @request POST:/pipeline/v1/fetchPipelineXml
     */
    fetchPipelineXml: (data: DocRef, params?: RequestParams) =>
      this.request<any, FetchPipelineXmlResponse>(`/pipeline/v1/fetchPipelineXml`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name GetPropertyTypes
     * @request GET:/pipeline/v1/propertyTypes
     */
    getPropertyTypes: (params?: RequestParams) =>
      this.request<any, FetchPropertyTypesResult[]>(`/pipeline/v1/propertyTypes`, "GET", params),

    /**
     * No description
     *
     * @name Read11
     * @request POST:/pipeline/v1/read
     */
    read11: (data: DocRef, params?: RequestParams) =>
      this.request<any, PipelineDoc>(`/pipeline/v1/read`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name SavePipelineXml
     * @request PUT:/pipeline/v1/savePipelineXml
     */
    savePipelineXml: (data: SavePipelineXmlRequest, params?: RequestParams) =>
      this.request<any, boolean>(`/pipeline/v1/savePipelineXml`, "PUT", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Update14
     * @request PUT:/pipeline/v1/update
     */
    update14: (data: PipelineDoc, params?: RequestParams) =>
      this.request<any, PipelineDoc>(`/pipeline/v1/update`, "PUT", params, data, BodyType.Json),
  };
  pipelines = {
    /**
     * No description
     *
     * @name Search4
     * @request GET:/pipelines/v1
     */
    search4: (query?: { offset?: number; pageSize?: number; filter?: string }, params?: RequestParams) =>
      this.request<any, any>(`/pipelines/v1${this.addQueryParams(query)}`, "GET", params),

    /**
     * No description
     *
     * @name CreateInherited
     * @request POST:/pipelines/v1/{parentPipelineId}/inherit
     */
    createInherited: (parentPipelineId: string, data: DocRef, params?: RequestParams) =>
      this.request<any, any>(`/pipelines/v1/${parentPipelineId}/inherit`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Fetch3
     * @request GET:/pipelines/v1/{pipelineId}
     */
    fetch3: (pipelineId: string, params?: RequestParams) =>
      this.request<any, any>(`/pipelines/v1/${pipelineId}`, "GET", params),

    /**
     * No description
     *
     * @name Save2
     * @request POST:/pipelines/v1/{pipelineId}
     */
    save2: (pipelineId: string, data: PipelineDTO, params?: RequestParams) =>
      this.request<any, any>(`/pipelines/v1/${pipelineId}`, "POST", params, data, BodyType.Json),
  };
  processor = {
    /**
     * No description
     *
     * @name Delete9
     * @request DELETE:/processor/v1/{id}
     */
    delete9: (id: number, params?: RequestParams) => this.request<any, any>(`/processor/v1/${id}`, "DELETE", params),

    /**
     * No description
     *
     * @name SetEnabled4
     * @request PUT:/processor/v1/{id}/enabled
     */
    setEnabled4: (id: number, data: boolean, params?: RequestParams) =>
      this.request<any, any>(`/processor/v1/${id}/enabled`, "PUT", params, data, BodyType.Json),
  };
  processorFilter = {
    /**
     * No description
     *
     * @name Create9
     * @request POST:/processorFilter/v1
     */
    create9: (data: CreateProcessFilterRequest, params?: RequestParams) =>
      this.request<any, ProcessorFilter>(`/processorFilter/v1`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Find5
     * @request POST:/processorFilter/v1/find
     */
    find5: (data: FetchProcessorRequest, params?: RequestParams) =>
      this.request<any, ProcessorListRowResultPage>(`/processorFilter/v1/find`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Reprocess
     * @request POST:/processorFilter/v1/reprocess
     */
    reprocess: (data: CreateReprocessFilterRequest, params?: RequestParams) =>
      this.request<any, ReprocessDataInfo[]>(`/processorFilter/v1/reprocess`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Delete8
     * @request DELETE:/processorFilter/v1/{id}
     */
    delete8: (id: number, params?: RequestParams) =>
      this.request<any, any>(`/processorFilter/v1/${id}`, "DELETE", params),

    /**
     * No description
     *
     * @name Read14
     * @request GET:/processorFilter/v1/{id}
     */
    read14: (id: number, params?: RequestParams) =>
      this.request<any, ProcessorFilter>(`/processorFilter/v1/${id}`, "GET", params),

    /**
     * No description
     *
     * @name Update17
     * @request PUT:/processorFilter/v1/{id}
     */
    update17: (id: number, data: ProcessorFilter, params?: RequestParams) =>
      this.request<any, ProcessorFilter>(`/processorFilter/v1/${id}`, "PUT", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name SetEnabled3
     * @request PUT:/processorFilter/v1/{id}/enabled
     */
    setEnabled3: (id: number, data: boolean, params?: RequestParams) =>
      this.request<any, any>(`/processorFilter/v1/${id}/enabled`, "PUT", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name SetPriority1
     * @request PUT:/processorFilter/v1/{id}/priority
     */
    setPriority1: (id: number, data: number, params?: RequestParams) =>
      this.request<any, any>(`/processorFilter/v1/${id}/priority`, "PUT", params, data, BodyType.Json),
  };
  processorTask = {
    /**
     * No description
     *
     * @name AbandonTasks
     * @request POST:/processorTask/v1/abandon/{nodeName}
     */
    abandonTasks: (nodeName: string, data: ProcessorTaskList, params?: RequestParams) =>
      this.request<any, boolean>(`/processorTask/v1/abandon/${nodeName}`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name AssignTasks
     * @request POST:/processorTask/v1/assign/{nodeName}
     */
    assignTasks: (nodeName: string, data: AssignTasksRequest, params?: RequestParams) =>
      this.request<any, ProcessorTaskList>(`/processorTask/v1/assign/${nodeName}`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Find6
     * @request POST:/processorTask/v1/find
     */
    find6: (data: ExpressionCriteria, params?: RequestParams) =>
      this.request<any, ResultPageProcessorTask>(`/processorTask/v1/find`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name FindSummary
     * @request POST:/processorTask/v1/summary
     */
    findSummary: (data: ExpressionCriteria, params?: RequestParams) =>
      this.request<any, ResultPageProcessorTaskSummary>(
        `/processorTask/v1/summary`,
        "POST",
        params,
        data,
        BodyType.Json,
      ),
  };
  refData = {
    /**
     * No description
     *
     * @name Entries
     * @request GET:/refData/v1/entries
     */
    entries: (query?: { limit?: number }, params?: RequestParams) =>
      this.request<any, RefStoreEntry[]>(`/refData/v1/entries${this.addQueryParams(query)}`, "GET", params),

    /**
     * No description
     *
     * @name Lookup
     * @request POST:/refData/v1/lookup
     */
    lookup: (data: RefDataLookupRequest, params?: RequestParams) =>
      this.request<any, string>(`/refData/v1/lookup`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Purge
     * @request DELETE:/refData/v1/purge/{purgeAge}
     */
    purge: (purgeAge: string, params?: RequestParams) =>
      this.request<any, any>(`/refData/v1/purge/${purgeAge}`, "DELETE", params),
  };
  remoteSearch = {
    /**
     * No description
     *
     * @name Destroy2
     * @request GET:/remoteSearch/v1/destroy
     */
    destroy2: (query?: { queryKey?: string }, params?: RequestParams) =>
      this.request<any, boolean>(`/remoteSearch/v1/destroy${this.addQueryParams(query)}`, "GET", params),

    /**
     * No description
     *
     * @name Poll2
     * @request GET:/remoteSearch/v1/poll
     */
    poll2: (query?: { queryKey?: string }, params?: RequestParams) =>
      this.request<any, any>(`/remoteSearch/v1/poll${this.addQueryParams(query)}`, "GET", params),

    /**
     * No description
     *
     * @name Start1
     * @request POST:/remoteSearch/v1/start
     */
    start1: (data: ClusterSearchTask, params?: RequestParams) =>
      this.request<any, boolean>(`/remoteSearch/v1/start`, "POST", params, data, BodyType.Json),
  };
  ruleset = {
    /**
     * No description
     *
     * @name ExportDocument3
     * @request POST:/ruleset/v2/export
     */
    exportDocument3: (data: DocRef, params?: RequestParams) =>
      this.request<any, Base64EncodedDocumentData>(`/ruleset/v2/export`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name ImportDocument3
     * @request POST:/ruleset/v2/import
     */
    importDocument3: (data: Base64EncodedDocumentData, params?: RequestParams) =>
      this.request<any, DocRef>(`/ruleset/v2/import`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name ListDocuments3
     * @request GET:/ruleset/v2/list
     */
    listDocuments3: (params?: RequestParams) => this.request<any, DocRef[]>(`/ruleset/v2/list`, "GET", params),

    /**
     * No description
     *
     * @name Read15
     * @request POST:/ruleset/v2/read
     */
    read15: (data: DocRef, params?: RequestParams) =>
      this.request<any, ReceiveDataRules>(`/ruleset/v2/read`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Update18
     * @request PUT:/ruleset/v2/update
     */
    update18: (data: ReceiveDataRules, params?: RequestParams) =>
      this.request<any, ReceiveDataRules>(`/ruleset/v2/update`, "PUT", params, data, BodyType.Json),
  };
  scheduledTime = {
    /**
     * No description
     *
     * @name Get4
     * @request POST:/scheduledTime/v1
     */
    get4: (data: GetScheduledTimesRequest, params?: RequestParams) =>
      this.request<any, ScheduledTimes>(`/scheduledTime/v1`, "POST", params, data, BodyType.Json),
  };
  script = {
    /**
     * No description
     *
     * @name FetchLinkedScripts
     * @request POST:/script/v1/fetchLinkedScripts
     */
    fetchLinkedScripts: (data: FetchLinkedScriptRequest, params?: RequestParams) =>
      this.request<any, ScriptDoc[]>(`/script/v1/fetchLinkedScripts`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Read16
     * @request POST:/script/v1/read
     */
    read16: (data: DocRef, params?: RequestParams) =>
      this.request<any, ScriptDoc>(`/script/v1/read`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Update19
     * @request PUT:/script/v1/update
     */
    update19: (data: ScriptDoc, params?: RequestParams) =>
      this.request<any, ScriptDoc>(`/script/v1/update`, "PUT", params, data, BodyType.Json),
  };
  searchable = {
    /**
     * No description
     *
     * @name GetDataSource2
     * @request POST:/searchable/v2/dataSource
     */
    getDataSource2: (data: DocRef, params?: RequestParams) =>
      this.request<any, DataSource>(`/searchable/v2/dataSource`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Destroy3
     * @request POST:/searchable/v2/destroy
     */
    destroy3: (data: QueryKey, params?: RequestParams) =>
      this.request<any, boolean>(`/searchable/v2/destroy`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Search6
     * @request POST:/searchable/v2/search
     */
    search6: (data: SearchRequest, params?: RequestParams) =>
      this.request<any, SearchResponse>(`/searchable/v2/search`, "POST", params, data, BodyType.Json),
  };
  session = {
    /**
     * No description
     *
     * @name List7
     * @request GET:/session/v1/list
     */
    list7: (query?: { nodeName?: string }, params?: RequestParams) =>
      this.request<any, SessionListResponse>(`/session/v1/list${this.addQueryParams(query)}`, "GET", params),

    /**
     * No description
     *
     * @name Logout1
     * @request GET:/session/v1/logout/{sessionId}
     */
    logout1: (sessionId: string, params?: RequestParams) =>
      this.request<any, any>(`/session/v1/logout/${sessionId}`, "GET", params),

    /**
     * No description
     *
     * @name Login1
     * @request GET:/session/v1/noauth/login
     */
    login1: (query?: { redirect_uri?: string }, params?: RequestParams) =>
      this.request<any, LoginResponse>(`/session/v1/noauth/login${this.addQueryParams(query)}`, "GET", params),
  };
  sessionInfo = {
    /**
     * No description
     *
     * @name Get3
     * @request GET:/sessionInfo/v1
     */
    get3: (params?: RequestParams) => this.request<any, SessionInfo>(`/sessionInfo/v1`, "GET", params),
  };
  solr = {
    /**
     * No description
     *
     * @name ExportDocument4
     * @request POST:/solr/index/v1/export
     */
    exportDocument4: (data: DocRef, params?: RequestParams) =>
      this.request<any, Base64EncodedDocumentData>(`/solr/index/v1/export`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name ImportDocument4
     * @request POST:/solr/index/v1/import
     */
    importDocument4: (data: Base64EncodedDocumentData, params?: RequestParams) =>
      this.request<any, DocRef>(`/solr/index/v1/import`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name ListDocuments4
     * @request GET:/solr/index/v1/list
     */
    listDocuments4: (params?: RequestParams) => this.request<any, DocRef[]>(`/solr/index/v1/list`, "GET", params),
  };
  solrIndex = {
    /**
     * No description
     *
     * @name FetchSolrTypes
     * @request POST:/solrIndex/v1/fetchSolrTypes
     */
    fetchSolrTypes: (data: SolrIndexDoc, params?: RequestParams) =>
      this.request<any, string[]>(`/solrIndex/v1/fetchSolrTypes`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Read17
     * @request POST:/solrIndex/v1/read
     */
    read17: (data: DocRef, params?: RequestParams) =>
      this.request<any, SolrIndexDoc>(`/solrIndex/v1/read`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name SolrConnectionTest
     * @request POST:/solrIndex/v1/solrConnectionTest
     */
    solrConnectionTest: (data: SolrIndexDoc, params?: RequestParams) =>
      this.request<any, SolrConnectionTestResponse>(
        `/solrIndex/v1/solrConnectionTest`,
        "POST",
        params,
        data,
        BodyType.Json,
      ),

    /**
     * No description
     *
     * @name Update20
     * @request PUT:/solrIndex/v1/update
     */
    update20: (data: SolrIndexDoc, params?: RequestParams) =>
      this.request<any, SolrIndexDoc>(`/solrIndex/v1/update`, "PUT", params, data, BodyType.Json),
  };
  sqlstatistics = {
    /**
     * No description
     *
     * @name GetDataSource3
     * @request POST:/sqlstatistics/v2/dataSource
     */
    getDataSource3: (data: DocRef, params?: RequestParams) =>
      this.request<any, DataSource>(`/sqlstatistics/v2/dataSource`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Destroy4
     * @request POST:/sqlstatistics/v2/destroy
     */
    destroy4: (data: QueryKey, params?: RequestParams) =>
      this.request<any, boolean>(`/sqlstatistics/v2/destroy`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Search9
     * @request POST:/sqlstatistics/v2/search
     */
    search9: (data: SearchRequest, params?: RequestParams) =>
      this.request<any, SearchResponse>(`/sqlstatistics/v2/search`, "POST", params, data, BodyType.Json),
  };
  statistic = {
    /**
     * No description
     *
     * @name BitMaskConversion1
     * @request POST:/statistic/rollUp/v1/bitMaskConversion
     */
    bitMaskConversion1: (data: number[], params?: RequestParams) =>
      this.request<any, CustomRollUpMaskFields[]>(
        `/statistic/rollUp/v1/bitMaskConversion`,
        "POST",
        params,
        data,
        BodyType.Json,
      ),

    /**
     * No description
     *
     * @name BitMaskPermGeneration1
     * @request POST:/statistic/rollUp/v1/bitMaskPermGeneration
     */
    bitMaskPermGeneration1: (data: number, params?: RequestParams) =>
      this.request<any, CustomRollUpMask[]>(
        `/statistic/rollUp/v1/bitMaskPermGeneration`,
        "POST",
        params,
        data,
        BodyType.Json,
      ),

    /**
     * No description
     *
     * @name FieldChange1
     * @request POST:/statistic/rollUp/v1/dataSourceFieldChange
     */
    fieldChange1: (data: StatisticsDataSourceFieldChangeRequest, params?: RequestParams) =>
      this.request<any, StatisticsDataSourceData>(
        `/statistic/rollUp/v1/dataSourceFieldChange`,
        "POST",
        params,
        data,
        BodyType.Json,
      ),

    /**
     * No description
     *
     * @name Read22
     * @request POST:/statistic/v1/read
     */
    read22: (data: DocRef, params?: RequestParams) =>
      this.request<any, StatisticStoreDoc>(`/statistic/v1/read`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Update23
     * @request PUT:/statistic/v1/update
     */
    update23: (data: StatisticStoreDoc, params?: RequestParams) =>
      this.request<any, StatisticStoreDoc>(`/statistic/v1/update`, "PUT", params, data, BodyType.Json),
  };
  statsStore = {
    /**
     * No description
     *
     * @name BitMaskConversion
     * @request POST:/statsStore/rollUp/v1/bitMaskConversion
     */
    bitMaskConversion: (data: number[], params?: RequestParams) =>
      this.request<any, ResultPageCustomRollUpMaskFields>(
        `/statsStore/rollUp/v1/bitMaskConversion`,
        "POST",
        params,
        data,
        BodyType.Json,
      ),

    /**
     * No description
     *
     * @name BitMaskPermGeneration
     * @request POST:/statsStore/rollUp/v1/bitMaskPermGeneration
     */
    bitMaskPermGeneration: (data: number, params?: RequestParams) =>
      this.request<any, ResultPageCustomRollUpMask>(
        `/statsStore/rollUp/v1/bitMaskPermGeneration`,
        "POST",
        params,
        data,
        BodyType.Json,
      ),

    /**
     * No description
     *
     * @name FieldChange
     * @request POST:/statsStore/rollUp/v1/dataSourceFieldChange
     */
    fieldChange: (data: StroomStatsStoreFieldChangeRequest, params?: RequestParams) =>
      this.request<any, StroomStatsStoreEntityData>(
        `/statsStore/rollUp/v1/dataSourceFieldChange`,
        "POST",
        params,
        data,
        BodyType.Json,
      ),

    /**
     * No description
     *
     * @name Read21
     * @request POST:/statsStore/v1/read
     */
    read21: (data: DocRef, params?: RequestParams) =>
      this.request<any, StroomStatsStoreDoc>(`/statsStore/v1/read`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Update22
     * @request PUT:/statsStore/v1/update
     */
    update22: (data: StroomStatsStoreDoc, params?: RequestParams) =>
      this.request<any, StroomStatsStoreDoc>(`/statsStore/v1/update`, "PUT", params, data, BodyType.Json),
  };
  stepping = {
    /**
     * No description
     *
     * @name FindElementDoc
     * @request POST:/stepping/v1/findElementDoc
     */
    findElementDoc: (data: FindElementDocRequest, params?: RequestParams) =>
      this.request<any, DocRef>(`/stepping/v1/findElementDoc`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name GetPipelineForStepping
     * @request POST:/stepping/v1/getPipelineForStepping
     */
    getPipelineForStepping: (data: GetPipelineForMetaRequest, params?: RequestParams) =>
      this.request<any, DocRef>(`/stepping/v1/getPipelineForStepping`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Step
     * @request POST:/stepping/v1/step
     */
    step: (data: PipelineStepRequest, params?: RequestParams) =>
      this.request<any, SteppingResult>(`/stepping/v1/step`, "POST", params, data, BodyType.Json),
  };
  storedQuery = {
    /**
     * No description
     *
     * @name Create2
     * @request POST:/storedQuery/v1/create
     */
    create2: (data: StoredQuery, params?: RequestParams) =>
      this.request<any, StoredQuery>(`/storedQuery/v1/create`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Delete1
     * @request DELETE:/storedQuery/v1/delete
     */
    delete1: (data: StoredQuery, params?: RequestParams) =>
      this.request<any, boolean>(`/storedQuery/v1/delete`, "DELETE", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Find
     * @request POST:/storedQuery/v1/find
     */
    find: (data: FindStoredQueryCriteria, params?: RequestParams) =>
      this.request<any, ResultPageStoredQuery>(`/storedQuery/v1/find`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Read2
     * @request POST:/storedQuery/v1/read
     */
    read2: (data: StoredQuery, params?: RequestParams) =>
      this.request<any, StoredQuery>(`/storedQuery/v1/read`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Update3
     * @request PUT:/storedQuery/v1/update
     */
    update3: (data: StoredQuery, params?: RequestParams) =>
      this.request<any, StoredQuery>(`/storedQuery/v1/update`, "PUT", params, data, BodyType.Json),
  };
  streamattributemap = {
    /**
     * No description
     *
     * @name Page
     * @request GET:/streamattributemap/v1
     */
    page: (query?: { pageOffset?: number; pageSize?: number }, params?: RequestParams) =>
      this.request<any, any>(`/streamattributemap/v1${this.addQueryParams(query)}`, "GET", params),

    /**
     * No description
     *
     * @name Search3
     * @request POST:/streamattributemap/v1
     */
    search3: (data: ExpressionOperator, query?: { pageOffset?: number; pageSize?: number }, params?: RequestParams) =>
      this.request<any, any>(
        `/streamattributemap/v1${this.addQueryParams(query)}`,
        "POST",
        params,
        data,
        BodyType.Json,
      ),

    /**
     * No description
     *
     * @name DataSource
     * @request GET:/streamattributemap/v1/dataSource
     */
    dataSource: (params?: RequestParams) => this.request<any, any>(`/streamattributemap/v1/dataSource`, "GET", params),

    /**
     * No description
     *
     * @name Search2
     * @request GET:/streamattributemap/v1/{id}
     */
    search2: (id: number, params?: RequestParams) =>
      this.request<any, any>(`/streamattributemap/v1/${id}`, "GET", params),

    /**
     * No description
     *
     * @name GetRelations
     * @request GET:/streamattributemap/v1/{id}/{anyStatus}/relations
     */
    getRelations: (id: number, anyStatus: boolean, params?: RequestParams) =>
      this.request<any, any>(`/streamattributemap/v1/${id}/${anyStatus}/relations`, "GET", params),
  };
  streamtasks = {
    /**
     * No description
     *
     * @name Fetch6
     * @request GET:/streamtasks/v1
     */
    fetch6: (
      query?: { offset?: number; pageSize?: number; sortBy?: string; desc?: boolean; filter?: string },
      params?: RequestParams,
    ) => this.request<any, any>(`/streamtasks/v1${this.addQueryParams(query)}`, "GET", params),

    /**
     * No description
     *
     * @name Enable
     * @request PATCH:/streamtasks/v1/{filterId}
     */
    enable: (filterId: number, data: StreamTaskPatch, params?: RequestParams) =>
      this.request<any, any>(`/streamtasks/v1/${filterId}`, "PATCH", params, data, BodyType.Json),
  };
  stroomIndex = {
    /**
     * No description
     *
     * @name GetDataSource
     * @request POST:/stroom-index/v2/dataSource
     */
    getDataSource: (data: DocRef, params?: RequestParams) =>
      this.request<any, DataSource>(`/stroom-index/v2/dataSource`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Destroy
     * @request POST:/stroom-index/v2/destroy
     */
    destroy: (data: QueryKey, params?: RequestParams) =>
      this.request<any, boolean>(`/stroom-index/v2/destroy`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Search1
     * @request POST:/stroom-index/v2/search
     */
    search1: (data: SearchRequest, params?: RequestParams) =>
      this.request<any, SearchResponse>(`/stroom-index/v2/search`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name GetAll2
     * @request GET:/stroom-index/volume/v1
     */
    getAll2: (params?: RequestParams) => this.request<any, any>(`/stroom-index/volume/v1`, "GET", params),

    /**
     * No description
     *
     * @name Create6
     * @request POST:/stroom-index/volume/v1
     */
    create6: (data: IndexVolume, params?: RequestParams) =>
      this.request<any, any>(`/stroom-index/volume/v1`, "POST", params, data),

    /**
     * No description
     *
     * @name Update9
     * @request PUT:/stroom-index/volume/v1
     */
    update9: (data: IndexVolume, params?: RequestParams) =>
      this.request<any, any>(`/stroom-index/volume/v1`, "PUT", params, data),

    /**
     * No description
     *
     * @name Delete5
     * @request DELETE:/stroom-index/volume/v1/{id}
     */
    delete5: (id: number, params?: RequestParams) =>
      this.request<any, any>(`/stroom-index/volume/v1/${id}`, "DELETE", params),

    /**
     * No description
     *
     * @name GetById
     * @request GET:/stroom-index/volume/v1/{id}
     */
    getById: (id: number, params?: RequestParams) =>
      this.request<any, any>(`/stroom-index/volume/v1/${id}`, "GET", params),

    /**
     * No description
     *
     * @name GetAll1
     * @request GET:/stroom-index/volumeGroup/v1
     */
    getAll1: (params?: RequestParams) => this.request<any, any>(`/stroom-index/volumeGroup/v1`, "GET", params),

    /**
     * No description
     *
     * @name Create5
     * @request POST:/stroom-index/volumeGroup/v1
     */
    create5: (params?: RequestParams) => this.request<any, any>(`/stroom-index/volumeGroup/v1`, "POST", params),

    /**
     * No description
     *
     * @name Update8
     * @request PUT:/stroom-index/volumeGroup/v1
     */
    update8: (data: IndexVolumeGroup, params?: RequestParams) =>
      this.request<any, any>(`/stroom-index/volumeGroup/v1`, "PUT", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name GetNames1
     * @request GET:/stroom-index/volumeGroup/v1/names
     */
    getNames1: (params?: RequestParams) => this.request<any, any>(`/stroom-index/volumeGroup/v1/names`, "GET", params),

    /**
     * No description
     *
     * @name Delete4
     * @request DELETE:/stroom-index/volumeGroup/v1/{id}
     */
    delete4: (id: string, params?: RequestParams) =>
      this.request<any, any>(`/stroom-index/volumeGroup/v1/${id}`, "DELETE", params),

    /**
     * No description
     *
     * @name Get1
     * @request GET:/stroom-index/volumeGroup/v1/{id}
     */
    get1: (id: string, params?: RequestParams) =>
      this.request<any, any>(`/stroom-index/volumeGroup/v1/${id}`, "GET", params),
  };
  stroomSolrIndex = {
    /**
     * No description
     *
     * @name GetDataSource1
     * @request POST:/stroom-solr-index/v2/dataSource
     */
    getDataSource1: (data: DocRef, params?: RequestParams) =>
      this.request<any, DataSource>(`/stroom-solr-index/v2/dataSource`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Destroy1
     * @request POST:/stroom-solr-index/v2/destroy
     */
    destroy1: (data: QueryKey, params?: RequestParams) =>
      this.request<any, boolean>(`/stroom-solr-index/v2/destroy`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Search5
     * @request POST:/stroom-solr-index/v2/search
     */
    search5: (data: SearchRequest, params?: RequestParams) =>
      this.request<any, SearchResponse>(`/stroom-solr-index/v2/search`, "POST", params, data, BodyType.Json),
  };
  stroomSession = {
    /**
     * No description
     *
     * @name Invalidate1
     * @request GET:/stroomSession/v1/invalidate
     */
    invalidate1: (params?: RequestParams) => this.request<any, boolean>(`/stroomSession/v1/invalidate`, "GET", params),

    /**
     * No description
     *
     * @name ValidateSession
     * @request GET:/stroomSession/v1/noauth/validateSession
     */
    validateSession: (query: { redirect_uri: string }, params?: RequestParams) =>
      this.request<any, ValidateSessionResponse>(
        `/stroomSession/v1/noauth/validateSession${this.addQueryParams(query)}`,
        "GET",
        params,
      ),
  };
  suggest = {
    /**
     * No description
     *
     * @name Fetch7
     * @request POST:/suggest/v1
     */
    fetch7: (data: FetchSuggestionsRequest, params?: RequestParams) =>
      this.request<any, string[]>(`/suggest/v1`, "POST", params, data, BodyType.Json),
  };
  systemInfo = {
    /**
     * No description
     *
     * @name GetAll
     * @request GET:/systemInfo/v1
     */
    getAll: (params?: RequestParams) => this.request<any, SystemInfoResultList>(`/systemInfo/v1`, "GET", params),

    /**
     * No description
     *
     * @name GetNames
     * @request GET:/systemInfo/v1/names
     */
    getNames: (params?: RequestParams) => this.request<any, string[]>(`/systemInfo/v1/names`, "GET", params),

    /**
     * No description
     *
     * @name Get2
     * @request GET:/systemInfo/v1/{name}
     */
    get2: (name: string, params?: RequestParams) =>
      this.request<any, SystemInfoResult>(`/systemInfo/v1/${name}`, "GET", params),
  };
  task = {
    /**
     * No description
     *
     * @name Find8
     * @request POST:/task/v1/find/{nodeName}
     */
    find8: (nodeName: string, data: FindTaskProgressRequest, params?: RequestParams) =>
      this.request<any, TaskProgressResponse>(`/task/v1/find/${nodeName}`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name List8
     * @request GET:/task/v1/list/{nodeName}
     */
    list8: (nodeName: string, params?: RequestParams) =>
      this.request<any, TaskProgressResponse>(`/task/v1/list/${nodeName}`, "GET", params),

    /**
     * No description
     *
     * @name Terminate
     * @request POST:/task/v1/terminate/{nodeName}
     */
    terminate: (nodeName: string, data: TerminateTaskProgressRequest, params?: RequestParams) =>
      this.request<any, boolean>(`/task/v1/terminate/${nodeName}`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name UserTasks
     * @request GET:/task/v1/user/{nodeName}
     */
    userTasks: (nodeName: string, params?: RequestParams) =>
      this.request<any, TaskProgressResponse>(`/task/v1/user/${nodeName}`, "GET", params),
  };
  textConverter = {
    /**
     * No description
     *
     * @name Read12
     * @request POST:/textConverter/v1/read
     */
    read12: (data: DocRef, params?: RequestParams) =>
      this.request<any, TextConverterDoc>(`/textConverter/v1/read`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Update15
     * @request PUT:/textConverter/v1/update
     */
    update15: (data: TextConverterDoc, params?: RequestParams) =>
      this.request<any, TextConverterDoc>(`/textConverter/v1/update`, "PUT", params, data, BodyType.Json),
  };
  token = {
    /**
     * No description
     *
     * @name DeleteAll
     * @request DELETE:/token/v1
     */
    deleteAll: (params?: RequestParams) => this.request<any, number>(`/token/v1`, "DELETE", params),

    /**
     * No description
     *
     * @name List6
     * @request GET:/token/v1
     */
    list6: (params?: RequestParams) => this.request<any, TokenResultPage>(`/token/v1`, "GET", params),

    /**
     * No description
     *
     * @name Create11
     * @request POST:/token/v1
     */
    create11: (data: CreateTokenRequest, params?: RequestParams) =>
      this.request<any, Token>(`/token/v1`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name DeleteByToken
     * @request DELETE:/token/v1/byToken/{token}
     */
    deleteByToken: (token: string, params?: RequestParams) =>
      this.request<any, number>(`/token/v1/byToken/${token}`, "DELETE", params),

    /**
     * No description
     *
     * @name Read20
     * @request GET:/token/v1/byToken/{token}
     */
    read20: (token: string, params?: RequestParams) =>
      this.request<any, Token>(`/token/v1/byToken/${token}`, "GET", params),

    /**
     * No description
     *
     * @name FetchTokenConfig
     * @request GET:/token/v1/noauth/fetchTokenConfig
     */
    fetchTokenConfig: (params?: RequestParams) =>
      this.request<any, TokenConfig>(`/token/v1/noauth/fetchTokenConfig`, "GET", params),

    /**
     * No description
     *
     * @name GetPublicKey
     * @request GET:/token/v1/publickey
     */
    getPublicKey: (params?: RequestParams) => this.request<any, string>(`/token/v1/publickey`, "GET", params),

    /**
     * No description
     *
     * @name Search8
     * @request POST:/token/v1/search
     */
    search8: (data: SearchTokenRequest, params?: RequestParams) =>
      this.request<any, TokenResultPage>(`/token/v1/search`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Delete11
     * @request DELETE:/token/v1/{id}
     */
    delete11: (id: number, params?: RequestParams) => this.request<any, number>(`/token/v1/${id}`, "DELETE", params),

    /**
     * No description
     *
     * @name Read19
     * @request GET:/token/v1/{id}
     */
    read19: (id: number, params?: RequestParams) => this.request<any, Token>(`/token/v1/${id}`, "GET", params),

    /**
     * No description
     *
     * @name ToggleEnabled
     * @request GET:/token/v1/{id}/enabled
     */
    toggleEnabled: (id: number, query: { enabled: boolean }, params?: RequestParams) =>
      this.request<any, number>(`/token/v1/${id}/enabled${this.addQueryParams(query)}`, "GET", params),
  };
  users = {
    /**
     * No description
     *
     * @name Get6
     * @request GET:/users/v1
     */
    get6: (query?: { name?: string; isGroup?: boolean; uuid?: string }, params?: RequestParams) =>
      this.request<any, User[]>(`/users/v1${this.addQueryParams(query)}`, "GET", params),

    /**
     * No description
     *
     * @name GetAssociates
     * @request GET:/users/v1/associates
     */
    getAssociates: (query?: { filter?: string }, params?: RequestParams) =>
      this.request<any, string[]>(`/users/v1/associates${this.addQueryParams(query)}`, "GET", params),

    /**
     * No description
     *
     * @name Create12
     * @request POST:/users/v1/create/{name}/{isGroup}
     */
    create12: (name: string, isGroup: boolean, params?: RequestParams) =>
      this.request<any, User>(`/users/v1/create/${name}/${isGroup}`, "POST", params),

    /**
     * No description
     *
     * @name Find7
     * @request POST:/users/v1/find
     */
    find7: (data: FindUserCriteria, params?: RequestParams) =>
      this.request<any, ResultPageUser>(`/users/v1/find`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name SetStatus1
     * @request PUT:/users/v1/{userName}/status
     */
    setStatus1: (userName: string, query?: { enabled?: boolean }, params?: RequestParams) =>
      this.request<any, boolean>(`/users/v1/${userName}/status${this.addQueryParams(query)}`, "PUT", params),

    /**
     * No description
     *
     * @name Get5
     * @request GET:/users/v1/{userUuid}
     */
    get5: (userUuid: string, params?: RequestParams) => this.request<any, User>(`/users/v1/${userUuid}`, "GET", params),

    /**
     * No description
     *
     * @name RemoveUserFromGroup
     * @request DELETE:/users/v1/{userUuid}/{groupUuid}
     */
    removeUserFromGroup: (userUuid: string, groupUuid: string, params?: RequestParams) =>
      this.request<any, boolean>(`/users/v1/${userUuid}/${groupUuid}`, "DELETE", params),

    /**
     * No description
     *
     * @name AddUserToGroup
     * @request PUT:/users/v1/{userUuid}/{groupUuid}
     */
    addUserToGroup: (userUuid: string, groupUuid: string, params?: RequestParams) =>
      this.request<any, boolean>(`/users/v1/${userUuid}/${groupUuid}`, "PUT", params),

    /**
     * No description
     *
     * @name DeleteUser
     * @request DELETE:/users/v1/{uuid}
     */
    deleteUser: (uuid: string, params?: RequestParams) =>
      this.request<any, boolean>(`/users/v1/${uuid}`, "DELETE", params),
  };
  viewData = {
    /**
     * No description
     *
     * @name Fetch4
     * @request POST:/viewData/v1/fetch
     */
    fetch4: (data: FetchDataRequest, params?: RequestParams) =>
      this.request<any, AbstractFetchDataResult>(`/viewData/v1/fetch`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name GetChildStreamTypes
     * @request GET:/viewData/v1/listChildTypes
     */
    getChildStreamTypes: (query?: { id?: number; partNo?: number }, params?: RequestParams) =>
      this.request<any, string[]>(`/viewData/v1/listChildTypes${this.addQueryParams(query)}`, "GET", params),
  };
  visualisation = {
    /**
     * No description
     *
     * @name Read23
     * @request POST:/visualisation/v1/read
     */
    read23: (data: DocRef, params?: RequestParams) =>
      this.request<any, VisualisationDoc>(`/visualisation/v1/read`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Update24
     * @request PUT:/visualisation/v1/update
     */
    update24: (data: VisualisationDoc, params?: RequestParams) =>
      this.request<any, VisualisationDoc>(`/visualisation/v1/update`, "PUT", params, data, BodyType.Json),
  };
  welcome = {
    /**
     * No description
     *
     * @name Welcome
     * @request GET:/welcome/v1
     */
    welcome: (params?: RequestParams) => this.request<any, any>(`/welcome/v1`, "GET", params),
  };
  xmlSchema = {
    /**
     * No description
     *
     * @name Read24
     * @request POST:/xmlSchema/v1/read
     */
    read24: (data: DocRef, params?: RequestParams) =>
      this.request<any, XmlSchemaDoc>(`/xmlSchema/v1/read`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Update25
     * @request PUT:/xmlSchema/v1/update
     */
    update25: (data: XmlSchemaDoc, params?: RequestParams) =>
      this.request<any, XmlSchemaDoc>(`/xmlSchema/v1/update`, "PUT", params, data, BodyType.Json),
  };
  xslt = {
    /**
     * No description
     *
     * @name Read13
     * @request POST:/xslt/v1/read
     */
    read13: (data: DocRef, params?: RequestParams) =>
      this.request<any, XsltDoc>(`/xslt/v1/read`, "POST", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Update16
     * @request PUT:/xslt/v1/update
     */
    update16: (data: XsltDoc, params?: RequestParams) =>
      this.request<any, XsltDoc>(`/xslt/v1/update`, "PUT", params, data, BodyType.Json),

    /**
     * No description
     *
     * @name Fetch5
     * @request GET:/xslt/v1/{xsltId}
     */
    fetch5: (xsltId: string, params?: RequestParams) => this.request<any, XsltDoc>(`/xslt/v1/${xsltId}`, "GET", params),

    /**
     * No description
     *
     * @name Save3
     * @request POST:/xslt/v1/{xsltId}
     */
    save3: (xsltId: string, data: XsltDTO, params?: RequestParams) =>
      this.request<any, any>(`/xslt/v1/${xsltId}`, "POST", params, data, BodyType.Json),
  };
}
