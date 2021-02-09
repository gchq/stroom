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
  itemRange?: OffsetRangeLong;
  sourceLocation?: SourceLocation;
  streamTypeName?: string;
  totalCharacterCount?: CountLong;
  totalItemCount?: CountLong;
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
  /** Set to true if users should be prompted to choose an activity on login. */
  chooseOnStartup?: boolean;

  /** The HTML to display in the activity editor popup. */
  editorBody?: string;

  /** The title of the activity editor popup. */
  editorTitle?: string;

  /** If you would like users to be able to record some info about the activity they are performing set this property to true. */
  enabled?: boolean;

  /** The title of the activity manager popup. */
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

  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
  docRef?: DocRef;
}

export type BooleanField = AbstractField & object;

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

export interface ChangeDocumentPermissionsRequest {
  cascade?: "NO" | "CHANGES_ONLY" | "ALL";
  changes?: Changes;

  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
  docRef?: DocRef;
}

export interface ChangePasswordRequest {
  confirmNewPassword?: string;
  currentPassword?: string;
  newPassword?: string;
  userId?: string;
}

export interface ChangeSet {
  addSet?: object[];
  removeSet?: object[];
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

export interface ComponentConfig {
  id?: string;
  name?: string;
  settings?: ComponentSettings;
  type?: string;
}

export interface ComponentResultRequest {
  /** The ID of the component that will receive the results corresponding to this ResultRequest */
  componentId: string;
  fetch?: "NONE" | "CHANGES" | "ALL";
}

export type ComponentSettings = object;

export interface ConditionalFormattingRule {
  backgroundColor?: string;
  enabled?: boolean;

  /** A logical addOperator term in a query expression tree */
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

export interface CopyOp {
  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
  destinationFolderRef?: DocRef;
  docRefs?: DocRef[];
  permissionInheritance?: "NONE" | "SOURCE" | "DESTINATION" | "COMBINED";
}

export interface CopyPermissionsFromParentRequest {
  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
  docRef?: DocRef;
}

export interface Count {
  count?: Number;
  exact?: boolean;
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
  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
  destinationFolderRef?: DocRef;
  docRefName?: string;
  docRefType?: string;
  permissionInheritance?: "NONE" | "SOURCE" | "DESTINATION" | "COMBINED";
}

export interface CreateProcessFilterRequest {
  autoPriority?: boolean;
  enabled?: boolean;

  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
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

export interface CustomRollUpMask {
  rolledUpTagPosition?: number[];
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

export interface DataRetentionRule {
  /** @format int32 */
  age?: number;

  /** @format int64 */
  creationTime?: number;
  enabled?: boolean;

  /** A logical addOperator term in a query expression tree */
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

export type DateField = AbstractField & object;

export type DateTimeFormatSettings = FormatSettings & { pattern: string; timeZone: TimeZone };

export type DefaultLocation = Location & { colNo?: number; lineNo?: number };

export interface DependencyCriteria {
  pageRequest?: PageRequest;
  partialName?: string;
  sortList?: Sort[];
}

/**
 * A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline.
 */
export interface DictionaryDTO {
  data?: string;
  description?: string;
  imports?: DocRef[];

  /**
   * The name for the data source
   * @example MyStatistic
   */
  name: string;

  /**
   * The type of the 'document' that this DocRef refers to
   * @example StroomStatsStore
   */
  type: string;

  /**
   * The unique identifier for this 'document'
   * @example 9f6184b4-bd78-48bc-b0cd-6e51a357f6a6
   */
  uuid: string;
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

export interface Doc {
  /** @format int64 */
  createTime?: number;

  /** @format int64 */
  createTimeMs?: number;
  createUser?: string;
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

/**
 * A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline.
 */
export interface DocRef {
  /**
   * The name for the data source
   * @example MyStatistic
   */
  name: string;

  /**
   * The type of the 'document' that this DocRef refers to
   * @example StroomStatsStore
   */
  type: string;

  /**
   * The unique identifier for this 'document'
   * @example 9f6184b4-bd78-48bc-b0cd-6e51a357f6a6
   */
  uuid: string;
}

export type DocRefField = AbstractField & { docRefType?: string };

export interface DocRefInfo {
  /** @format int64 */
  createTime?: number;
  createUser?: string;

  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
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

export type DoubleField = AbstractField & object;

export interface DownloadQueryRequest {
  dashboardQueryKey?: DashboardQueryKey;

  /** A request for new search or a follow up request for more data for an existing iterative search */
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

  /** A request for new search or a follow up request for more data for an existing iterative search */
  searchRequest?: SearchRequest;
}

export interface EntityEvent {
  action?: "CREATE" | "UPDATE" | "DELETE" | "CLEAR_CACHE";

  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
  docRef?: DocRef;
}

export interface Entry {
  key?: string;
  value?: string;
}

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

export interface ExplorerServiceCopyRequest {
  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
  destinationFolderRef?: DocRef;
  docRefs?: DocRef[];
  permissionInheritance?: "NONE" | "SOURCE" | "DESTINATION" | "COMBINED";
}

export interface ExplorerServiceCreateRequest {
  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
  destinationFolderRef?: DocRef;
  docName?: string;
  docType?: string;
  permissionInheritance?: "NONE" | "SOURCE" | "DESTINATION" | "COMBINED";
}

export interface ExplorerServiceDeleteRequest {
  docRefs?: DocRef[];
}

export interface ExplorerServiceMoveRequest {
  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
  destinationFolderRef?: DocRef;
  docRefs?: DocRef[];
  permissionInheritance?: "NONE" | "SOURCE" | "DESTINATION" | "COMBINED";
}

export interface ExplorerServiceRenameRequest {
  docName?: string;

  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
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
  /** A logical addOperator term in a query expression tree */
  expression?: ExpressionOperator;
  pageRequest?: PageRequest;
  sortList?: Sort[];
}

/**
 * Base type for an item in an expression tree
 */
export interface ExpressionItem {
  /**
   * Whether this item in the expression tree is enabled or not
   * @example true
   */
  enabled?: boolean;
}

/**
 * A logical addOperator term in a query expression tree
 */
export interface ExpressionOperator {
  children?: ExpressionItem[];

  /**
   * Whether this item in the expression tree is enabled or not
   * @example true
   */
  enabled?: boolean;

  /** The logical addOperator type */
  op: "AND" | "OR" | "NOT";
}

export type ExpressionTerm = ExpressionItem & {
  condition:
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
  field: string;
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
  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
  docRef?: DocRef;
}

export interface FetchDataRequest {
  expandedSeverities?: ("INFO" | "WARNING" | "ERROR" | "FATAL_ERROR")[];
  markerMode?: boolean;

  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
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

  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
  script?: DocRef;
}

export type FetchMarkerResult = AbstractFetchDataResult & { markers?: Marker[] };

export interface FetchNodeStatusResponse {
  pageResponse?: PageResponse;
  values?: NodeStatusResult[];
}

export interface FetchPipelineXmlResponse {
  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
  pipeline?: DocRef;
  xml?: string;
}

export interface FetchProcessorRequest {
  expandedRows?: ProcessorListRow[];

  /** A logical addOperator term in a query expression tree */
  expression?: ExpressionOperator;
}

export interface FetchSuggestionsRequest {
  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
  dataSource: DocRef;
  field: AbstractField;
  text?: string;
}

/**
 * Describes a field in a result set. The field can have various expressions applied to it, e.g. SUM(), along with sorting, filtering, formatting and grouping
 */
export interface Field {
  /**
   * The expression to use to generate the value for this field
   * @example SUM(${count})
   */
  expression: string;

  /** A pair of regular expression filters (inclusion and exclusion) to apply to the field.  Either or both can be supplied */
  filter?: Filter;

  /** Describes the formatting that will be applied to values in a field */
  format?: Format;

  /**
   * If this field is to be grouped then this defines the level of grouping, with 0 being the top level of grouping, 1 being the next level down, etc.
   * @format int32
   */
  group?: number;

  /** The internal id of the field for equality purposes */
  id?: string;

  /** The name of the field for display purposes */
  name?: string;
  sort?: Sort;
  special?: boolean;
  visible?: boolean;

  /** @format int32 */
  width?: number;
}

/**
 * A pair of regular expression filters (inclusion and exclusion) to apply to the field.  Either or both can be supplied
 */
export interface Filter {
  /**
   * Only results NOT matching this filter will be included
   * @example ^[0-9]{3}$
   */
  excludes?: string;

  /**
   * Only results matching this filter will be included
   * @example ^[0-9]{3}$
   */
  includes?: string;
}

export interface FindDBTableCriteria {
  pageRequest?: PageRequest;
  sortList?: Sort[];
}

export interface FindDataRetentionImpactCriteria {
  /** A logical addOperator term in a query expression tree */
  expression?: ExpressionOperator;
  pageRequest?: PageRequest;
  sortList?: Sort[];
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
  sortList?: Sort[];
}

export interface FindIndexShardCriteria {
  documentCountRange?: RangeInteger;
  indexShardIdSet?: SelectionLong;
  indexShardStatusSet?: SelectionIndexShardStatus;
  indexUuidSet?: SelectionString;
  nodeNameSet?: SelectionString;
  pageRequest?: PageRequest;
  partition?: StringCriteria;
  sortList?: Sort[];
  volumeIdSet?: SelectionInteger;
}

export interface FindMetaCriteria {
  /** A logical addOperator term in a query expression tree */
  expression?: ExpressionOperator;
  fetchRelationships?: boolean;
  pageRequest?: PageRequest;
  sortList?: Sort[];
}

export interface FindStoredQueryCriteria {
  componentId?: string;
  dashboardUuid?: string;
  favourite?: boolean;
  name?: StringCriteria;
  pageRequest?: PageRequest;
  requiredPermission?: string;
  sortList?: Sort[];
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
  sortList?: Sort[];
}

export interface FindTaskProgressRequest {
  criteria?: FindTaskProgressCriteria;
}

export interface FindUserCriteria {
  group?: boolean;
  pageRequest?: PageRequest;
  quickFilterInput?: string;
  relatedUser?: User;
  sortList?: Sort[];
}

export type FlatResult = Result & { size?: number; structure?: Field[]; values?: object[][] };

export type FloatField = AbstractField & object;

/**
 * Describes the formatting that will be applied to values in a field
 */
export interface Format {
  settings?: FormatSettings;

  /**
   * The formatting type to apply
   * @example NUMBER
   */
  type: "GENERAL" | "NUMBER" | "DATE_TIME" | "TEXT";
  wrap?: boolean;
}

export interface FormatSettings {
  default?: boolean;
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

export interface GetFeedStatusRequest {
  feedName?: string;
  senderDn?: string;
}

export interface GetFeedStatusResponse {
  message?: string;
  status?: "Receive" | "Reject" | "Drop";
  stroomStatusCode?:
    | "OK"
    | "FEED_MUST_BE_SPECIFIED"
    | "FEED_IS_NOT_DEFINED"
    | "FEED_IS_NOT_SET_TO_RECEIVED_DATA"
    | "UNEXPECTED_DATA_TYPE"
    | "UNKNOWN_COMPRESSION"
    | "CLIENT_CERTIFICATE_REQUIRED"
    | "CLIENT_CERTIFICATE_NOT_AUTHORISED"
    | "COMPRESSED_STREAM_INVALID"
    | "UNKNOWN_ERROR";
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
  sortList?: Sort[];
}

export type IdField = AbstractField & object;

export interface ImportConfigRequest {
  confirmList?: ImportState[];
  resourceKey?: ResourceKey;
}

export interface ImportState {
  action?: boolean;
  destPath?: string;

  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
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
  /** If you would like users to provide some query info when performing a query set this property to true. */
  enabled?: boolean;

  /** The title of the query info popup. */
  title?: string;

  /** A regex used to validate query info. */
  validationRegex?: string;
}

export type IntegerField = AbstractField & object;

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
}

export interface LoginRequest {
  password?: string;
  userId?: string;
}

export type LongField = AbstractField & object;

export interface MapDefinition {
  mapName?: string;
  refStreamDefinition?: RefStreamDefinition;
}

export interface Marker {
  severity?: "INFO" | "WARNING" | "ERROR" | "FATAL_ERROR";
}

export interface Message {
  message?: string;
  severity?: "INFO" | "WARNING" | "ERROR" | "FATAL_ERROR";
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

export type Number = object;

export type NumberFormatSettings = FormatSettings & { decimalPlaces: number; useSeparator?: boolean };

/**
 * The offset and length of a range of data in a sub-set of a query result set
 */
export interface OffsetRange {
  /**
   * The length in records of the sub-set of results
   * @format int64
   * @example 100
   */
  length: number;

  /**
   * The start offset for this sub-set of data, where zero is the offset of the first record in the full result set
   * @format int64
   * @example 0
   */
  offset: number;
}

export interface OffsetRangeLong {
  /** @format int64 */
  length?: number;

  /** @format int64 */
  offset?: number;
}

export interface OverrideValue {
  hasOverride?: boolean;
  value?: object;
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

/**
 * A key value pair that describes a property of a query
 */
export interface Param {
  /** The property key */
  key: string;

  /** The property value */
  value: string;
}

export interface PasswordPolicyConfig {
  /** Will the UI allow password resets */
  allowPasswordResets?: boolean;

  /** If true, on first login the user will be forced to change their password. */
  forcePasswordChangeOnFirstLogin?: boolean;

  /** The age after which a password will have to be changed. The frequency of checks is controlled by the job 'Account Maintenance'. */
  mandatoryPasswordChangeDuration: StroomDuration;

  /**
   * The minimum number of characters that new passwords need to contain.
   * @format int32
   * @min 0
   */
  minimumPasswordLength: number;

  /**
   * The minimum strength password that is allowed.
   * @format int32
   * @min 0
   * @max 5
   */
  minimumPasswordStrength: number;

  /** Unused user accounts with a duration since account creation greater than this value will be locked. The frequency of checks is controlled by the job 'Account Maintenance'. */
  neverUsedAccountDeactivationThreshold: StroomDuration;

  /** A regex pattern that new passwords must match */
  passwordComplexityRegex?: string;

  /** A message informing users of the password policy */
  passwordPolicyMessage?: string;

  /** User accounts with a duration since last login greater than this value will be locked. The frequency of checks is controlled by the job 'Account Maintenance'. */
  unusedAccountDeactivationThreshold: StroomDuration;
}

export type PermissionChangeEvent = object;

export interface PermissionChangeRequest {
  event?: PermissionChangeEvent;
}

export interface PipelineDTO {
  configStack?: PipelineData[];
  description?: string;

  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
  docRef?: DocRef;
  merged?: PipelineData;

  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
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

  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
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

  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
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

  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
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

  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
  entity?: DocRef;

  /** @format int32 */
  integer?: number;

  /** @format int64 */
  long?: number;
  string?: string;
}

export interface PipelineReference {
  element: string;

  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
  feed: DocRef;
  name: string;

  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
  pipeline: DocRef;

  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
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

  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
  pipeline?: DocRef;
  stepFilterMap?: Record<string, SteppingFilterSettings>;
  stepLocation?: StepLocation;

  /** @format int32 */
  stepSize?: number;
  stepType?: "FIRST" | "FORWARD" | "BACKWARD" | "LAST" | "REFRESH";
}

export interface ProcessConfig {
  /**
   * The default number of records that batch search processing will be limited by.
   * @format int64
   */
  defaultRecordLimit?: number;

  /**
   * The default number of minutes that batch search processing will be limited by.
   * @format int64
   */
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
}

export type ProcessorRow = ProcessorListRow & { expander?: Expander; processor?: Processor };

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

/**
 * The query terms for the search
 */
export interface Query {
  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
  dataSource: DocRef;

  /** The root logical operator in the query expression tree */
  expression: ExpressionOperator;

  /** A list of key/value pairs that provide additional information about the query */
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
  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
  dataSource?: DocRef;

  /** A logical addOperator term in a query expression tree */
  expression?: ExpressionOperator;
  limits?: Limits;
}

/**
 * A unique key to identify the instance of the search by. This key is used to identify multiple requests for the same search when running in incremental mode.
 */
export interface QueryKey {
  /**
   * The UUID that makes up the query key
   * @example 7740bcd0-a49e-4c22-8540-044f85770716
   */
  uuid: string;
}

export interface Range {
  from?: Number;
  matchNull?: boolean;
  to?: Number;
}

export interface RangeInteger {
  /** @format int32 */
  from?: number;
  matchNull?: boolean;

  /** @format int32 */
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

  /** A logical addOperator term in a query expression tree */
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
  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
  pipelineDocRef?: DocRef;
  pipelineVersion?: string;

  /** @format int64 */
  streamId?: number;

  /** @format int64 */
  streamNo?: number;
}

export type RemovePermissionEvent = PermissionChangeEvent & {
  documentUuid?: string;
  permission?: string;
  userUuid?: string;
};

export interface ReprocessDataInfo {
  details?: string;
  message?: string;
  severity?: "INFO" | "WARNING" | "ERROR" | "FATAL_ERROR";
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

/**
 * Base object for describing a set of result data
 */
export interface Result {
  /** The ID of the component that this result set was requested for. See ResultRequest in SearchRequest */
  componentId: string;

  /** If an error has occurred producing this result set then this will have details of the error */
  error?: string;
}

export interface ResultPage {
  pageResponse?: PageResponse;
  values?: object[];
}

export interface ResultPageDBTableStatus {
  pageResponse?: PageResponse;
  values?: DBTableStatus[];
}

export interface ResultPageUser {
  pageResponse?: PageResponse;
  values?: User[];
}

/**
 * A definition for how to return the raw results of the query in the SearchResponse, e.g. sorted, grouped, limited, etc.
 */
export interface ResultRequest {
  /** The ID of the component that will receive the results corresponding to this ResultRequest */
  componentId: string;

  /** The fetch mode for the query. NONE means fetch no data, ALL means fetch all known results, CHANGES means fetch only those records not see in previous requests */
  fetch?: "NONE" | "CHANGES" | "ALL";
  mappings: TableSettings[];

  /** TODO */
  openGroups: string[];

  /** The offset and length of a range of data in a sub-set of a query result set */
  requestedRange: OffsetRange;

  /** The style of results required. FLAT will provide a FlatResult object, while TABLE will provide a TableResult object */
  resultStyle: "FLAT" | "TABLE";
}

/**
 * A row of data in a result set
 */
export interface Row {
  backgroundColor?: string;

  /**
   * The grouping depth, where 0 is the top level of grouping, or where there is no grouping
   * @format int32
   * @example 0
   */
  depth: number;

  /** TODO */
  groupKey: string;
  textColor?: string;

  /** The value for this row of data. The values in the list are in the same order as the fields in the ResultRequest */
  values: string[];
}

export interface SavePipelineXmlRequest {
  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
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

  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
  dataSourceRef?: DocRef;

  /** A logical addOperator term in a query expression tree */
  expression?: ExpressionOperator;
  incremental?: boolean;
  params?: Param[];
  queryInfo?: string;
  storeHistory?: boolean;
}

export interface SearchAccountRequest {
  pageRequest?: PageRequest;
  quickFilter?: string;
  sortList?: Sort[];
}

export interface SearchBusPollRequest {
  applicationInstanceId?: string;
  searchRequests?: SearchRequest[];
}

/**
 * A request for new search or a follow up request for more data for an existing iterative search
 */
export interface SearchRequest {
  /** The locale to use when formatting date values in the search results. The value is the string form of a java.time.ZoneId */
  dateTimeLocale: string;

  /** If true the response will contain all results found so far, typically no results on the first request. Future requests for the same query key may return more results. Intended for use on longer running searches to allow partial result sets to be returned as soon as they are available rather than waiting for the full result set. */
  incremental: boolean;

  /** A unique key to identify the instance of the search by. This key is used to identify multiple requests for the same search when running in incremental mode. */
  key: QueryKey;

  /** The query terms for the search */
  query: Query;
  resultRequests: ResultRequest[];

  /**
   * Set the maximum time (in ms) for the server to wait for a complete result set. The timeout applies to both incremental and non incremental queries, though the behaviour is slightly different. The timeout will make the server wait for which ever comes first out of the query completing or the timeout period being reached. If no value is supplied then for an incremental query a default value of 0 will be used (i.e. returning immediately) and for a non-incremental query the server's default timeout period will be used. For an incremental query, if the query has not completed by the end of the timeout period, it will return the currently know results with complete=false, however for a non-incremental query it will return no results, complete=false and details of the timeout in the error field
   * @format int64
   */
  timeout?: number;
}

/**
 * The response to a search request, that may or may not contain results. The results may only be a partial set if an iterative screech was requested
 */
export interface SearchResponse {
  /** True if the query has returned all known results */
  complete?: boolean;

  /** A list of errors that occurred in running the query */
  errors?: string[];

  /** A list of strings to highlight in the UI that should correlate with the search query. */
  highlights: string[];
  results?: Result[];
}

export interface SearchTokenRequest {
  pageRequest?: PageRequest;
  quickFilter?: string;
  sortList?: Sort[];
}

export interface Selection {
  matchAll?: boolean;
  set?: object[];
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

  /** A logical addOperator term in a query expression tree */
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
  desc?: boolean;
  id?: string;
  ignoreCase?: boolean;
}

export interface SourceConfig {
  /**
   * The maximum number of characters of data to display in the Data Preview pane.
   * @format int64
   * @min 1
   */
  maxCharactersInPreviewFetch?: number;

  /**
   * The maximum number of characters of data to display in the Source View editor at at time.
   * @format int64
   * @min 1
   */
  maxCharactersPerFetch?: number;

  /**
   * When displaying multi-line data in the Data Preview or Source views, the viewer will attempt to always show complete lines. It will go past the requested range by up to this many characters in order to complete the line.
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
  /** The HTML to display in the splash screen. */
  body?: string;

  /** If you would like users to see a splash screen on login. */
  enabled?: boolean;

  /** The title of the splash screen popup. */
  title?: string;

  /** The version of the splash screen message. */
  version?: string;
}

export type SplitLayoutConfig = LayoutConfig & { children?: LayoutConfig[]; dimension?: number; preferredSize?: Size };

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
  skipToSeverity?: "INFO" | "WARNING" | "ERROR" | "FATAL_ERROR";
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
  severity?: "INFO" | "WARNING" | "ERROR" | "FATAL_ERROR";
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

  /** The query terms for the search */
  query?: Query;

  /** @format int64 */
  updateTimeMs?: number;
  updateUser?: string;

  /** @format int32 */
  version?: number;
}

export type StreamLocation = Location & { colNo?: number; lineNo?: number; streamNo?: number };

export type StreamingOutput = object;

export interface StringCriteria {
  caseInsensitive?: boolean;
  matchNull?: boolean;
  matchStyle?: "Wild" | "WildStart" | "WildEnd" | "WildStartAndEnd";
  string?: string;
  stringUpper?: string;
}

export type StroomDuration = object;

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

export type Summary = Marker & {
  count?: number;
  expander?: Expander;
  severity?: "INFO" | "WARNING" | "ERROR" | "FATAL_ERROR";
  total?: number;
};

export interface SystemInfoResult {
  description?: string;
  details?: Record<string, object>;
  name: string;
}

export interface TabConfig {
  id?: string;
  visible?: boolean;
}

export type TabLayoutConfig = LayoutConfig & { preferredSize?: Size; selected?: number; tabs?: TabConfig[] };

export type TableComponentSettings = ComponentSettings & {
  conditionalFormattingRules?: ConditionalFormattingRule[];
  extractValues?: boolean;
  extractionPipeline?: DocRef;
  fields: Field[];
  maxResults?: number[];
  modelVersion?: string;
  queryId: string;
  showDetail?: boolean;
};

export type TableResult = Result & { fields: Field[]; resultRange: OffsetRange; rows: Row[]; totalResults?: number };

export type TableResultRequest = ComponentResultRequest & {
  openGroups?: string[];
  requestedRange?: OffsetRange;
  tableSettings?: TableSettings;
};

/**
 * An object to describe how the query results should be returned, including which fields should be included and what sorting, grouping, filtering, limiting, etc. should be applied
 */
export interface TableSettings {
  conditionalFormattingRules?: ConditionalFormattingRule[];

  /** TODO */
  extractValues?: boolean;

  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
  extractionPipeline?: DocRef;
  fields: Field[];

  /**
   * Defines the maximum number of results to return at each grouping level, e.g. '1000,10,1' means 1000 results at group level 0, 10 at level 1 and 1 at level 2. In the absence of this field system defaults will apply
   * @example 1000,10,1
   */
  maxResults?: number[];
  modelVersion?: string;

  /** TODO */
  queryId: string;

  /** When grouping is used a value of true indicates that the results will include the full detail of any results aggregated into a group as well as their aggregates. A value of false will only include the aggregated values for each group. Defaults to false. */
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

export type TextField = AbstractField & object;

export interface TextRange {
  from?: Location;
  to?: Location;
}

export interface ThemeConfig {
  /** GUI */
  backgroundAttachment?: string;

  /** GUI */
  backgroundColor?: string;

  /** GUI */
  backgroundImage?: string;

  /** GUI */
  backgroundOpacity?: string;

  /** GUI */
  backgroundPosition?: string;

  /** GUI */
  backgroundRepeat?: string;

  /** A comma separated list of KV pairs to provide colours for labels. */
  labelColours?: string;

  /** GUI */
  tubeOpacity?: string;

  /** GUI */
  tubeVisible?: string;
}

/**
 * The timezone to apply to a date time value
 */
export interface TimeZone {
  /**
   * The id of the time zone, conforming to java.time.ZoneId
   * @example GMT
   */
  id?: string;

  /**
   * The number of hours this timezone is offset from UTC
   * @format int32
   * @example -1
   */
  offsetHours?: number;

  /**
   * The number of minutes this timezone is offset from UTC
   * @format int32
   * @example -30
   */
  offsetMinutes?: number;

  /** How the time zone will be specified, e.g. from provided client 'Local' time, 'UTC', a recognised timezone 'Id' or an 'Offset' from UTC in hours and minutes. */
  use: "LOCAL" | "UTC" | "ID" | "OFFSET";
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
  /** The cryptographic algorithm used in the Json Web Signatures. Valid values can be found at https://openid.net/specs/draft-jones-json-web-signature-04.html#Signing */
  algorithm: string;

  /**
   * The default API key expiry time
   * @format int64
   */
  defaultApiKeyExpiryInMinutes?: number;

  /** The Issuer value used in Json Web Tokens. */
  jwsIssuer: string;

  /** The time before an email reset token will expire. */
  timeUntilExpirationForEmailResetToken: StroomDuration;

  /** The time before a user token will expire. */
  timeUntilExpirationForUserToken: StroomDuration;
}

export interface TokenRequest {
  client_id?: string;
  client_secret?: string;
  code?: string;
  grant_type?: string;
  redirect_uri?: string;
}

export interface TokenResultPage {
  pageResponse?: PageResponse;
  values?: Token[];
}

export interface UiConfig {
  /** The about message that is displayed when selecting Help -> About. The about message is in HTML format. */
  aboutHtml?: string;
  activity?: ActivityConfig;

  /** The default maximum number of search results to return to the dashboard, unless the user requests lower values. */
  defaultMaxResults?: string;

  /** The URL of hosted help files. */
  helpUrl?: string;

  /** The title to use for the application in the browser. */
  htmlTitle?: string;

  /** Provide a warning message to users about an outage or other significant event. */
  maintenanceMessage?: string;

  /** The regex pattern for entity names. */
  namePattern?: string;

  /**
   * Determines the behaviour of the browser built-in context menu. This property is for developer use only. Set to 'return false;' to see Stroom's context menu. Set to 'return true;' to see the standard browser menu.
   * @pattern ^return (true|false);$
   */
  oncontextmenu?: string;
  process?: ProcessConfig;
  query?: QueryConfig;
  source?: SourceConfig;
  splash?: SplashConfig;
  theme?: ThemeConfig;
  uiPreferences?: UiPreferences;
  url?: UrlConfig;

  /** The welcome message that is displayed in the welcome tab when logging in to Stroom. The welcome message is in HTML format. */
  welcomeHtml?: string;
}

export interface UiPreferences {
  /** The date format to use in the UI */
  dateFormat?: string;
}

export interface UpdateAccountRequest {
  account?: Account;
  confirmPassword?: string;
  password?: string;
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
  /** The path to the API Keys screen. */
  apiKeys?: string;

  /** The path to the Change Password screen. */
  changepassword?: string;

  /** The path to the Users screen. */
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

export interface ValidateExpressionResult {
  ok?: boolean;
  string?: string;
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

  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
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

/**
 * A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline.
 */
export interface XsltDTO {
  data?: string;
  description?: string;

  /**
   * The name for the data source
   * @example MyStatistic
   */
  name: string;

  /**
   * The type of the 'document' that this DocRef refers to
   * @example StroomStatsStore
   */
  type: string;

  /**
   * The unique identifier for this 'document'
   * @example 9f6184b4-bd78-48bc-b0cd-6e51a357f6a6
   */
  uuid: string;
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
}

export class HttpClient<SecurityDataType = unknown> {
  public baseUrl: string = "/api";
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

  protected addQueryParams(rawQuery?: RequestQueryParamsType): string {
    const query = rawQuery || {};
    const keys = Object.keys(query).filter((key) => "undefined" !== typeof query[key]);
    return keys.length
      ? `?${keys
          .map((key) =>
            typeof query[key] === "object" && !Array.isArray(query[key])
              ? this.addQueryParams(query[key] as object).substring(1)
              : this.addQueryParam(query, key),
          )
          .join("&")}`
      : "";
  }

  private bodyFormatters: Record<BodyType, (input: any) => any> = {
    [BodyType.Json]: JSON.stringify,
    [BodyType.FormData]: (input: any) =>
      Object.keys(input).reduce((data, key) => {
        data.append(key, input[key]);
        return data;
      }, new FormData()),
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
 * @title Stroom API
 * @version v1/v2
 * @baseUrl /api
 * Various APIs for interacting with Stroom and its data
 */
export class Api<SecurityDataType = any> extends HttpClient<SecurityDataType> {
  account = {
    /**
     * No description
     *
     * @tags Account
     * @name List
     * @summary Get all accounts.
     * @request GET:/account/v1
     * @secure
     */
    list: (params?: RequestParams) =>
      this.request<AccountResultPage, any>(`/account/v1`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags Account
     * @name Create
     * @summary Create an account.
     * @request POST:/account/v1
     * @secure
     */
    create: (body: CreateAccountRequest, params?: RequestParams) =>
      this.request<number, any>(`/account/v1`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags Account
     * @name Search
     * @summary Search for an account by email.
     * @request POST:/account/v1/search
     * @secure
     */
    search: (body: SearchAccountRequest, params?: RequestParams) =>
      this.request<AccountResultPage, any>(`/account/v1/search`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags Account
     * @name Read
     * @summary Get an account by ID.
     * @request GET:/account/v1/{id}
     * @secure
     */
    read: (id: number, params?: RequestParams) =>
      this.request<Account, any>(`/account/v1/${id}`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags Account
     * @name Update
     * @summary Update an account.
     * @request PUT:/account/v1/{id}
     * @secure
     */
    update: (id: number, body: UpdateAccountRequest, params?: RequestParams) =>
      this.request<boolean, any>(`/account/v1/${id}`, "PUT", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags Account
     * @name Delete
     * @summary Delete an account by ID.
     * @request DELETE:/account/v1/{id}
     * @secure
     */
    delete: (id: number, params?: RequestParams) =>
      this.request<boolean, any>(`/account/v1/${id}`, "DELETE", params, null, BodyType.Json, true),
  };
  activity = {
    /**
     * No description
     *
     * @tags activity - v1
     * @name List
     * @summary Lists activities
     * @request GET:/activity/v1
     * @secure
     */
    list: (query?: { filter?: string }, params?: RequestParams) =>
      this.request<ResultPage, any>(
        `/activity/v1${this.addQueryParams(query)}`,
        "GET",
        params,
        null,
        BodyType.Json,
        true,
      ),

    /**
     * No description
     *
     * @tags activity - v1
     * @name Create
     * @summary Create an Activity
     * @request POST:/activity/v1
     * @secure
     */
    create: (params?: RequestParams) =>
      this.request<Activity, any>(`/activity/v1`, "POST", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags activity - v1
     * @name AcknowledgeSplash
     * @summary Acknowledge the slash screen
     * @request POST:/activity/v1/acknowledge
     * @secure
     */
    acknowledgeSplash: (body: AcknowledgeSplashRequest, params?: RequestParams) =>
      this.request<boolean, any>(`/activity/v1/acknowledge`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags activity - v1
     * @name GetCurrentActivity
     * @summary Gets the current activity
     * @request GET:/activity/v1/current
     * @secure
     */
    getCurrentActivity: (params?: RequestParams) =>
      this.request<Activity, any>(`/activity/v1/current`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags activity - v1
     * @name SetCurrentActivity
     * @summary Gets the current activity
     * @request PUT:/activity/v1/current
     * @secure
     */
    setCurrentActivity: (params?: RequestParams) =>
      this.request<Activity, any>(`/activity/v1/current`, "PUT", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags activity - v1
     * @name ListFieldDefinitions
     * @summary Lists activity field definitions
     * @request GET:/activity/v1/fields
     * @secure
     */
    listFieldDefinitions: (params?: RequestParams) =>
      this.request<object[], any>(`/activity/v1/fields`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags activity - v1
     * @name Validate
     * @summary Create an Activity
     * @request POST:/activity/v1/validate
     * @secure
     */
    validate: (body: Activity, params?: RequestParams) =>
      this.request<ActivityValidationResult, any>(`/activity/v1/validate`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags activity - v1
     * @name Read
     * @summary Get an Activity
     * @request GET:/activity/v1/{id}
     * @secure
     */
    read: (id: number, params?: RequestParams) =>
      this.request<Activity, any>(`/activity/v1/${id}`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags activity - v1
     * @name Update
     * @summary Update an Activity
     * @request PUT:/activity/v1/{id}
     * @secure
     */
    update: (id: number, params?: RequestParams) =>
      this.request<Activity, any>(`/activity/v1/${id}`, "PUT", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags activity - v1
     * @name Delete
     * @summary Delete an activity
     * @request DELETE:/activity/v1/{id}
     * @secure
     */
    delete: (id: number, params?: RequestParams) =>
      this.request<boolean, any>(`/activity/v1/${id}`, "DELETE", params, null, BodyType.Json, true),
  };
  annotation = {
    /**
     * No description
     *
     * @tags annotations - v1
     * @name Get
     * @summary Gets an annotation
     * @request GET:/annotation/v1
     * @secure
     */
    get: (query?: { annotationId?: number }, params?: RequestParams) =>
      this.request<any, any>(`/annotation/v1${this.addQueryParams(query)}`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags annotations - v1
     * @name CreateEntry
     * @request POST:/annotation/v1
     * @secure
     */
    createEntry: (body: CreateEntryRequest, params?: RequestParams) =>
      this.request<AnnotationDetail, any>(`/annotation/v1`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags annotations - v1
     * @name GetComment
     * @summary Gets a list of predefined comments
     * @request GET:/annotation/v1/comment
     * @secure
     */
    getComment: (query?: { filter?: string }, params?: RequestParams) =>
      this.request<any, any>(
        `/annotation/v1/comment${this.addQueryParams(query)}`,
        "GET",
        params,
        null,
        BodyType.Json,
        true,
      ),

    /**
     * No description
     *
     * @tags annotations - v1
     * @name Link
     * @summary Links an annotation to an event
     * @request POST:/annotation/v1/link
     * @secure
     */
    link: (body: EventLink, params?: RequestParams) =>
      this.request<any, any>(`/annotation/v1/link`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags annotations - v1
     * @name GetLinkedEvents
     * @summary Gets a list of events linked to this annotation
     * @request GET:/annotation/v1/linkedEvents
     * @secure
     */
    getLinkedEvents: (query?: { annotationId?: number }, params?: RequestParams) =>
      this.request<any, any>(
        `/annotation/v1/linkedEvents${this.addQueryParams(query)}`,
        "GET",
        params,
        null,
        BodyType.Json,
        true,
      ),

    /**
     * No description
     *
     * @tags annotations - v1
     * @name SetAssignedTo
     * @summary Bulk action to set the assignment for several annotations
     * @request POST:/annotation/v1/setAssignedTo
     * @secure
     */
    setAssignedTo: (body: SetAssignedToRequest, params?: RequestParams) =>
      this.request<any, any>(`/annotation/v1/setAssignedTo`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags annotations - v1
     * @name SetStatus
     * @summary Bulk action to set the status for several annotations
     * @request POST:/annotation/v1/setStatus
     * @secure
     */
    setStatus: (body: SetStatusRequest, params?: RequestParams) =>
      this.request<any, any>(`/annotation/v1/setStatus`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags annotations - v1
     * @name GetStatus
     * @summary Gets a list of allowed statuses
     * @request GET:/annotation/v1/status
     * @secure
     */
    getStatus: (query?: { filter?: string }, params?: RequestParams) =>
      this.request<any, any>(
        `/annotation/v1/status${this.addQueryParams(query)}`,
        "GET",
        params,
        null,
        BodyType.Json,
        true,
      ),

    /**
     * No description
     *
     * @tags annotations - v1
     * @name Unlink
     * @summary Unlinks an annotation from an event
     * @request POST:/annotation/v1/unlink
     * @secure
     */
    unlink: (body: EventLink, params?: RequestParams) =>
      this.request<any, any>(`/annotation/v1/unlink`, "POST", params, body, BodyType.Json, true),
  };
  appPermissions = {
    /**
     * @description Stroom Application Permissions API
     *
     * @tags application permissions - v1
     * @name GetAllPermissionNames
     * @request GET:/appPermissions/v1
     * @secure
     */
    getAllPermissionNames: (params?: RequestParams) =>
      this.request<any, any>(`/appPermissions/v1`, "GET", params, null, BodyType.Json, true),

    /**
     * @description Stroom Application Permissions API
     *
     * @tags application permissions - v1
     * @name GetPermissionNamesForUserName
     * @request GET:/appPermissions/v1/byName/{userName}
     * @secure
     */
    getPermissionNamesForUserName: (userName: string, params?: RequestParams) =>
      this.request<any, any>(`/appPermissions/v1/byName/${userName}`, "GET", params, null, BodyType.Json, true),

    /**
     * @description Stroom Application Permissions API
     *
     * @tags application permissions - v1
     * @name GetPermissionNamesForUser
     * @request GET:/appPermissions/v1/{userUuid}
     * @secure
     */
    getPermissionNamesForUser: (userUuid: string, params?: RequestParams) =>
      this.request<any, any>(`/appPermissions/v1/${userUuid}`, "GET", params, null, BodyType.Json, true),

    /**
     * @description Stroom Application Permissions API
     *
     * @tags application permissions - v1
     * @name AddPermission
     * @request POST:/appPermissions/v1/{userUuid}/{permission}
     * @secure
     */
    addPermission: (permission: string, userUuid: string, params?: RequestParams) =>
      this.request<any, any>(`/appPermissions/v1/${userUuid}/${permission}`, "POST", params, null, BodyType.Json, true),

    /**
     * @description Stroom Application Permissions API
     *
     * @tags application permissions - v1
     * @name RemovePermission
     * @request DELETE:/appPermissions/v1/{userUuid}/{permission}
     * @secure
     */
    removePermission: (permission: string, userUuid: string, params?: RequestParams) =>
      this.request<any, any>(
        `/appPermissions/v1/${userUuid}/${permission}`,
        "DELETE",
        params,
        null,
        BodyType.Json,
        true,
      ),
  };
  authentication = {
    /**
     * @description Stroom Authentication API
     *
     * @tags Authentication
     * @name Logout
     * @summary Log a user out of their session
     * @request GET:/authentication/v1/logout
     * @secure
     */
    logout: (query: { redirect_uri: string }, params?: RequestParams) =>
      this.request<boolean, any>(
        `/authentication/v1/logout${this.addQueryParams(query)}`,
        "GET",
        params,
        null,
        BodyType.Json,
        true,
      ),

    /**
     * @description Stroom Authentication API
     *
     * @tags Authentication
     * @name NeedsPasswordChange
     * @summary Check if a user's password needs changing.
     * @request GET:/authentication/v1/needsPasswordChange
     * @secure
     */
    needsPasswordChange: (query?: { email?: string }, params?: RequestParams) =>
      this.request<boolean, any>(
        `/authentication/v1/needsPasswordChange${this.addQueryParams(query)}`,
        "GET",
        params,
        null,
        BodyType.Json,
        true,
      ),

    /**
     * @description Stroom Authentication API
     *
     * @tags Authentication
     * @name ChangePassword
     * @summary Change a user's password.
     * @request POST:/authentication/v1/noauth/changePassword
     * @secure
     */
    changePassword: (body: ChangePasswordRequest, params?: RequestParams) =>
      this.request<string, any>(`/authentication/v1/noauth/changePassword`, "POST", params, body, BodyType.Json, true),

    /**
     * @description Stroom Authentication API
     *
     * @tags Authentication
     * @name ConfirmPassword
     * @summary Confirm an authenticated users current password.
     * @request POST:/authentication/v1/noauth/confirmPassword
     * @secure
     */
    confirmPassword: (body: ConfirmPasswordRequest, params?: RequestParams) =>
      this.request<string, any>(`/authentication/v1/noauth/confirmPassword`, "POST", params, body, BodyType.Json, true),

    /**
     * @description Stroom Authentication API
     *
     * @tags Authentication
     * @name FetchPasswordPolicy
     * @summary Get the password policy
     * @request GET:/authentication/v1/noauth/fetchPasswordPolicy
     * @secure
     */
    fetchPasswordPolicy: (params?: RequestParams) =>
      this.request<PasswordPolicyConfig, any>(
        `/authentication/v1/noauth/fetchPasswordPolicy`,
        "GET",
        params,
        null,
        BodyType.Json,
        true,
      ),

    /**
     * @description Stroom Authentication API
     *
     * @tags Authentication
     * @name GetAuthenticationState
     * @summary Get the current authentication state
     * @request GET:/authentication/v1/noauth/getAuthenticationState
     * @secure
     */
    getAuthenticationState: (params?: RequestParams) =>
      this.request<AuthenticationState, any>(
        `/authentication/v1/noauth/getAuthenticationState`,
        "GET",
        params,
        null,
        BodyType.Json,
        true,
      ),

    /**
     * @description Stroom Authentication API
     *
     * @tags Authentication
     * @name Login
     * @summary Handle a login request made using username and password credentials.
     * @request POST:/authentication/v1/noauth/login
     * @secure
     */
    login: (body: LoginRequest, params?: RequestParams) =>
      this.request<string, any>(`/authentication/v1/noauth/login`, "POST", params, body, BodyType.Json, true),

    /**
     * @description Stroom Authentication API
     *
     * @tags Authentication
     * @name ResetEmail
     * @summary Reset a user account using an email address.
     * @request GET:/authentication/v1/noauth/reset/{email}
     * @secure
     */
    resetEmail: (email: string, params?: RequestParams) =>
      this.request<string, any>(`/authentication/v1/noauth/reset/${email}`, "GET", params, null, BodyType.Json, true),

    /**
     * @description Stroom Authentication API
     *
     * @tags Authentication
     * @name ResetPassword
     * @summary Reset an authenticated user's password.
     * @request POST:/authentication/v1/resetPassword
     * @secure
     */
    resetPassword: (body: ResetPasswordRequest, params?: RequestParams) =>
      this.request<string, any>(`/authentication/v1/resetPassword`, "POST", params, body, BodyType.Json, true),
  };
  authorisation = {
    /**
     * @description Stroom Authorisation API
     *
     * @tags authorisation - v1
     * @name CreateUser
     * @request POST:/authorisation/v1/createUser
     * @secure
     */
    createUser: (query?: { id?: string }, params?: RequestParams) =>
      this.request<any, any>(
        `/authorisation/v1/createUser${this.addQueryParams(query)}`,
        "POST",
        params,
        null,
        BodyType.Json,
        true,
      ),

    /**
     * @description Stroom Authorisation API
     *
     * @tags authorisation - v1
     * @name HasPermission
     * @request POST:/authorisation/v1/hasPermission
     * @secure
     */
    hasPermission: (params?: RequestParams) =>
      this.request<any, any>(`/authorisation/v1/hasPermission`, "POST", params, null, BodyType.Json, true),

    /**
     * @description Stroom Authorisation API
     *
     * @tags authorisation - v1
     * @name IsAuthorised
     * @summary Submit a request to verify if the user has the requested permission on a 'document'
     * @request POST:/authorisation/v1/isAuthorised
     * @secure
     */
    isAuthorised: (body: string, params?: RequestParams) =>
      this.request<any, any>(`/authorisation/v1/isAuthorised`, "POST", params, body, BodyType.Json, true),

    /**
     * @description Stroom Authorisation API
     *
     * @tags authorisation - v1
     * @name SetUserStatus
     * @request GET:/authorisation/v1/setUserStatus
     * @secure
     */
    setUserStatus: (query?: { status?: string; userId?: string }, params?: RequestParams) =>
      this.request<any, any>(
        `/authorisation/v1/setUserStatus${this.addQueryParams(query)}`,
        "GET",
        params,
        null,
        BodyType.Json,
        true,
      ),
  };
  cache = {
    /**
     * No description
     *
     * @tags cache - v1
     * @name List
     * @summary Lists caches
     * @request GET:/cache/v1
     * @secure
     */
    list: (params?: RequestParams) =>
      this.request<object[], any>(`/cache/v1`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags cache - v1
     * @name Clear
     * @summary Clears a cache
     * @request DELETE:/cache/v1
     * @secure
     */
    clear: (query?: { cacheName?: string; nodeName?: string }, params?: RequestParams) =>
      this.request<number, any>(`/cache/v1${this.addQueryParams(query)}`, "DELETE", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags cache - v1
     * @name Info
     * @summary Gets cache info
     * @request GET:/cache/v1/info
     * @secure
     */
    info: (query?: { cacheName?: string; nodeName?: string }, params?: RequestParams) =>
      this.request<CacheInfo, any>(
        `/cache/v1/info${this.addQueryParams(query)}`,
        "GET",
        params,
        null,
        BodyType.Json,
        true,
      ),
  };
  cluster = {
    /**
     * No description
     *
     * @tags clusterlock - v1
     * @name KeepLockAlive
     * @summary Keep a lock alive
     * @request PUT:/cluster/lock/v1/keepALive/{nodeName}
     * @secure
     */
    keepLockAlive: (nodeName: string, body: ClusterLockKey, params?: RequestParams) =>
      this.request<boolean, any>(`/cluster/lock/v1/keepALive/${nodeName}`, "PUT", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags clusterlock - v1
     * @name ReleaseLock
     * @summary Release a lock
     * @request PUT:/cluster/lock/v1/release/{nodeName}
     * @secure
     */
    releaseLock: (nodeName: string, body: ClusterLockKey, params?: RequestParams) =>
      this.request<boolean, any>(`/cluster/lock/v1/release/${nodeName}`, "PUT", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags clusterlock - v1
     * @name TryLock
     * @summary Try to lock
     * @request PUT:/cluster/lock/v1/try/{nodeName}
     * @secure
     */
    tryLock: (nodeName: string, body: ClusterLockKey, params?: RequestParams) =>
      this.request<boolean, any>(`/cluster/lock/v1/try/${nodeName}`, "PUT", params, body, BodyType.Json, true),
  };
  config = {
    /**
     * No description
     *
     * @tags config - v1
     * @name Create
     * @summary Update a ConfigProperty
     * @request POST:/config/v1
     * @secure
     */
    create: (body: ConfigProperty, params?: RequestParams) =>
      this.request<ConfigProperty, any>(`/config/v1`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags config - v1
     * @name Update
     * @summary Update a ConfigProperty
     * @request PUT:/config/v1/clusterProperties/{propertyName}/dbOverrideValue
     * @secure
     */
    update: (propertyName: string, body: ConfigProperty, params?: RequestParams) =>
      this.request<ConfigProperty, any>(
        `/config/v1/clusterProperties/${propertyName}/dbOverrideValue`,
        "PUT",
        params,
        body,
        BodyType.Json,
        true,
      ),

    /**
     * No description
     *
     * @tags config - v1
     * @name GetYamlValueByNodeAndName
     * @request GET:/config/v1/clusterProperties/{propertyName}/yamlOverrideValue/{nodeName}
     * @secure
     */
    getYamlValueByNodeAndName: (nodeName: string, propertyName: string, params?: RequestParams) =>
      this.request<OverrideValueString, any>(
        `/config/v1/clusterProperties/${propertyName}/yamlOverrideValue/${nodeName}`,
        "GET",
        params,
        null,
        BodyType.Json,
        true,
      ),

    /**
     * No description
     *
     * @tags config - v1
     * @name FetchUiConfig
     * @summary Get config property
     * @request GET:/config/v1/noauth/fetchUiConfig
     * @secure
     */
    fetchUiConfig: (params?: RequestParams) =>
      this.request<UiConfig, any>(`/config/v1/noauth/fetchUiConfig`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags config - v1
     * @name ListByNode
     * @request POST:/config/v1/nodeProperties/{nodeName}
     * @secure
     */
    listByNode: (nodeName: string, body: GlobalConfigCriteria, params?: RequestParams) =>
      this.request<ListConfigResponse, any>(
        `/config/v1/nodeProperties/${nodeName}`,
        "POST",
        params,
        body,
        BodyType.Json,
        true,
      ),

    /**
     * No description
     *
     * @tags config - v1
     * @name List
     * @summary TODO
     * @request POST:/config/v1/properties
     * @secure
     */
    list: (body: GlobalConfigCriteria, params?: RequestParams) =>
      this.request<ListConfigResponse, any>(`/config/v1/properties`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags config - v1
     * @name GetPropertyByName
     * @request GET:/config/v1/properties/{propertyName}
     * @secure
     */
    getPropertyByName: (propertyName: string, params?: RequestParams) =>
      this.request<ConfigProperty, any>(
        `/config/v1/properties/${propertyName}`,
        "GET",
        params,
        null,
        BodyType.Json,
        true,
      ),
  };
  content = {
    /**
     * No description
     *
     * @tags content - v1
     * @name ConfirmImport
     * @summary Get import confirmation state
     * @request POST:/content/v1/confirmImport
     * @secure
     */
    confirmImport: (body: ResourceKey, params?: RequestParams) =>
      this.request<object[], any>(`/content/v1/confirmImport`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags content - v1
     * @name ExportContent
     * @summary Export content
     * @request POST:/content/v1/export
     * @secure
     */
    exportContent: (body: DocRefs, params?: RequestParams) =>
      this.request<ResourceGeneration, any>(`/content/v1/export`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags content - v1
     * @name FetchDependencies
     * @summary Fetch content dependencies
     * @request POST:/content/v1/fetchDependencies
     * @secure
     */
    fetchDependencies: (body: DependencyCriteria, params?: RequestParams) =>
      this.request<ResultPage, any>(`/content/v1/fetchDependencies`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags content - v1
     * @name ImportContent
     * @summary Import content
     * @request POST:/content/v1/import
     * @secure
     */
    importContent: (body: ImportConfigRequest, params?: RequestParams) =>
      this.request<ResourceGeneration, any>(`/content/v1/import`, "POST", params, body, BodyType.Json, true),
  };
  dashboard = {
    /**
     * No description
     *
     * @tags dashboard - v1
     * @name DownloadQuery
     * @summary Download a query
     * @request POST:/dashboard/v1/downloadQuery
     * @secure
     */
    downloadQuery: (body: DownloadQueryRequest, params?: RequestParams) =>
      this.request<ResourceGeneration, any>(`/dashboard/v1/downloadQuery`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags dashboard - v1
     * @name DownloadSearchResults
     * @summary Download search results
     * @request POST:/dashboard/v1/downloadSearchResults
     * @secure
     */
    downloadSearchResults: (body: DownloadSearchResultsRequest, params?: RequestParams) =>
      this.request<ResourceGeneration, any>(
        `/dashboard/v1/downloadSearchResults`,
        "POST",
        params,
        body,
        BodyType.Json,
        true,
      ),

    /**
     * No description
     *
     * @tags dashboard - v1
     * @name FetchTimeZones
     * @summary Fetch time zone data from the server
     * @request GET:/dashboard/v1/fetchTimeZones
     * @secure
     */
    fetchTimeZones: (params?: RequestParams) =>
      this.request<object[], any>(`/dashboard/v1/fetchTimeZones`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags dashboard - v1
     * @name FetchFunctions
     * @summary Fetch all expression functions
     * @request GET:/dashboard/v1/functions
     * @secure
     */
    fetchFunctions: (params?: RequestParams) =>
      this.request<object[], any>(`/dashboard/v1/functions`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags dashboard - v1
     * @name Poll
     * @summary Poll for new search results
     * @request POST:/dashboard/v1/poll
     * @secure
     */
    poll: (body: SearchBusPollRequest, params?: RequestParams) =>
      this.request<object[], any>(`/dashboard/v1/poll`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags dashboard - v1
     * @name Read
     * @summary Get a dashboard doc
     * @request POST:/dashboard/v1/read
     * @secure
     */
    read: (body: DocRef, params?: RequestParams) =>
      this.request<DashboardDoc, any>(`/dashboard/v1/read`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags dashboard - v1
     * @name Update
     * @summary Update a dashboard doc
     * @request PUT:/dashboard/v1/update
     * @secure
     */
    update: (params?: RequestParams) =>
      this.request<DashboardDoc, any>(`/dashboard/v1/update`, "PUT", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags dashboard - v1
     * @name ValidateExpression
     * @summary Validate an expression
     * @request POST:/dashboard/v1/validateExpression
     * @secure
     */
    validateExpression: (body: string, params?: RequestParams) =>
      this.request<ValidateExpressionResult, any>(
        `/dashboard/v1/validateExpression`,
        "POST",
        params,
        body,
        BodyType.Json,
        true,
      ),
  };
  data = {
    /**
     * No description
     *
     * @tags data - v1
     * @name Download
     * @summary Download matching data
     * @request POST:/data/v1/download
     * @secure
     */
    download: (body: FindMetaCriteria, params?: RequestParams) =>
      this.request<ResourceGeneration, any>(`/data/v1/download`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags data - v1
     * @name Info
     * @summary Find full info about a data item
     * @request GET:/data/v1/info/{id}
     * @secure
     */
    info: (id: number, params?: RequestParams) =>
      this.request<DataInfoSection, any>(`/data/v1/info/${id}`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags data - v1
     * @name Upload
     * @summary Upload data
     * @request POST:/data/v1/upload
     * @secure
     */
    upload: (body: UploadDataRequest, params?: RequestParams) =>
      this.request<ResourceGeneration, any>(`/data/v1/upload`, "POST", params, body, BodyType.Json, true),
  };
  dataRetentionRules = {
    /**
     * No description
     *
     * @tags dataRetentionRules - v1
     * @name GetRetentionDeletionSummary
     * @summary Get a summary of meta deletions with the passed data retention rules
     * @request POST:/dataRetentionRules/v1/impactSummary
     * @secure
     */
    getRetentionDeletionSummary: (body: DataRetentionDeleteSummaryRequest, params?: RequestParams) =>
      this.request<DataRetentionDeleteSummary, any>(
        `/dataRetentionRules/v1/impactSummary`,
        "POST",
        params,
        body,
        BodyType.Json,
        true,
      ),

    /**
     * No description
     *
     * @tags dataRetentionRules - v1
     * @name CancelQuery
     * @summary Delete a running query
     * @request DELETE:/dataRetentionRules/v1/impactSummary/{queryId}
     * @secure
     */
    cancelQuery: (queryId: string, params?: RequestParams) =>
      this.request<boolean, any>(
        `/dataRetentionRules/v1/impactSummary/${queryId}`,
        "DELETE",
        params,
        null,
        BodyType.Json,
        true,
      ),

    /**
     * No description
     *
     * @tags dataRetentionRules - v1
     * @name Read
     * @summary Get data retention rules
     * @request POST:/dataRetentionRules/v1/read
     * @secure
     */
    read: (params?: RequestParams) =>
      this.request<DataRetentionRules, any>(`/dataRetentionRules/v1/read`, "POST", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags dataRetentionRules - v1
     * @name Update
     * @summary Update data retention rules
     * @request PUT:/dataRetentionRules/v1/update
     * @secure
     */
    update: (body: DataRetentionRules, params?: RequestParams) =>
      this.request<DataRetentionRules, any>(`/dataRetentionRules/v1/update`, "PUT", params, body, BodyType.Json, true),
  };
  dataSource = {
    /**
     * No description
     *
     * @tags dataSource - v1
     * @name FetchFields
     * @summary Fetch data source fields
     * @request POST:/dataSource/v1/fetchFields
     * @secure
     */
    fetchFields: (body: DocRef, params?: RequestParams) =>
      this.request<object[], any>(`/dataSource/v1/fetchFields`, "POST", params, body, BodyType.Json, true),
  };
  dbStatus = {
    /**
     * No description
     *
     * @tags dbStatus - v1
     * @name GetSystemTableStatus
     * @summary Find status of the DB
     * @request GET:/dbStatus/v1
     * @secure
     */
    getSystemTableStatus: (params?: RequestParams) =>
      this.request<ResultPageDBTableStatus, any>(`/dbStatus/v1`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags dbStatus - v1
     * @name FindSystemTableStatus
     * @summary Find status of the DB
     * @request POST:/dbStatus/v1
     * @secure
     */
    findSystemTableStatus: (body: FindDBTableCriteria, params?: RequestParams) =>
      this.request<ResultPageDBTableStatus, any>(`/dbStatus/v1`, "POST", params, body, BodyType.Json, true),
  };
  dictionary = {
    /**
     * No description
     *
     * @tags dictionary - v1
     * @name Download
     * @summary Download a dictionary doc
     * @request POST:/dictionary/v1/download
     * @secure
     */
    download: (params?: RequestParams) =>
      this.request<ResourceGeneration, any>(`/dictionary/v1/download`, "POST", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags dictionary - v1
     * @name ExportDocument
     * @summary Submit an export request
     * @request POST:/dictionary/v1/export
     * @secure
     */
    exportDocument: (body: DocRef, params?: RequestParams) =>
      this.request<Base64EncodedDocumentData, any>(`/dictionary/v1/export`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags dictionary - v1
     * @name ImportDocument
     * @summary Submit an import request
     * @request POST:/dictionary/v1/import
     * @secure
     */
    importDocument: (body: Base64EncodedDocumentData, params?: RequestParams) =>
      this.request<DocRef, any>(`/dictionary/v1/import`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags dictionary - v1
     * @name ListDocuments
     * @summary Submit a request for a list of doc refs held by this service
     * @request GET:/dictionary/v1/list
     * @secure
     */
    listDocuments: (params?: RequestParams) =>
      this.request<object[], any>(`/dictionary/v1/list`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags dictionary - v1
     * @name Read
     * @summary Get a dictionary doc
     * @request POST:/dictionary/v1/read
     * @secure
     */
    read: (params?: RequestParams) =>
      this.request<DictionaryDoc, any>(`/dictionary/v1/read`, "POST", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags dictionary - v1
     * @name Update
     * @summary Update a dictionary doc
     * @request PUT:/dictionary/v1/update
     * @secure
     */
    update: (params?: RequestParams) =>
      this.request<DictionaryDoc, any>(`/dictionary/v1/update`, "PUT", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags dictionary - v1
     * @name Fetch
     * @request GET:/dictionary/v1/{dictionaryUuid}
     * @secure
     */
    fetch: (dictionaryUuid: string, params?: RequestParams) =>
      this.request<DictionaryDTO, any>(`/dictionary/v1/${dictionaryUuid}`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags dictionary - v1
     * @name Save
     * @request POST:/dictionary/v1/{dictionaryUuid}
     * @secure
     */
    save: (dictionaryUuid: string, params?: RequestParams) =>
      this.request<any, any>(`/dictionary/v1/${dictionaryUuid}`, "POST", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags dictionary - v2
     * @name ExportDocument2
     * @summary Submit an export request
     * @request POST:/dictionary/v2/export
     * @originalName exportDocument
     * @duplicate
     * @secure
     */
    exportDocument2: (body: DocRef, params?: RequestParams) =>
      this.request<Base64EncodedDocumentData, any>(`/dictionary/v2/export`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags dictionary - v2
     * @name ImportDocument2
     * @summary Submit an import request
     * @request POST:/dictionary/v2/import
     * @originalName importDocument
     * @duplicate
     * @secure
     */
    importDocument2: (body: Base64EncodedDocumentData, params?: RequestParams) =>
      this.request<DocRef, any>(`/dictionary/v2/import`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags dictionary - v2
     * @name ListDocuments2
     * @summary Submit a request for a list of doc refs held by this service
     * @request GET:/dictionary/v2/list
     * @originalName listDocuments
     * @duplicate
     * @secure
     */
    listDocuments2: (params?: RequestParams) =>
      this.request<object[], any>(`/dictionary/v2/list`, "GET", params, null, BodyType.Json, true),
  };
  docPermissions = {
    /**
     * @description Stroom Document Permissions API
     *
     * @tags document permissions - v1
     * @name GetPermissionsForDocument
     * @request GET:/docPermissions/v1/forDoc/{docUuid}
     * @secure
     */
    getPermissionsForDocument: (docUuid: string, params?: RequestParams) =>
      this.request<any, any>(`/docPermissions/v1/forDoc/${docUuid}`, "GET", params, null, BodyType.Json, true),

    /**
     * @description Stroom Document Permissions API
     *
     * @tags document permissions - v1
     * @name ClearDocumentPermissions
     * @request DELETE:/docPermissions/v1/forDoc/{docUuid}
     * @secure
     */
    clearDocumentPermissions: (docUuid: string, params?: RequestParams) =>
      this.request<any, any>(`/docPermissions/v1/forDoc/${docUuid}`, "DELETE", params, null, BodyType.Json, true),

    /**
     * @description Stroom Document Permissions API
     *
     * @tags document permissions - v1
     * @name GetPermissionsForDocumentForUser
     * @request GET:/docPermissions/v1/forDocForUser/{docUuid}/{userUuid}
     * @secure
     */
    getPermissionsForDocumentForUser: (docUuid: string, userUuid: string, params?: RequestParams) =>
      this.request<any, any>(
        `/docPermissions/v1/forDocForUser/${docUuid}/${userUuid}`,
        "GET",
        params,
        null,
        BodyType.Json,
        true,
      ),

    /**
     * @description Stroom Document Permissions API
     *
     * @tags document permissions - v1
     * @name RemovePermissionForDocumentForUser
     * @request DELETE:/docPermissions/v1/forDocForUser/{docUuid}/{userUuid}
     * @secure
     */
    removePermissionForDocumentForUser: (docUuid: string, userUuid: string, params?: RequestParams) =>
      this.request<any, any>(
        `/docPermissions/v1/forDocForUser/${docUuid}/${userUuid}`,
        "DELETE",
        params,
        null,
        BodyType.Json,
        true,
      ),

    /**
     * @description Stroom Document Permissions API
     *
     * @tags document permissions - v1
     * @name AddPermission
     * @request POST:/docPermissions/v1/forDocForUser/{docUuid}/{userUuid}/{permissionName}
     * @secure
     */
    addPermission: (docUuid: string, permissionName: string, userUuid: string, params?: RequestParams) =>
      this.request<any, any>(
        `/docPermissions/v1/forDocForUser/${docUuid}/${userUuid}/${permissionName}`,
        "POST",
        params,
        null,
        BodyType.Json,
        true,
      ),

    /**
     * @description Stroom Document Permissions API
     *
     * @tags document permissions - v1
     * @name RemovePermission
     * @request DELETE:/docPermissions/v1/forDocForUser/{docUuid}/{userUuid}/{permissionName}
     * @secure
     */
    removePermission: (docUuid: string, permissionName: string, userUuid: string, params?: RequestParams) =>
      this.request<any, any>(
        `/docPermissions/v1/forDocForUser/${docUuid}/${userUuid}/${permissionName}`,
        "DELETE",
        params,
        null,
        BodyType.Json,
        true,
      ),

    /**
     * @description Stroom Document Permissions API
     *
     * @tags document permissions - v1
     * @name GetPermissionForDocType
     * @request GET:/docPermissions/v1/forDocType/{docType}
     * @secure
     */
    getPermissionForDocType: (docType: string, params?: RequestParams) =>
      this.request<any, any>(`/docPermissions/v1/forDocType/${docType}`, "GET", params, null, BodyType.Json, true),
  };
  elements = {
    /**
     * No description
     *
     * @tags elements - v1
     * @name GetElementProperties
     * @request GET:/elements/v1/elementProperties
     * @secure
     */
    getElementProperties: (params?: RequestParams) =>
      this.request<any, any>(`/elements/v1/elementProperties`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags elements - v1
     * @name GetElements
     * @request GET:/elements/v1/elements
     * @secure
     */
    getElements: (params?: RequestParams) =>
      this.request<any, any>(`/elements/v1/elements`, "GET", params, null, BodyType.Json, true),
  };
  entityEvent = {
    /**
     * No description
     *
     * @tags entityEvent - v1
     * @name FireEvent
     * @summary Sends an entity event
     * @request PUT:/entityEvent/v1/{nodeName}
     * @secure
     */
    fireEvent: (nodeName: string, body: EntityEvent, params?: RequestParams) =>
      this.request<boolean, any>(`/entityEvent/v1/${nodeName}`, "PUT", params, body, BodyType.Json, true),
  };
  explorer = {
    /**
     * No description
     *
     * @tags explorer - v1
     * @name GetExplorerTree
     * @request GET:/explorer/v1/all
     * @secure
     */
    getExplorerTree: (params?: RequestParams) =>
      this.request<any, any>(`/explorer/v1/all`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags explorer - v1
     * @name CopyDocument
     * @request POST:/explorer/v1/copy
     * @secure
     */
    copyDocument: (body: CopyOp, params?: RequestParams) =>
      this.request<any, any>(`/explorer/v1/copy`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags explorer - v1
     * @name CreateDocument
     * @request POST:/explorer/v1/create
     * @secure
     */
    createDocument: (body: CreateOp, params?: RequestParams) =>
      this.request<any, any>(`/explorer/v1/create`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags explorer - v1
     * @name DeleteDocument
     * @request DELETE:/explorer/v1/delete
     * @secure
     */
    deleteDocument: (params?: RequestParams) =>
      this.request<any, any>(`/explorer/v1/delete`, "DELETE", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags explorer - v1
     * @name GetDocRefTypes
     * @request GET:/explorer/v1/docRefTypes
     * @secure
     */
    getDocRefTypes: (params?: RequestParams) =>
      this.request<any, any>(`/explorer/v1/docRefTypes`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags explorer - v1
     * @name GetDocInfo
     * @request GET:/explorer/v1/info/{type}/{uuid}
     * @secure
     */
    getDocInfo: (type: string, uuid: string, params?: RequestParams) =>
      this.request<DocRefInfo, any>(`/explorer/v1/info/${type}/${uuid}`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags explorer - v1
     * @name MoveDocument
     * @request PUT:/explorer/v1/move
     * @secure
     */
    moveDocument: (params?: RequestParams) =>
      this.request<any, any>(`/explorer/v1/move`, "PUT", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags explorer - v1
     * @name RenameDocument
     * @request PUT:/explorer/v1/rename
     * @secure
     */
    renameDocument: (params?: RequestParams) =>
      this.request<any, any>(`/explorer/v1/rename`, "PUT", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags explorer - v1
     * @name Search
     * @request GET:/explorer/v1/search
     * @secure
     */
    search: (query?: { pageOffset?: number; pageSize?: number; searchTerm?: string }, params?: RequestParams) =>
      this.request<any, any>(
        `/explorer/v1/search${this.addQueryParams(query)}`,
        "GET",
        params,
        null,
        BodyType.Json,
        true,
      ),

    /**
     * No description
     *
     * @tags explorer - v2
     * @name Copy
     * @summary Copy explorer items
     * @request POST:/explorer/v2/copy
     * @secure
     */
    copy: (body: ExplorerServiceCopyRequest, params?: RequestParams) =>
      this.request<BulkActionResult, any>(`/explorer/v2/copy`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags explorer - v2
     * @name Create
     * @summary Create explorer item
     * @request POST:/explorer/v2/create
     * @secure
     */
    create: (body: ExplorerServiceCreateRequest, params?: RequestParams) =>
      this.request<DocRef, any>(`/explorer/v2/create`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags explorer - v2
     * @name Delete
     * @summary Delete explorer items
     * @request DELETE:/explorer/v2/delete
     * @secure
     */
    delete: (body: ExplorerServiceDeleteRequest, params?: RequestParams) =>
      this.request<BulkActionResult, any>(`/explorer/v2/delete`, "DELETE", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags explorer - v2
     * @name FetchDocRefs
     * @summary Fetch document references
     * @request POST:/explorer/v2/fetchDocRefs
     * @secure
     */
    fetchDocRefs: (body: DocRef[], params?: RequestParams) =>
      this.request<object[], any>(`/explorer/v2/fetchDocRefs`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags explorer - v2
     * @name FetchDocumentTypes
     * @summary Fetch document types
     * @request GET:/explorer/v2/fetchDocumentTypes
     * @secure
     */
    fetchDocumentTypes: (params?: RequestParams) =>
      this.request<DocumentTypes, any>(`/explorer/v2/fetchDocumentTypes`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags explorer - v2
     * @name Fetch
     * @summary Fetch explorer nodes
     * @request POST:/explorer/v2/fetchExplorerNodes
     * @secure
     */
    fetch: (body: FindExplorerNodeCriteria, params?: RequestParams) =>
      this.request<FetchExplorerNodeResult, any>(
        `/explorer/v2/fetchExplorerNodes`,
        "POST",
        params,
        body,
        BodyType.Json,
        true,
      ),

    /**
     * No description
     *
     * @tags explorer - v2
     * @name FetchExplorerPermissions
     * @summary Fetch permissions for explorer items
     * @request POST:/explorer/v2/fetchExplorerPermissions
     * @secure
     */
    fetchExplorerPermissions: (body: ExplorerNode[], params?: RequestParams) =>
      this.request<Record<string, object>, any>(
        `/explorer/v2/fetchExplorerPermissions`,
        "POST",
        params,
        body,
        BodyType.Json,
        true,
      ),

    /**
     * No description
     *
     * @tags explorer - v2
     * @name Info
     * @summary Get document info
     * @request POST:/explorer/v2/info
     * @secure
     */
    info: (body: DocRef, params?: RequestParams) =>
      this.request<DocRefInfo, any>(`/explorer/v2/info`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags explorer - v2
     * @name Move
     * @summary Move explorer items
     * @request PUT:/explorer/v2/move
     * @secure
     */
    move: (body: ExplorerServiceMoveRequest, params?: RequestParams) =>
      this.request<BulkActionResult, any>(`/explorer/v2/move`, "PUT", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags explorer - v2
     * @name Rename
     * @summary Rename explorer items
     * @request PUT:/explorer/v2/rename
     * @secure
     */
    rename: (body: ExplorerServiceRenameRequest, params?: RequestParams) =>
      this.request<DocRef, any>(`/explorer/v2/rename`, "PUT", params, body, BodyType.Json, true),
  };
  export = {
    /**
     * No description
     *
     * @tags export - v1
     * @name Export
     * @request GET:/export/v1
     * @secure
     */
    export: (params?: RequestParams) => this.request<any, any>(`/export/v1`, "GET", params, null, BodyType.Json, true),
  };
  feed = {
    /**
     * No description
     *
     * @tags feed - v1
     * @name FetchSupportedEncodings
     * @summary Fetch supported encodings
     * @request GET:/feed/v1/fetchSupportedEncodings
     * @secure
     */
    fetchSupportedEncodings: (params?: RequestParams) =>
      this.request<object[], any>(`/feed/v1/fetchSupportedEncodings`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags feed - v1
     * @name Read
     * @summary Get a feed doc
     * @request POST:/feed/v1/read
     * @secure
     */
    read: (body: DocRef, params?: RequestParams) =>
      this.request<FeedDoc, any>(`/feed/v1/read`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags feed - v1
     * @name Update
     * @summary Update a feed doc
     * @request PUT:/feed/v1/update
     * @secure
     */
    update: (body: FeedDoc, params?: RequestParams) =>
      this.request<FeedDoc, any>(`/feed/v1/update`, "PUT", params, body, BodyType.Json, true),
  };
  feedStatus = {
    /**
     * No description
     *
     * @tags feedStatus - v1
     * @name GetFeedStatus
     * @summary Submit a request to get the status of a feed
     * @request POST:/feedStatus/v1/getFeedStatus
     * @secure
     */
    getFeedStatus: (body: GetFeedStatusRequest, params?: RequestParams) =>
      this.request<GetFeedStatusResponse, any>(
        `/feedStatus/v1/getFeedStatus`,
        "POST",
        params,
        body,
        BodyType.Json,
        true,
      ),
  };
  fsVolume = {
    /**
     * No description
     *
     * @tags fsVolume - v1
     * @name Create
     * @request POST:/fsVolume/v1
     * @secure
     */
    create: (params?: RequestParams) =>
      this.request<FsVolume, any>(`/fsVolume/v1`, "POST", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags fsVolume - v1
     * @name Find
     * @summary Finds volumes
     * @request POST:/fsVolume/v1/find
     * @secure
     */
    find: (body: FindFsVolumeCriteria, params?: RequestParams) =>
      this.request<object[], any>(`/fsVolume/v1/find`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags fsVolume - v1
     * @name Rescan
     * @summary Rescans volumes
     * @request GET:/fsVolume/v1/rescan
     * @secure
     */
    rescan: (params?: RequestParams) =>
      this.request<boolean, any>(`/fsVolume/v1/rescan`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags fsVolume - v1
     * @name Read
     * @summary Get a volume
     * @request GET:/fsVolume/v1/{id}
     * @secure
     */
    read: (id: number, params?: RequestParams) =>
      this.request<FsVolume, any>(`/fsVolume/v1/${id}`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags fsVolume - v1
     * @name Update
     * @summary Update a volume
     * @request PUT:/fsVolume/v1/{id}
     * @secure
     */
    update: (id: number, body: FsVolume, params?: RequestParams) =>
      this.request<FsVolume, any>(`/fsVolume/v1/${id}`, "PUT", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags fsVolume - v1
     * @name Delete
     * @summary Delete a volume
     * @request DELETE:/fsVolume/v1/{id}
     * @secure
     */
    delete: (id: number, params?: RequestParams) =>
      this.request<boolean, any>(`/fsVolume/v1/${id}`, "DELETE", params, null, BodyType.Json, true),
  };
  index = {
    /**
     * No description
     *
     * @tags index - v1
     * @name ExportDocument
     * @summary Submit an export request
     * @request POST:/index/v1/export
     * @secure
     */
    exportDocument: (body: DocRef, params?: RequestParams) =>
      this.request<Base64EncodedDocumentData, any>(`/index/v1/export`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags index - v1
     * @name ImportDocument
     * @summary Submit an import request
     * @request POST:/index/v1/import
     * @secure
     */
    importDocument: (body: Base64EncodedDocumentData, params?: RequestParams) =>
      this.request<DocRef, any>(`/index/v1/import`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags index - v1
     * @name ListDocuments
     * @summary Submit a request for a list of doc refs held by this service
     * @request GET:/index/v1/list
     * @secure
     */
    listDocuments: (params?: RequestParams) =>
      this.request<object[], any>(`/index/v1/list`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags index - v1
     * @name Fetch
     * @request GET:/index/v1/{indexUuid}
     * @secure
     */
    fetch: (indexUuid: string, params?: RequestParams) =>
      this.request<any, any>(`/index/v1/${indexUuid}`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags index - v1
     * @name Save
     * @request POST:/index/v1/{indexUuid}
     * @secure
     */
    save: (indexUuid: string, params?: RequestParams) =>
      this.request<any, any>(`/index/v1/${indexUuid}`, "POST", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags index - v2
     * @name Read
     * @summary Get an index doc
     * @request POST:/index/v2/read
     * @secure
     */
    read: (body: DocRef, params?: RequestParams) =>
      this.request<IndexDoc, any>(`/index/v2/read`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags index - v2
     * @name DeleteIndexShards
     * @summary Delete matching index shards
     * @request POST:/index/v2/shard/delete
     * @secure
     */
    deleteIndexShards: (body: FindIndexShardCriteria, query?: { nodeName?: string }, params?: RequestParams) =>
      this.request<number, any>(
        `/index/v2/shard/delete${this.addQueryParams(query)}`,
        "POST",
        params,
        body,
        BodyType.Json,
        true,
      ),

    /**
     * No description
     *
     * @tags index - v2
     * @name FindIndexShards
     * @summary Find matching index shards
     * @request POST:/index/v2/shard/find
     * @secure
     */
    findIndexShards: (body: FindIndexShardCriteria, params?: RequestParams) =>
      this.request<ResultPage, any>(`/index/v2/shard/find`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags index - v2
     * @name FlushIndexShards
     * @summary Flush matching index shards
     * @request POST:/index/v2/shard/flush
     * @secure
     */
    flushIndexShards: (body: FindIndexShardCriteria, query?: { nodeName?: string }, params?: RequestParams) =>
      this.request<number, any>(
        `/index/v2/shard/flush${this.addQueryParams(query)}`,
        "POST",
        params,
        body,
        BodyType.Json,
        true,
      ),

    /**
     * No description
     *
     * @tags index - v2
     * @name Update
     * @summary Update an index doc
     * @request PUT:/index/v2/update
     * @secure
     */
    update: (params?: RequestParams) =>
      this.request<IndexDoc, any>(`/index/v2/update`, "PUT", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags index volume - v2
     * @name Create
     * @summary Creates an index volume
     * @request POST:/index/volume/v2
     * @secure
     */
    create: (body: IndexVolume, params?: RequestParams) =>
      this.request<IndexVolume, any>(`/index/volume/v2`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags index volume - v2
     * @name Find
     * @summary Finds index volumes matching request
     * @request POST:/index/volume/v2/find
     * @secure
     */
    find: (body: ExpressionCriteria, params?: RequestParams) =>
      this.request<ResultPage, any>(`/index/volume/v2/find`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags index volume - v2
     * @name Rescan
     * @summary Rescans index volumes
     * @request DELETE:/index/volume/v2/rescan
     * @secure
     */
    rescan: (query?: { nodeName?: string }, params?: RequestParams) =>
      this.request<boolean, any>(
        `/index/volume/v2/rescan${this.addQueryParams(query)}`,
        "DELETE",
        params,
        null,
        BodyType.Json,
        true,
      ),

    /**
     * No description
     *
     * @tags index volume - v2
     * @name Read2
     * @summary Gets an index volume
     * @request GET:/index/volume/v2/{id}
     * @originalName read
     * @duplicate
     * @secure
     */
    read2: (id: number, params?: RequestParams) =>
      this.request<IndexVolume, any>(`/index/volume/v2/${id}`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags index volume - v2
     * @name Update2
     * @summary Updates an index volume
     * @request PUT:/index/volume/v2/{id}
     * @originalName update
     * @duplicate
     * @secure
     */
    update2: (id: number, body: IndexVolume, params?: RequestParams) =>
      this.request<IndexVolume, any>(`/index/volume/v2/${id}`, "PUT", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags index volume - v2
     * @name Delete
     * @summary Deletes an index volume
     * @request DELETE:/index/volume/v2/{id}
     * @secure
     */
    delete: (id: number, params?: RequestParams) =>
      this.request<boolean, any>(`/index/volume/v2/${id}`, "DELETE", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags index volumeGroup - v2
     * @name Create2
     * @summary Creates an index volume group
     * @request POST:/index/volumeGroup/v2
     * @originalName create
     * @duplicate
     * @secure
     */
    create2: (body: string, params?: RequestParams) =>
      this.request<IndexVolumeGroup, any>(`/index/volumeGroup/v2`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags index volumeGroup - v2
     * @name Find2
     * @summary Finds index volume groups matching request
     * @request POST:/index/volumeGroup/v2/find
     * @originalName find
     * @duplicate
     * @secure
     */
    find2: (body: ExpressionCriteria, params?: RequestParams) =>
      this.request<ResultPage, any>(`/index/volumeGroup/v2/find`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags index volumeGroup - v2
     * @name Read3
     * @summary Gets an index volume group
     * @request GET:/index/volumeGroup/v2/{id}
     * @originalName read
     * @duplicate
     * @secure
     */
    read3: (id: number, params?: RequestParams) =>
      this.request<IndexVolumeGroup, any>(`/index/volumeGroup/v2/${id}`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags index volumeGroup - v2
     * @name Update3
     * @summary Updates an index volume group
     * @request PUT:/index/volumeGroup/v2/{id}
     * @originalName update
     * @duplicate
     * @secure
     */
    update3: (id: number, params?: RequestParams) =>
      this.request<IndexVolumeGroup, any>(`/index/volumeGroup/v2/${id}`, "PUT", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags index volumeGroup - v2
     * @name Delete2
     * @summary Deletes an index volume group
     * @request DELETE:/index/volumeGroup/v2/{id}
     * @originalName delete
     * @duplicate
     * @secure
     */
    delete2: (id: number, params?: RequestParams) =>
      this.request<boolean, any>(`/index/volumeGroup/v2/${id}`, "DELETE", params, null, BodyType.Json, true),
  };
  job = {
    /**
     * No description
     *
     * @tags job - v1
     * @name List
     * @summary Lists jobs
     * @request GET:/job/v1
     * @secure
     */
    list: (params?: RequestParams) =>
      this.request<ResultPage, any>(`/job/v1`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags job - v1
     * @name SetEnabled
     * @summary Sets the enabled status of the job
     * @request PUT:/job/v1/{id}/enabled
     * @secure
     */
    setEnabled: (id: number, body: boolean, params?: RequestParams) =>
      this.request<any, any>(`/job/v1/${id}/enabled`, "PUT", params, body, BodyType.Json, true),
  };
  jobNode = {
    /**
     * No description
     *
     * @tags jobNode - v1
     * @name List
     * @summary Lists job nodes
     * @request GET:/jobNode/v1
     * @secure
     */
    list: (query?: { jobName?: string; nodeName?: string }, params?: RequestParams) =>
      this.request<ResultPage, any>(
        `/jobNode/v1${this.addQueryParams(query)}`,
        "GET",
        params,
        null,
        BodyType.Json,
        true,
      ),

    /**
     * No description
     *
     * @tags jobNode - v1
     * @name Info
     * @summary Gets current info for a job node
     * @request GET:/jobNode/v1/info
     * @secure
     */
    info: (query?: { jobName?: string; nodeName?: string }, params?: RequestParams) =>
      this.request<JobNodeInfo, any>(
        `/jobNode/v1/info${this.addQueryParams(query)}`,
        "GET",
        params,
        null,
        BodyType.Json,
        true,
      ),

    /**
     * No description
     *
     * @tags jobNode - v1
     * @name SetEnabled
     * @summary Sets the enabled status of the job node
     * @request PUT:/jobNode/v1/{id}/enabled
     * @secure
     */
    setEnabled: (id: number, body: boolean, params?: RequestParams) =>
      this.request<any, any>(`/jobNode/v1/${id}/enabled`, "PUT", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags jobNode - v1
     * @name SetSchedule
     * @summary Sets the schedule job node
     * @request PUT:/jobNode/v1/{id}/schedule
     * @secure
     */
    setSchedule: (id: number, body: string, params?: RequestParams) =>
      this.request<any, any>(`/jobNode/v1/${id}/schedule`, "PUT", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags jobNode - v1
     * @name SetTaskLimit
     * @summary Sets the task limit for the job node
     * @request PUT:/jobNode/v1/{id}/taskLimit
     * @secure
     */
    setTaskLimit: (id: number, body: number, params?: RequestParams) =>
      this.request<any, any>(`/jobNode/v1/${id}/taskLimit`, "PUT", params, body, BodyType.Json, true),
  };
  kafkaConfig = {
    /**
     * No description
     *
     * @tags kafkaConfig - v1
     * @name Download
     * @summary Download a kafkaConfig doc
     * @request POST:/kafkaConfig/v1/download
     * @secure
     */
    download: (body: DocRef, params?: RequestParams) =>
      this.request<ResourceGeneration, any>(`/kafkaConfig/v1/download`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags kafkaConfig - v1
     * @name Read
     * @summary Get a kafkaConfig doc
     * @request POST:/kafkaConfig/v1/read
     * @secure
     */
    read: (body: DocRef, params?: RequestParams) =>
      this.request<KafkaConfigDoc, any>(`/kafkaConfig/v1/read`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags kafkaConfig - v1
     * @name Update
     * @summary Update a kafkaConfig doc
     * @request PUT:/kafkaConfig/v1/update
     * @secure
     */
    update: (body: KafkaConfigDoc, params?: RequestParams) =>
      this.request<KafkaConfigDoc, any>(`/kafkaConfig/v1/update`, "PUT", params, body, BodyType.Json, true),
  };
  meta = {
    /**
     * No description
     *
     * @tags meta - v1
     * @name FindMetaRow
     * @summary Find matching meta data
     * @request POST:/meta/v1/find
     * @secure
     */
    findMetaRow: (body: FindMetaCriteria, params?: RequestParams) =>
      this.request<ResourceGeneration, any>(`/meta/v1/find`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags meta - v1
     * @name GetReprocessSelectionSummary
     * @summary Get a summary of the parent items of the selected meta data
     * @request POST:/meta/v1/getReprocessSelectionSummary
     * @secure
     */
    getReprocessSelectionSummary: (body: FindMetaCriteria, params?: RequestParams) =>
      this.request<ResourceGeneration, any>(
        `/meta/v1/getReprocessSelectionSummary`,
        "POST",
        params,
        body,
        BodyType.Json,
        true,
      ),

    /**
     * No description
     *
     * @tags meta - v1
     * @name GetSelectionSummary
     * @summary Get a summary of the selected meta data
     * @request POST:/meta/v1/getSelectionSummary
     * @secure
     */
    getSelectionSummary: (body: FindMetaCriteria, params?: RequestParams) =>
      this.request<ResourceGeneration, any>(`/meta/v1/getSelectionSummary`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags meta - v1
     * @name GetTypes
     * @summary Get a list of possible stream types
     * @request GET:/meta/v1/getTypes
     * @secure
     */
    getTypes: (params?: RequestParams) =>
      this.request<object[], any>(`/meta/v1/getTypes`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags meta - v1
     * @name UpdateStatus
     * @summary Update status on matching meta data
     * @request PUT:/meta/v1/update/status
     * @secure
     */
    updateStatus: (params?: RequestParams) =>
      this.request<number, any>(`/meta/v1/update/status`, "PUT", params, null, BodyType.Json, true),
  };
  node = {
    /**
     * No description
     *
     * @tags node - v1
     * @name Find
     * @summary Lists nodes
     * @request GET:/node/v1
     * @secure
     */
    find: (params?: RequestParams) =>
      this.request<FetchNodeStatusResponse, any>(`/node/v1`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags node - v1
     * @name ListAllNodes
     * @summary Lists all nodes
     * @request GET:/node/v1/all
     * @secure
     */
    listAllNodes: (params?: RequestParams) =>
      this.request<object[], any>(`/node/v1/all`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags node - v1
     * @name ListEnabledNodes
     * @summary Lists enabled nodes
     * @request GET:/node/v1/enabled
     * @secure
     */
    listEnabledNodes: (params?: RequestParams) =>
      this.request<object[], any>(`/node/v1/enabled`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags node - v1
     * @name SetEnabled
     * @summary Sets whether a node is enabled
     * @request PUT:/node/v1/enabled/{nodeName}
     * @secure
     */
    setEnabled: (nodeName: string, body: boolean, params?: RequestParams) =>
      this.request<any, any>(`/node/v1/enabled/${nodeName}`, "PUT", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags node - v1
     * @name Info
     * @summary Gets detailed information about a node
     * @request GET:/node/v1/info/{nodeName}
     * @secure
     */
    info: (nodeName: string, params?: RequestParams) =>
      this.request<number, any>(`/node/v1/info/${nodeName}`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags node - v1
     * @name Ping
     * @summary Gets a ping time for a node
     * @request GET:/node/v1/ping/{nodeName}
     * @secure
     */
    ping: (nodeName: string, params?: RequestParams) =>
      this.request<number, any>(`/node/v1/ping/${nodeName}`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags node - v1
     * @name SetPriority
     * @summary Sets the priority of a node
     * @request PUT:/node/v1/priority/{nodeName}
     * @secure
     */
    setPriority: (nodeName: string, body: number, params?: RequestParams) =>
      this.request<any, any>(`/node/v1/priority/${nodeName}`, "PUT", params, body, BodyType.Json, true),
  };
  oauth2 = {
    /**
     * No description
     *
     * @tags ApiKey
     * @name OpenIdConfiguration
     * @summary Provides discovery for openid configuration
     * @request GET:/oauth2/v1/noauth/.well-known/openid-configuration
     * @secure
     */
    openIdConfiguration: (params?: RequestParams) =>
      this.request<string, any>(
        `/oauth2/v1/noauth/.well-known/openid-configuration`,
        "GET",
        params,
        null,
        BodyType.Json,
        true,
      ),

    /**
     * No description
     *
     * @tags Authentication
     * @name Auth
     * @summary Submit an OpenId AuthenticationRequest.
     * @request GET:/oauth2/v1/noauth/auth
     * @secure
     */
    auth: (
      query: {
        client_id: string;
        nonce?: string;
        prompt?: string;
        redirect_uri: string;
        response_type: string;
        scope: string;
        state?: string;
      },
      params?: RequestParams,
    ) =>
      this.request<string, any>(
        `/oauth2/v1/noauth/auth${this.addQueryParams(query)}`,
        "GET",
        params,
        null,
        BodyType.Json,
        true,
      ),

    /**
     * No description
     *
     * @tags ApiKey
     * @name Certs
     * @summary Provides access to this service's current public key. A client may use these keys to verify JWTs issued by this service.
     * @request GET:/oauth2/v1/noauth/certs
     * @secure
     */
    certs: (params?: RequestParams) =>
      this.request<string, any>(`/oauth2/v1/noauth/certs`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags Authentication
     * @name Token
     * @summary Get a token from an access code
     * @request POST:/oauth2/v1/noauth/token
     * @secure
     */
    token: (body: TokenRequest, params?: RequestParams) =>
      this.request<string, any>(`/oauth2/v1/noauth/token`, "POST", params, body, BodyType.Json, true),
  };
  permission = {
    /**
     * No description
     *
     * @tags application permissions - v1
     * @name GetUserAndPermissions
     * @summary User and app permissions for the current session
     * @request GET:/permission/app/v1
     * @secure
     */
    getUserAndPermissions: (params?: RequestParams) =>
      this.request<UserAndPermissions, any>(`/permission/app/v1`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags application permissions - v1
     * @name ChangeUser
     * @summary User and app permissions for the current session
     * @request POST:/permission/app/v1/changeUser
     * @secure
     */
    changeUser: (body: ChangeUserRequest, params?: RequestParams) =>
      this.request<boolean, any>(`/permission/app/v1/changeUser`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags application permissions - v1
     * @name FetchAllPermissions
     * @summary Get all possible permissions
     * @request GET:/permission/app/v1/fetchAllPermissions
     * @secure
     */
    fetchAllPermissions: (params?: RequestParams) =>
      this.request<object[], any>(`/permission/app/v1/fetchAllPermissions`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags application permissions - v1
     * @name FetchUserAppPermissions
     * @summary User and app permissions for the specified user
     * @request POST:/permission/app/v1/fetchUserAppPermissions
     * @secure
     */
    fetchUserAppPermissions: (body: User, params?: RequestParams) =>
      this.request<UserAndPermissions, any>(
        `/permission/app/v1/fetchUserAppPermissions`,
        "POST",
        params,
        body,
        BodyType.Json,
        true,
      ),

    /**
     * No description
     *
     * @tags application permissions - v1
     * @name FireChange
     * @summary Fires a permission change event
     * @request POST:/permission/changeEvent/v1/fireChange/{nodeName}
     * @secure
     */
    fireChange: (nodeName: string, body: PermissionChangeRequest, params?: RequestParams) =>
      this.request<boolean, any>(
        `/permission/changeEvent/v1/fireChange/${nodeName}`,
        "POST",
        params,
        body,
        BodyType.Json,
        true,
      ),

    /**
     * No description
     *
     * @tags application permissions - v1
     * @name ChangeDocumentPermissions
     * @summary Change document permissions
     * @request POST:/permission/doc/v1/changeDocumentPermissions
     * @secure
     */
    changeDocumentPermissions: (body: ChangeDocumentPermissionsRequest, params?: RequestParams) =>
      this.request<boolean, any>(
        `/permission/doc/v1/changeDocumentPermissions`,
        "POST",
        params,
        body,
        BodyType.Json,
        true,
      ),

    /**
     * No description
     *
     * @tags application permissions - v1
     * @name CheckDocumentPermission
     * @summary Check document permission
     * @request POST:/permission/doc/v1/checkDocumentPermission
     * @secure
     */
    checkDocumentPermission: (body: CheckDocumentPermissionRequest, params?: RequestParams) =>
      this.request<boolean, any>(
        `/permission/doc/v1/checkDocumentPermission`,
        "POST",
        params,
        body,
        BodyType.Json,
        true,
      ),

    /**
     * No description
     *
     * @tags application permissions - v1
     * @name CopyPermissionFromParent
     * @summary Copy permissions from parent
     * @request POST:/permission/doc/v1/copyPermissionsFromParent
     * @secure
     */
    copyPermissionFromParent: (body: CopyPermissionsFromParentRequest, params?: RequestParams) =>
      this.request<DocumentPermissions, any>(
        `/permission/doc/v1/copyPermissionsFromParent`,
        "POST",
        params,
        body,
        BodyType.Json,
        true,
      ),

    /**
     * No description
     *
     * @tags application permissions - v1
     * @name FetchAllDocumentPermissions
     * @summary Fetch document permissions
     * @request POST:/permission/doc/v1/fetchAllDocumentPermissions
     * @secure
     */
    fetchAllDocumentPermissions: (body: FetchAllDocumentPermissionsRequest, params?: RequestParams) =>
      this.request<DocumentPermissions, any>(
        `/permission/doc/v1/fetchAllDocumentPermissions`,
        "POST",
        params,
        body,
        BodyType.Json,
        true,
      ),

    /**
     * No description
     *
     * @tags application permissions - v1
     * @name FilterUsers
     * @summary Get all permissions for a given document type
     * @request POST:/permission/doc/v1/filterUsers
     * @secure
     */
    filterUsers: (params?: RequestParams) =>
      this.request<object[], any>(`/permission/doc/v1/filterUsers`, "POST", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags application permissions - v1
     * @name GetPermissionForDocType
     * @summary Get all permissions for a given document type
     * @request GET:/permission/doc/v1/getPermissionForDocType/${docType}
     * @secure
     */
    getPermissionForDocType: (docType: string, params?: RequestParams) =>
      this.request<object[], any>(
        `/permission/doc/v1/getPermissionForDocType/$${docType}`,
        "GET",
        params,
        null,
        BodyType.Json,
        true,
      ),
  };
  pipeline = {
    /**
     * No description
     *
     * @tags pipeline - v1
     * @name FetchPipelineData
     * @summary Fetch data for a pipeline
     * @request POST:/pipeline/v1/fetchPipelineData
     * @secure
     */
    fetchPipelineData: (body: DocRef, params?: RequestParams) =>
      this.request<object[], any>(`/pipeline/v1/fetchPipelineData`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags pipeline - v1
     * @name FetchPipelineXml
     * @summary Fetch the XML for a pipeline
     * @request POST:/pipeline/v1/fetchPipelineXml
     * @secure
     */
    fetchPipelineXml: (body: DocRef, params?: RequestParams) =>
      this.request<FetchPipelineXmlResponse, any>(
        `/pipeline/v1/fetchPipelineXml`,
        "POST",
        params,
        body,
        BodyType.Json,
        true,
      ),

    /**
     * No description
     *
     * @tags pipeline - v1
     * @name GetPropertyTypes
     * @summary Get pipeline property types
     * @request GET:/pipeline/v1/propertyTypes
     * @secure
     */
    getPropertyTypes: (params?: RequestParams) =>
      this.request<object[], any>(`/pipeline/v1/propertyTypes`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags pipeline - v1
     * @name Read
     * @summary Get a pipeline doc
     * @request POST:/pipeline/v1/read
     * @secure
     */
    read: (body: DocRef, params?: RequestParams) =>
      this.request<PipelineDoc, any>(`/pipeline/v1/read`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags pipeline - v1
     * @name SavePipelineXml
     * @summary Update a pipeline doc with XML directly
     * @request PUT:/pipeline/v1/savePipelineXml
     * @secure
     */
    savePipelineXml: (body: SavePipelineXmlRequest, params?: RequestParams) =>
      this.request<boolean, any>(`/pipeline/v1/savePipelineXml`, "PUT", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags pipeline - v1
     * @name Update
     * @summary Update a pipeline doc
     * @request PUT:/pipeline/v1/update
     * @secure
     */
    update: (body: PipelineDoc, params?: RequestParams) =>
      this.request<PipelineDoc, any>(`/pipeline/v1/update`, "PUT", params, body, BodyType.Json, true),
  };
  pipelines = {
    /**
     * No description
     *
     * @tags pipeline - v1
     * @name Search
     * @request GET:/pipelines/v1
     * @secure
     */
    search: (query?: { filter?: string; offset?: number; pageSize?: number }, params?: RequestParams) =>
      this.request<any, any>(`/pipelines/v1${this.addQueryParams(query)}`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags pipeline - v1
     * @name CreateInherited
     * @request POST:/pipelines/v1/{parentPipelineId}/inherit
     * @secure
     */
    createInherited: (parentPipelineId: string, body: DocRef, params?: RequestParams) =>
      this.request<any, any>(`/pipelines/v1/${parentPipelineId}/inherit`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags pipeline - v1
     * @name Fetch
     * @request GET:/pipelines/v1/{pipelineId}
     * @secure
     */
    fetch: (pipelineId: string, params?: RequestParams) =>
      this.request<any, any>(`/pipelines/v1/${pipelineId}`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags pipeline - v1
     * @name Save
     * @request POST:/pipelines/v1/{pipelineId}
     * @secure
     */
    save: (pipelineId: string, body: PipelineDTO, params?: RequestParams) =>
      this.request<any, any>(`/pipelines/v1/${pipelineId}`, "POST", params, body, BodyType.Json, true),
  };
  processor = {
    /**
     * No description
     *
     * @tags processor - v1
     * @name Delete
     * @summary Deletes a processor
     * @request DELETE:/processor/v1/{id}
     * @secure
     */
    delete: (id: number, params?: RequestParams) =>
      this.request<Processor, any>(`/processor/v1/${id}`, "DELETE", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags processor - v1
     * @name SetEnabled
     * @summary Sets the enabled/disabled state for a processor
     * @request PUT:/processor/v1/{id}/enabled
     * @secure
     */
    setEnabled: (id: number, body: boolean, params?: RequestParams) =>
      this.request<any, any>(`/processor/v1/${id}/enabled`, "PUT", params, body, BodyType.Json, true),
  };
  processorFilter = {
    /**
     * No description
     *
     * @tags processorFilter - v1
     * @name Create
     * @summary Creates a filter
     * @request POST:/processorFilter/v1
     * @secure
     */
    create: (body: CreateProcessFilterRequest, params?: RequestParams) =>
      this.request<ProcessorFilter, any>(`/processorFilter/v1`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags processorFilter - v1
     * @name Find
     * @summary Finds processors and filters matching request
     * @request POST:/processorFilter/v1/find
     * @secure
     */
    find: (body: FetchProcessorRequest, params?: RequestParams) =>
      this.request<ResultPage, any>(`/processorFilter/v1/find`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags processorFilter - v1
     * @name Reprocess
     * @summary Create filters to reprocess data
     * @request POST:/processorFilter/v1/reprocess
     * @secure
     */
    reprocess: (body: CreateReprocessFilterRequest, params?: RequestParams) =>
      this.request<ReprocessDataInfo[], any>(
        `/processorFilter/v1/reprocess`,
        "POST",
        params,
        body,
        BodyType.Json,
        true,
      ),

    /**
     * No description
     *
     * @tags processorFilter - v1
     * @name Read
     * @summary Gets a filter
     * @request GET:/processorFilter/v1/{id}
     * @secure
     */
    read: (id: number, params?: RequestParams) =>
      this.request<ProcessorFilter, any>(`/processorFilter/v1/${id}`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags processorFilter - v1
     * @name Update
     * @summary Updates a filter
     * @request PUT:/processorFilter/v1/{id}
     * @secure
     */
    update: (id: number, params?: RequestParams) =>
      this.request<ProcessorFilter, any>(`/processorFilter/v1/${id}`, "PUT", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags processorFilter - v1
     * @name Delete
     * @summary Deletes a filter
     * @request DELETE:/processorFilter/v1/{id}
     * @secure
     */
    delete: (id: number, params?: RequestParams) =>
      this.request<ProcessorFilter, any>(`/processorFilter/v1/${id}`, "DELETE", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags processorFilter - v1
     * @name SetEnabled
     * @summary Sets the enabled/disabled state for a filter
     * @request PUT:/processorFilter/v1/{id}/enabled
     * @secure
     */
    setEnabled: (id: number, params?: RequestParams) =>
      this.request<any, any>(`/processorFilter/v1/${id}/enabled`, "PUT", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags processorFilter - v1
     * @name SetPriority
     * @summary Sets the priority for a filter
     * @request PUT:/processorFilter/v1/{id}/priority
     * @secure
     */
    setPriority: (id: number, params?: RequestParams) =>
      this.request<any, any>(`/processorFilter/v1/${id}/priority`, "PUT", params, null, BodyType.Json, true),
  };
  processorTask = {
    /**
     * No description
     *
     * @tags processorTask - v1
     * @name AbandonTasks
     * @summary Abandon some tasks
     * @request POST:/processorTask/v1/abandon/{nodeName}
     * @secure
     */
    abandonTasks: (nodeName: string, body: ProcessorTaskList, params?: RequestParams) =>
      this.request<boolean, any>(`/processorTask/v1/abandon/${nodeName}`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags processorTask - v1
     * @name AssignTasks
     * @summary Assign some tasks
     * @request POST:/processorTask/v1/assign/{nodeName}
     * @secure
     */
    assignTasks: (nodeName: string, body: AssignTasksRequest, params?: RequestParams) =>
      this.request<ProcessorTaskList, any>(
        `/processorTask/v1/assign/${nodeName}`,
        "POST",
        params,
        body,
        BodyType.Json,
        true,
      ),

    /**
     * No description
     *
     * @tags processorTask - v1
     * @name Find
     * @summary Finds processors tasks
     * @request POST:/processorTask/v1/find
     * @secure
     */
    find: (body: ExpressionCriteria, params?: RequestParams) =>
      this.request<ResultPage, any>(`/processorTask/v1/find`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags processorTask - v1
     * @name FindSummary
     * @summary Finds processor task summaries
     * @request POST:/processorTask/v1/summary
     * @secure
     */
    findSummary: (body: ExpressionCriteria, params?: RequestParams) =>
      this.request<ResultPage, any>(`/processorTask/v1/summary`, "POST", params, body, BodyType.Json, true),
  };
  refData = {
    /**
     * No description
     *
     * @tags reference data - v1
     * @name Entries
     * @request GET:/refData/v1/entries
     * @secure
     */
    entries: (query?: { limit?: number }, params?: RequestParams) =>
      this.request<RefStoreEntry[], any>(
        `/refData/v1/entries${this.addQueryParams(query)}`,
        "GET",
        params,
        null,
        BodyType.Json,
        true,
      ),

    /**
     * No description
     *
     * @tags reference data - v1
     * @name Lookup
     * @request POST:/refData/v1/lookup
     * @secure
     */
    lookup: (params?: RequestParams) =>
      this.request<string, any>(`/refData/v1/lookup`, "POST", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags reference data - v1
     * @name Purge
     * @request DELETE:/refData/v1/purge/{purgeAge}
     * @secure
     */
    purge: (purgeAge: string, params?: RequestParams) =>
      this.request<any, any>(`/refData/v1/purge/${purgeAge}`, "DELETE", params, null, BodyType.Json, true),
  };
  remoteSearch = {
    /**
     * No description
     *
     * @tags remoteSearch - v1
     * @name Destroy
     * @summary Destroy search results
     * @request GET:/remoteSearch/v1/destroy
     * @secure
     */
    destroy: (query?: { queryKey?: string }, params?: RequestParams) =>
      this.request<boolean, any>(
        `/remoteSearch/v1/destroy${this.addQueryParams(query)}`,
        "GET",
        params,
        null,
        BodyType.Json,
        true,
      ),

    /**
     * No description
     *
     * @tags remoteSearch - v1
     * @name Poll
     * @request GET:/remoteSearch/v1/poll
     * @secure
     */
    poll: (query?: { queryKey?: string }, params?: RequestParams) =>
      this.request<StreamingOutput, any>(
        `/remoteSearch/v1/poll${this.addQueryParams(query)}`,
        "GET",
        params,
        null,
        BodyType.Json,
        true,
      ),

    /**
     * No description
     *
     * @tags remoteSearch - v1
     * @name Start
     * @summary Start a search
     * @request POST:/remoteSearch/v1/start
     * @secure
     */
    start: (params?: RequestParams) =>
      this.request<boolean, any>(`/remoteSearch/v1/start`, "POST", params, null, BodyType.Json, true),
  };
  ruleset = {
    /**
     * No description
     *
     * @tags ruleset - v2
     * @name ExportDocument
     * @summary Submit an export request
     * @request POST:/ruleset/v2/export
     * @secure
     */
    exportDocument: (body: DocRef, params?: RequestParams) =>
      this.request<Base64EncodedDocumentData, any>(`/ruleset/v2/export`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags ruleset - v2
     * @name ImportDocument
     * @summary Submit an import request
     * @request POST:/ruleset/v2/import
     * @secure
     */
    importDocument: (body: Base64EncodedDocumentData, params?: RequestParams) =>
      this.request<DocRef, any>(`/ruleset/v2/import`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags ruleset - v2
     * @name ListDocuments
     * @summary Submit a request for a list of doc refs held by this service
     * @request GET:/ruleset/v2/list
     * @secure
     */
    listDocuments: (params?: RequestParams) =>
      this.request<object[], any>(`/ruleset/v2/list`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags ruleset - v2
     * @name Read
     * @summary Get a rules doc
     * @request POST:/ruleset/v2/read
     * @secure
     */
    read: (body: DocRef, params?: RequestParams) =>
      this.request<ReceiveDataRules, any>(`/ruleset/v2/read`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags ruleset - v2
     * @name Update
     * @summary Update a rules doc
     * @request PUT:/ruleset/v2/update
     * @secure
     */
    update: (body: ReceiveDataRules, params?: RequestParams) =>
      this.request<ReceiveDataRules, any>(`/ruleset/v2/update`, "PUT", params, body, BodyType.Json, true),
  };
  scheduledTime = {
    /**
     * No description
     *
     * @tags scheduledTime - v1
     * @name Get
     * @summary Gets scheduled time info
     * @request POST:/scheduledTime/v1
     * @secure
     */
    get: (body: GetScheduledTimesRequest, params?: RequestParams) =>
      this.request<ScheduledTimes, any>(`/scheduledTime/v1`, "POST", params, body, BodyType.Json, true),
  };
  script = {
    /**
     * No description
     *
     * @tags script - v1
     * @name FetchLinkedScripts
     * @summary Fetch related scripts
     * @request POST:/script/v1/fetchLinkedScripts
     * @secure
     */
    fetchLinkedScripts: (body: FetchLinkedScriptRequest, params?: RequestParams) =>
      this.request<ScriptDoc, any>(`/script/v1/fetchLinkedScripts`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags script - v1
     * @name Read
     * @summary Get a script doc
     * @request POST:/script/v1/read
     * @secure
     */
    read: (body: DocRef, params?: RequestParams) =>
      this.request<ScriptDoc, any>(`/script/v1/read`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags script - v1
     * @name Update
     * @summary Update a script doc
     * @request PUT:/script/v1/update
     * @secure
     */
    update: (body: ScriptDoc, params?: RequestParams) =>
      this.request<ScriptDoc, any>(`/script/v1/update`, "PUT", params, body, BodyType.Json, true),
  };
  searchable = {
    /**
     * No description
     *
     * @tags searchable - v2
     * @name GetDataSource
     * @summary Submit a request for a data source definition, supplying the DocRef for the data source
     * @request POST:/searchable/v2/dataSource
     * @secure
     */
    getDataSource: (body: DocRef, params?: RequestParams) =>
      this.request<DataSource, any>(`/searchable/v2/dataSource`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags searchable - v2
     * @name Destroy
     * @summary Destroy a running query
     * @request POST:/searchable/v2/destroy
     * @secure
     */
    destroy: (body: QueryKey, params?: RequestParams) =>
      this.request<boolean, any>(`/searchable/v2/destroy`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags searchable - v2
     * @name Search
     * @summary Submit a search request
     * @request POST:/searchable/v2/search
     * @secure
     */
    search: (body: SearchRequest, params?: RequestParams) =>
      this.request<SearchResponse, any>(`/searchable/v2/search`, "POST", params, body, BodyType.Json, true),
  };
  session = {
    /**
     * No description
     *
     * @tags session - v1
     * @name List
     * @summary Lists user sessions for a node, or all nodes in the cluster if nodeName is null
     * @request GET:/session/v1/list
     * @secure
     */
    list: (query?: { nodeName?: string }, params?: RequestParams) =>
      this.request<SessionDetails, any>(
        `/session/v1/list${this.addQueryParams(query)}`,
        "GET",
        params,
        null,
        BodyType.Json,
        true,
      ),

    /**
     * No description
     *
     * @tags session - v1
     * @name Logout
     * @summary Logs the specified session out of Stroom
     * @request GET:/session/v1/logout/{sessionId}
     * @secure
     */
    logout: (sessionId: string, params?: RequestParams) =>
      this.request<string, any>(`/session/v1/logout/${sessionId}`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags session - v1
     * @name Login
     * @summary Checks if the current session is authenticated and redirects to an auth flow if it is not
     * @request GET:/session/v1/noauth/login
     * @secure
     */
    login: (query?: { redirect_uri?: string }, params?: RequestParams) =>
      this.request<string, any>(
        `/session/v1/noauth/login${this.addQueryParams(query)}`,
        "GET",
        params,
        null,
        BodyType.Json,
        true,
      ),
  };
  sessionInfo = {
    /**
     * No description
     *
     * @tags sessionInfo - v1
     * @name Get
     * @request GET:/sessionInfo/v1
     * @secure
     */
    get: (params?: RequestParams) =>
      this.request<SessionInfo, any>(`/sessionInfo/v1`, "GET", params, null, BodyType.Json, true),
  };
  solr = {
    /**
     * @description Solr Index API
     *
     * @tags solr index - v1
     * @name ExportDocument
     * @summary Submit an export request
     * @request POST:/solr/index/v1/export
     * @secure
     */
    exportDocument: (body: DocRef, params?: RequestParams) =>
      this.request<Base64EncodedDocumentData, any>(`/solr/index/v1/export`, "POST", params, body, BodyType.Json, true),

    /**
     * @description Solr Index API
     *
     * @tags solr index - v1
     * @name ImportDocument
     * @summary Submit an import request
     * @request POST:/solr/index/v1/import
     * @secure
     */
    importDocument: (body: Base64EncodedDocumentData, params?: RequestParams) =>
      this.request<DocRef, any>(`/solr/index/v1/import`, "POST", params, body, BodyType.Json, true),

    /**
     * @description Solr Index API
     *
     * @tags solr index - v1
     * @name ListDocuments
     * @summary Submit a request for a list of doc refs held by this service
     * @request GET:/solr/index/v1/list
     * @secure
     */
    listDocuments: (params?: RequestParams) =>
      this.request<object[], any>(`/solr/index/v1/list`, "GET", params, null, BodyType.Json, true),
  };
  solrIndex = {
    /**
     * No description
     *
     * @tags solrIndex - v1
     * @name FetchSolrTypes
     * @summary Fetch Solr types
     * @request POST:/solrIndex/v1/fetchSolrTypes
     * @secure
     */
    fetchSolrTypes: (body: SolrIndexDoc, params?: RequestParams) =>
      this.request<object[], any>(`/solrIndex/v1/fetchSolrTypes`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags solrIndex - v1
     * @name Read
     * @summary Get a solr index doc
     * @request POST:/solrIndex/v1/read
     * @secure
     */
    read: (body: DocRef, params?: RequestParams) =>
      this.request<SolrIndexDoc, any>(`/solrIndex/v1/read`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags solrIndex - v1
     * @name SolrConnectionTest
     * @summary Test connection to Solr
     * @request POST:/solrIndex/v1/solrConnectionTest
     * @secure
     */
    solrConnectionTest: (body: SolrIndexDoc, params?: RequestParams) =>
      this.request<string, any>(`/solrIndex/v1/solrConnectionTest`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags solrIndex - v1
     * @name Update
     * @summary Update a solr index doc
     * @request PUT:/solrIndex/v1/update
     * @secure
     */
    update: (body: SolrIndexDoc, params?: RequestParams) =>
      this.request<SolrIndexDoc, any>(`/solrIndex/v1/update`, "PUT", params, body, BodyType.Json, true),
  };
  sqlstatistics = {
    /**
     * No description
     *
     * @tags sqlstatistics query - v2
     * @name GetDataSource
     * @summary Submit a request for a data source definition, supplying the DocRef for the data source
     * @request POST:/sqlstatistics/v2/dataSource
     * @secure
     */
    getDataSource: (body: DocRef, params?: RequestParams) =>
      this.request<DataSource, any>(`/sqlstatistics/v2/dataSource`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags sqlstatistics query - v2
     * @name Destroy
     * @summary Destroy a running query
     * @request POST:/sqlstatistics/v2/destroy
     * @secure
     */
    destroy: (body: QueryKey, params?: RequestParams) =>
      this.request<boolean, any>(`/sqlstatistics/v2/destroy`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags sqlstatistics query - v2
     * @name Search
     * @summary Submit a search request
     * @request POST:/sqlstatistics/v2/search
     * @secure
     */
    search: (body: SearchRequest, params?: RequestParams) =>
      this.request<SearchResponse, any>(`/sqlstatistics/v2/search`, "POST", params, body, BodyType.Json, true),
  };
  statistic = {
    /**
     * No description
     *
     * @tags statisticrollUp - v1
     * @name BitMaskConversion
     * @summary Get rollup bit mask
     * @request POST:/statistic/rollUp/v1/bitMaskConversion
     * @secure
     */
    bitMaskConversion: (body: number[], params?: RequestParams) =>
      this.request<object[], any>(`/statistic/rollUp/v1/bitMaskConversion`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags statisticrollUp - v1
     * @name BitMaskPermGeneration
     * @summary Create rollup bit mask
     * @request POST:/statistic/rollUp/v1/bitMaskPermGeneration
     * @secure
     */
    bitMaskPermGeneration: (body: number, params?: RequestParams) =>
      this.request<object[], any>(
        `/statistic/rollUp/v1/bitMaskPermGeneration`,
        "POST",
        params,
        body,
        BodyType.Json,
        true,
      ),

    /**
     * No description
     *
     * @tags statisticrollUp - v1
     * @name FieldChange
     * @summary Change fields
     * @request POST:/statistic/rollUp/v1/dataSourceFieldChange
     * @secure
     */
    fieldChange: (body: StatisticsDataSourceFieldChangeRequest, params?: RequestParams) =>
      this.request<StatisticsDataSourceData, any>(
        `/statistic/rollUp/v1/dataSourceFieldChange`,
        "POST",
        params,
        body,
        BodyType.Json,
        true,
      ),

    /**
     * No description
     *
     * @tags statistic - v1
     * @name Read
     * @summary Get a statistic doc
     * @request POST:/statistic/v1/read
     * @secure
     */
    read: (body: DocRef, params?: RequestParams) =>
      this.request<XsltDoc, any>(`/statistic/v1/read`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags statistic - v1
     * @name Update
     * @summary Update a statistic doc
     * @request PUT:/statistic/v1/update
     * @secure
     */
    update: (body: StatisticStoreDoc, params?: RequestParams) =>
      this.request<StatisticStoreDoc, any>(`/statistic/v1/update`, "PUT", params, body, BodyType.Json, true),
  };
  statsStore = {
    /**
     * No description
     *
     * @tags statsStorerollUp - v1
     * @name BitMaskConversion
     * @summary Get rollup bit mask
     * @request POST:/statsStore/rollUp/v1/bitMaskConversion
     * @secure
     */
    bitMaskConversion: (body: number[], params?: RequestParams) =>
      this.request<object[], any>(`/statsStore/rollUp/v1/bitMaskConversion`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags statsStorerollUp - v1
     * @name BitMaskPermGeneration
     * @summary Create rollup bit mask
     * @request POST:/statsStore/rollUp/v1/bitMaskPermGeneration
     * @secure
     */
    bitMaskPermGeneration: (body: number, params?: RequestParams) =>
      this.request<object[], any>(
        `/statsStore/rollUp/v1/bitMaskPermGeneration`,
        "POST",
        params,
        body,
        BodyType.Json,
        true,
      ),

    /**
     * No description
     *
     * @tags statsStorerollUp - v1
     * @name FieldChange
     * @summary Change fields
     * @request POST:/statsStore/rollUp/v1/dataSourceFieldChange
     * @secure
     */
    fieldChange: (body: StroomStatsStoreFieldChangeRequest, params?: RequestParams) =>
      this.request<StroomStatsStoreEntityData, any>(
        `/statsStore/rollUp/v1/dataSourceFieldChange`,
        "POST",
        params,
        body,
        BodyType.Json,
        true,
      ),

    /**
     * No description
     *
     * @tags statsStore - v1
     * @name Read
     * @summary Get a stats store doc
     * @request POST:/statsStore/v1/read
     * @secure
     */
    read: (body: DocRef, params?: RequestParams) =>
      this.request<DictionaryDoc, any>(`/statsStore/v1/read`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags statsStore - v1
     * @name Update
     * @summary Update a stats store doc
     * @request PUT:/statsStore/v1/update
     * @secure
     */
    update: (body: StroomStatsStoreDoc, params?: RequestParams) =>
      this.request<StroomStatsStoreDoc, any>(`/statsStore/v1/update`, "PUT", params, body, BodyType.Json, true),
  };
  stepping = {
    /**
     * No description
     *
     * @tags stepping - v1
     * @name FindElementDoc
     * @summary Load the document for an element
     * @request POST:/stepping/v1/findElementDoc
     * @secure
     */
    findElementDoc: (params?: RequestParams) =>
      this.request<Doc, any>(`/stepping/v1/findElementDoc`, "POST", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags stepping - v1
     * @name GetPipelineForStepping
     * @summary Get a pipeline for stepping
     * @request POST:/stepping/v1/getPipelineForStepping
     * @secure
     */
    getPipelineForStepping: (body: GetPipelineForMetaRequest, params?: RequestParams) =>
      this.request<DocRef, any>(`/stepping/v1/getPipelineForStepping`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags stepping - v1
     * @name Step
     * @summary Step a pipeline
     * @request POST:/stepping/v1/step
     * @secure
     */
    step: (body: PipelineStepRequest, params?: RequestParams) =>
      this.request<SteppingResult, any>(`/stepping/v1/step`, "POST", params, body, BodyType.Json, true),
  };
  storedQuery = {
    /**
     * No description
     *
     * @tags storedQuery - v1
     * @name Create
     * @summary Create a stored query
     * @request POST:/storedQuery/v1/create
     * @secure
     */
    create: (body: StoredQuery, params?: RequestParams) =>
      this.request<StoredQuery, any>(`/storedQuery/v1/create`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags storedQuery - v1
     * @name Delete
     * @summary Delete a stored query
     * @request DELETE:/storedQuery/v1/delete
     * @secure
     */
    delete: (params?: RequestParams) =>
      this.request<StoredQuery, any>(`/storedQuery/v1/delete`, "DELETE", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags storedQuery - v1
     * @name Find
     * @summary Find stored queries
     * @request POST:/storedQuery/v1/find
     * @secure
     */
    find: (body: FindStoredQueryCriteria, params?: RequestParams) =>
      this.request<ResultPage, any>(`/storedQuery/v1/find`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags storedQuery - v1
     * @name Read
     * @summary Get a stored query
     * @request POST:/storedQuery/v1/read
     * @secure
     */
    read: (body: StoredQuery, params?: RequestParams) =>
      this.request<StoredQuery, any>(`/storedQuery/v1/read`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags storedQuery - v1
     * @name Update
     * @summary Update a stored query
     * @request PUT:/storedQuery/v1/update
     * @secure
     */
    update: (params?: RequestParams) =>
      this.request<StoredQuery, any>(`/storedQuery/v1/update`, "PUT", params, null, BodyType.Json, true),
  };
  streamattributemap = {
    /**
     * No description
     *
     * @tags stream attribute map - v1
     * @name Page
     * @request GET:/streamattributemap/v1
     * @secure
     */
    page: (query?: { pageOffset?: number; pageSize?: number }, params?: RequestParams) =>
      this.request<any, any>(
        `/streamattributemap/v1${this.addQueryParams(query)}`,
        "GET",
        params,
        null,
        BodyType.Json,
        true,
      ),

    /**
     * No description
     *
     * @tags stream attribute map - v1
     * @name Search
     * @request POST:/streamattributemap/v1
     * @secure
     */
    search: (body: ExpressionOperator, query?: { pageOffset?: number; pageSize?: number }, params?: RequestParams) =>
      this.request<any, any>(
        `/streamattributemap/v1${this.addQueryParams(query)}`,
        "POST",
        params,
        body,
        BodyType.Json,
        true,
      ),

    /**
     * No description
     *
     * @tags stream attribute map - v1
     * @name DataSource
     * @request GET:/streamattributemap/v1/dataSource
     * @secure
     */
    dataSource: (params?: RequestParams) =>
      this.request<any, any>(`/streamattributemap/v1/dataSource`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags stream attribute map - v1
     * @name Search2
     * @request GET:/streamattributemap/v1/{id}
     * @originalName search
     * @duplicate
     * @secure
     */
    search2: (id: number, params?: RequestParams) =>
      this.request<any, any>(`/streamattributemap/v1/${id}`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags stream attribute map - v1
     * @name GetRelations
     * @request GET:/streamattributemap/v1/{id}/{anyStatus}/relations
     * @secure
     */
    getRelations: (anyStatus: boolean, id: number, params?: RequestParams) =>
      this.request<any, any>(
        `/streamattributemap/v1/${id}/${anyStatus}/relations`,
        "GET",
        params,
        null,
        BodyType.Json,
        true,
      ),
  };
  streamtasks = {
    /**
     * No description
     *
     * @tags stream task - v1
     * @name Fetch
     * @request GET:/streamtasks/v1
     * @secure
     */
    fetch: (
      query?: { desc?: boolean; filter?: string; offset?: number; pageSize?: number; sortBy?: string },
      params?: RequestParams,
    ) =>
      this.request<any, any>(`/streamtasks/v1${this.addQueryParams(query)}`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags stream task - v1
     * @name Enable
     * @request PATCH:/streamtasks/v1/{filterId}
     * @secure
     */
    enable: (filterId: number, params?: RequestParams) =>
      this.request<any, any>(`/streamtasks/v1/${filterId}`, "PATCH", params, null, BodyType.Json, true),
  };
  stroomIndex = {
    /**
     * No description
     *
     * @tags stroom-index query - v2
     * @name GetDataSource
     * @summary Submit a request for a data source definition, supplying the DocRef for the data source
     * @request POST:/stroom-index/v2/dataSource
     * @secure
     */
    getDataSource: (body: DocRef, params?: RequestParams) =>
      this.request<DataSource, any>(`/stroom-index/v2/dataSource`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags stroom-index query - v2
     * @name Destroy
     * @summary Destroy a running query
     * @request POST:/stroom-index/v2/destroy
     * @secure
     */
    destroy: (body: QueryKey, params?: RequestParams) =>
      this.request<boolean, any>(`/stroom-index/v2/destroy`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags stroom-index query - v2
     * @name Search
     * @summary Submit a search request
     * @request POST:/stroom-index/v2/search
     * @secure
     */
    search: (body: SearchRequest, params?: RequestParams) =>
      this.request<SearchResponse, any>(`/stroom-index/v2/search`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags stroom-index volumes - v1
     * @name GetAll
     * @request GET:/stroom-index/volume/v1
     * @secure
     */
    getAll: (params?: RequestParams) =>
      this.request<any, any>(`/stroom-index/volume/v1`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags stroom-index volumes - v1
     * @name Create
     * @request POST:/stroom-index/volume/v1
     * @secure
     */
    create: (body: IndexVolume, params?: RequestParams) =>
      this.request<any, any>(`/stroom-index/volume/v1`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags stroom-index volumes - v1
     * @name Update
     * @request PUT:/stroom-index/volume/v1
     * @secure
     */
    update: (body: IndexVolume, params?: RequestParams) =>
      this.request<any, any>(`/stroom-index/volume/v1`, "PUT", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags stroom-index volumes - v1
     * @name GetById
     * @request GET:/stroom-index/volume/v1/{id}
     * @secure
     */
    getById: (id: number, params?: RequestParams) =>
      this.request<any, any>(`/stroom-index/volume/v1/${id}`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags stroom-index volumes - v1
     * @name Delete
     * @request DELETE:/stroom-index/volume/v1/{id}
     * @secure
     */
    delete: (id: number, params?: RequestParams) =>
      this.request<any, any>(`/stroom-index/volume/v1/${id}`, "DELETE", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags stroom-index volumeGroup - v1
     * @name GetAll2
     * @request GET:/stroom-index/volumeGroup/v1
     * @originalName getAll
     * @duplicate
     * @secure
     */
    getAll2: (params?: RequestParams) =>
      this.request<any, any>(`/stroom-index/volumeGroup/v1`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags stroom-index volumeGroup - v1
     * @name Create2
     * @request POST:/stroom-index/volumeGroup/v1
     * @originalName create
     * @duplicate
     * @secure
     */
    create2: (params?: RequestParams) =>
      this.request<any, any>(`/stroom-index/volumeGroup/v1`, "POST", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags stroom-index volumeGroup - v1
     * @name Update2
     * @request PUT:/stroom-index/volumeGroup/v1
     * @originalName update
     * @duplicate
     * @secure
     */
    update2: (body: IndexVolumeGroup, params?: RequestParams) =>
      this.request<any, any>(`/stroom-index/volumeGroup/v1`, "PUT", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags stroom-index volumeGroup - v1
     * @name GetNames
     * @request GET:/stroom-index/volumeGroup/v1/names
     * @secure
     */
    getNames: (params?: RequestParams) =>
      this.request<any, any>(`/stroom-index/volumeGroup/v1/names`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags stroom-index volumeGroup - v1
     * @name Get
     * @request GET:/stroom-index/volumeGroup/v1/{id}
     * @secure
     */
    get: (id: string, params?: RequestParams) =>
      this.request<any, any>(`/stroom-index/volumeGroup/v1/${id}`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags stroom-index volumeGroup - v1
     * @name Delete2
     * @request DELETE:/stroom-index/volumeGroup/v1/{id}
     * @originalName delete
     * @duplicate
     * @secure
     */
    delete2: (id: string, params?: RequestParams) =>
      this.request<any, any>(`/stroom-index/volumeGroup/v1/${id}`, "DELETE", params, null, BodyType.Json, true),
  };
  stroomSolrIndex = {
    /**
     * @description Stroom Solr Index Query API
     *
     * @tags stroom-solr-index query - v2
     * @name GetDataSource
     * @summary Submit a request for a data source definition, supplying the DocRef for the data source
     * @request POST:/stroom-solr-index/v2/dataSource
     * @secure
     */
    getDataSource: (body: DocRef, params?: RequestParams) =>
      this.request<DataSource, any>(`/stroom-solr-index/v2/dataSource`, "POST", params, body, BodyType.Json, true),

    /**
     * @description Stroom Solr Index Query API
     *
     * @tags stroom-solr-index query - v2
     * @name Destroy
     * @summary Destroy a running query
     * @request POST:/stroom-solr-index/v2/destroy
     * @secure
     */
    destroy: (body: QueryKey, params?: RequestParams) =>
      this.request<boolean, any>(`/stroom-solr-index/v2/destroy`, "POST", params, body, BodyType.Json, true),

    /**
     * @description Stroom Solr Index Query API
     *
     * @tags stroom-solr-index query - v2
     * @name Search
     * @summary Submit a search request
     * @request POST:/stroom-solr-index/v2/search
     * @secure
     */
    search: (body: SearchRequest, params?: RequestParams) =>
      this.request<SearchResponse, any>(`/stroom-solr-index/v2/search`, "POST", params, body, BodyType.Json, true),
  };
  stroomSession = {
    /**
     * No description
     *
     * @tags stroomSession - v1
     * @name Invalidate
     * @summary Invalidate the current session
     * @request GET:/stroomSession/v1/invalidate
     * @secure
     */
    invalidate: (params?: RequestParams) =>
      this.request<boolean, any>(`/stroomSession/v1/invalidate`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags stroomSession - v1
     * @name ValidateSession
     * @summary Validate the current session, return a redirect Uri if invalid.
     * @request GET:/stroomSession/v1/noauth/validateSession
     * @secure
     */
    validateSession: (query: { redirect_uri: string }, params?: RequestParams) =>
      this.request<boolean, any>(
        `/stroomSession/v1/noauth/validateSession${this.addQueryParams(query)}`,
        "GET",
        params,
        null,
        BodyType.Json,
        true,
      ),
  };
  suggest = {
    /**
     * No description
     *
     * @tags suggest - v1
     * @name Fetch
     * @summary Fetch some suggestions
     * @request POST:/suggest/v1
     * @secure
     */
    fetch: (body: FetchSuggestionsRequest, params?: RequestParams) =>
      this.request<object[], any>(`/suggest/v1`, "POST", params, body, BodyType.Json, true),
  };
  systemInfo = {
    /**
     * No description
     *
     * @tags system info - v1
     * @name GetAll
     * @summary Get all system info results
     * @request GET:/systemInfo/v1
     * @secure
     */
    getAll: (params?: RequestParams) =>
      this.request<SystemInfoResult, any>(`/systemInfo/v1`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags system info - v1
     * @name GetNames
     * @summary Get all system info result names
     * @request GET:/systemInfo/v1/names
     * @secure
     */
    getNames: (params?: RequestParams) =>
      this.request<object[], any>(`/systemInfo/v1/names`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags system info - v1
     * @name Get
     * @summary Get a system info result by name
     * @request GET:/systemInfo/v1/{name}
     * @secure
     */
    get: (name: string, params?: RequestParams) =>
      this.request<object[], any>(`/systemInfo/v1/${name}`, "GET", params, null, BodyType.Json, true),
  };
  task = {
    /**
     * No description
     *
     * @tags task - v1
     * @name Find
     * @summary Finds tasks for a node
     * @request POST:/task/v1/find/{nodeName}
     * @secure
     */
    find: (nodeName: string, body: FindTaskProgressRequest, params?: RequestParams) =>
      this.request<TaskProgressResponse, any>(`/task/v1/find/${nodeName}`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags task - v1
     * @name List
     * @summary Lists tasks for a node
     * @request GET:/task/v1/list/{nodeName}
     * @secure
     */
    list: (nodeName: string, params?: RequestParams) =>
      this.request<TaskProgressResponse, any>(`/task/v1/list/${nodeName}`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags task - v1
     * @name Terminate
     * @summary Terminates tasks for a node
     * @request POST:/task/v1/terminate/{nodeName}
     * @secure
     */
    terminate: (nodeName: string, body: TerminateTaskProgressRequest, params?: RequestParams) =>
      this.request<boolean, any>(`/task/v1/terminate/${nodeName}`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags task - v1
     * @name UserTasks
     * @summary Lists tasks for a node
     * @request GET:/task/v1/user/{nodeName}
     * @secure
     */
    userTasks: (nodeName: string, params?: RequestParams) =>
      this.request<TaskProgressResponse, any>(`/task/v1/user/${nodeName}`, "GET", params, null, BodyType.Json, true),
  };
  textConverter = {
    /**
     * No description
     *
     * @tags textConverter - v1
     * @name Read
     * @summary Get a text converter doc
     * @request POST:/textConverter/v1/read
     * @secure
     */
    read: (body: DocRef, params?: RequestParams) =>
      this.request<TextConverterDoc, any>(`/textConverter/v1/read`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags textConverter - v1
     * @name Update
     * @summary Update a text converter doc
     * @request PUT:/textConverter/v1/update
     * @secure
     */
    update: (body: TextConverterDoc, params?: RequestParams) =>
      this.request<TextConverterDoc, any>(`/textConverter/v1/update`, "PUT", params, body, BodyType.Json, true),
  };
  token = {
    /**
     * No description
     *
     * @tags ApiKey
     * @name List
     * @summary Get all tokens.
     * @request GET:/token/v1
     * @secure
     */
    list: (params?: RequestParams) =>
      this.request<TokenResultPage, any>(`/token/v1`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags ApiKey
     * @name Create
     * @summary Create a new token.
     * @request POST:/token/v1
     * @secure
     */
    create: (body: CreateTokenRequest, params?: RequestParams) =>
      this.request<Token, any>(`/token/v1`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags ApiKey
     * @name DeleteAll
     * @summary Delete all tokens.
     * @request DELETE:/token/v1
     * @secure
     */
    deleteAll: (params?: RequestParams) =>
      this.request<number, any>(`/token/v1`, "DELETE", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags ApiKey
     * @name Read
     * @summary Read a token by the token string itself.
     * @request GET:/token/v1/byToken/{token}
     * @secure
     */
    read: (token: string, params?: RequestParams) =>
      this.request<Token, any>(`/token/v1/byToken/${token}`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags ApiKey
     * @name DeleteByToken
     * @summary Delete a token by the token string itself.
     * @request DELETE:/token/v1/byToken/{token}
     * @secure
     */
    deleteByToken: (token: string, params?: RequestParams) =>
      this.request<number, any>(`/token/v1/byToken/${token}`, "DELETE", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags ApiKey
     * @name FetchTokenConfig
     * @summary Get the token configuration
     * @request GET:/token/v1/noauth/fetchTokenConfig
     * @secure
     */
    fetchTokenConfig: (params?: RequestParams) =>
      this.request<TokenConfig, any>(`/token/v1/noauth/fetchTokenConfig`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags ApiKey
     * @name GetPublicKey
     * @summary Provides access to this service's current public key. A client may use these keys to verify JWTs issued by this service.
     * @request GET:/token/v1/publickey
     * @secure
     */
    getPublicKey: (params?: RequestParams) =>
      this.request<string, any>(`/token/v1/publickey`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags ApiKey
     * @name Search
     * @summary Submit a search request for tokens
     * @request POST:/token/v1/search
     * @secure
     */
    search: (body: SearchTokenRequest, params?: RequestParams) =>
      this.request<TokenResultPage, any>(`/token/v1/search`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags ApiKey
     * @name Read2
     * @summary Read a token by ID.
     * @request GET:/token/v1/{id}
     * @originalName read
     * @duplicate
     * @secure
     */
    read2: (id: number, params?: RequestParams) =>
      this.request<Token, any>(`/token/v1/${id}`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags ApiKey
     * @name Delete
     * @summary Delete a token by ID.
     * @request DELETE:/token/v1/{id}
     * @secure
     */
    delete: (id: number, params?: RequestParams) =>
      this.request<number, any>(`/token/v1/${id}`, "DELETE", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags ApiKey
     * @name ToggleEnabled
     * @summary Enable or disable the state of a token.
     * @request GET:/token/v1/{id}/enabled
     * @secure
     */
    toggleEnabled: (id: number, query: { enabled: boolean }, params?: RequestParams) =>
      this.request<number, any>(
        `/token/v1/${id}/enabled${this.addQueryParams(query)}`,
        "GET",
        params,
        null,
        BodyType.Json,
        true,
      ),
  };
  users = {
    /**
     * @description Stroom Authorisation API
     *
     * @tags authorisation - v1
     * @name Get
     * @request GET:/users/v1
     * @secure
     */
    get: (query?: { isGroup?: boolean; name?: string; uuid?: string }, params?: RequestParams) =>
      this.request<User[], any>(`/users/v1${this.addQueryParams(query)}`, "GET", params, null, BodyType.Json, true),

    /**
     * @description Stroom Authorisation API
     *
     * @tags authorisation - v1
     * @name GetAssociates
     * @summary Gets a list of associated users
     * @request GET:/users/v1/associates
     * @secure
     */
    getAssociates: (query?: { filter?: string }, params?: RequestParams) =>
      this.request<any, any>(
        `/users/v1/associates${this.addQueryParams(query)}`,
        "GET",
        params,
        null,
        BodyType.Json,
        true,
      ),

    /**
     * @description Stroom Authorisation API
     *
     * @tags authorisation - v1
     * @name Create
     * @request POST:/users/v1/create/{name}/{isGroup}
     * @secure
     */
    create: (isGroup: boolean, name: string, params?: RequestParams) =>
      this.request<User, any>(`/users/v1/create/${name}/${isGroup}`, "POST", params, null, BodyType.Json, true),

    /**
     * @description Stroom Authorisation API
     *
     * @tags authorisation - v1
     * @name Find
     * @request POST:/users/v1/find
     * @secure
     */
    find: (body: FindUserCriteria, params?: RequestParams) =>
      this.request<ResultPageUser, any>(`/users/v1/find`, "POST", params, body, BodyType.Json, true),

    /**
     * @description Stroom Authorisation API
     *
     * @tags authorisation - v1
     * @name SetStatus
     * @request PUT:/users/v1/{userName}/status
     * @secure
     */
    setStatus: (userName: string, query?: { enabled?: boolean }, params?: RequestParams) =>
      this.request<boolean, any>(
        `/users/v1/${userName}/status${this.addQueryParams(query)}`,
        "PUT",
        params,
        null,
        BodyType.Json,
        true,
      ),

    /**
     * @description Stroom Authorisation API
     *
     * @tags authorisation - v1
     * @name Get2
     * @request GET:/users/v1/{userUuid}
     * @originalName get
     * @duplicate
     * @secure
     */
    get2: (userUuid: string, params?: RequestParams) =>
      this.request<User, any>(`/users/v1/${userUuid}`, "GET", params, null, BodyType.Json, true),

    /**
     * @description Stroom Authorisation API
     *
     * @tags authorisation - v1
     * @name AddUserToGroup
     * @request PUT:/users/v1/{userUuid}/{groupUuid}
     * @secure
     */
    addUserToGroup: (groupUuid: string, userUuid: string, params?: RequestParams) =>
      this.request<boolean, any>(`/users/v1/${userUuid}/${groupUuid}`, "PUT", params, null, BodyType.Json, true),

    /**
     * @description Stroom Authorisation API
     *
     * @tags authorisation - v1
     * @name RemoveUserFromGroup
     * @request DELETE:/users/v1/{userUuid}/{groupUuid}
     * @secure
     */
    removeUserFromGroup: (groupUuid: string, userUuid: string, params?: RequestParams) =>
      this.request<boolean, any>(`/users/v1/${userUuid}/${groupUuid}`, "DELETE", params, null, BodyType.Json, true),

    /**
     * @description Stroom Authorisation API
     *
     * @tags authorisation - v1
     * @name DeleteUser
     * @request DELETE:/users/v1/{uuid}
     * @secure
     */
    deleteUser: (uuid: string, params?: RequestParams) =>
      this.request<boolean, any>(`/users/v1/${uuid}`, "DELETE", params, null, BodyType.Json, true),
  };
  viewData = {
    /**
     * No description
     *
     * @tags viewData - v1
     * @name Fetch
     * @summary Fetch matching data
     * @request POST:/viewData/v1/fetch
     * @secure
     */
    fetch: (body: FetchDataRequest, params?: RequestParams) =>
      this.request<AbstractFetchDataResult, any>(`/viewData/v1/fetch`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags viewData - v1
     * @name GetChildStreamTypes
     * @summary List child types for a stream
     * @request GET:/viewData/v1/listChildTypes
     * @secure
     */
    getChildStreamTypes: (query?: { id?: number; partNo?: number }, params?: RequestParams) =>
      this.request<AbstractFetchDataResult, any>(
        `/viewData/v1/listChildTypes${this.addQueryParams(query)}`,
        "GET",
        params,
        null,
        BodyType.Json,
        true,
      ),
  };
  visualisation = {
    /**
     * No description
     *
     * @tags visualisation - v1
     * @name Read
     * @summary Get a visualisation doc
     * @request POST:/visualisation/v1/read
     * @secure
     */
    read: (body: DocRef, params?: RequestParams) =>
      this.request<VisualisationDoc, any>(`/visualisation/v1/read`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags visualisation - v1
     * @name Update
     * @summary Update a visualisation doc
     * @request PUT:/visualisation/v1/update
     * @secure
     */
    update: (body: VisualisationDoc, params?: RequestParams) =>
      this.request<VisualisationDoc, any>(`/visualisation/v1/update`, "PUT", params, body, BodyType.Json, true),
  };
  welcome = {
    /**
     * No description
     *
     * @tags welcome - v1
     * @name Welcome
     * @request GET:/welcome/v1
     * @secure
     */
    welcome: (params?: RequestParams) =>
      this.request<any, any>(`/welcome/v1`, "GET", params, null, BodyType.Json, true),
  };
  xmlSchema = {
    /**
     * No description
     *
     * @tags xmlSchema - v1
     * @name Read
     * @summary Get an xml schema doc
     * @request POST:/xmlSchema/v1/read
     * @secure
     */
    read: (body: DocRef, params?: RequestParams) =>
      this.request<XmlSchemaDoc, any>(`/xmlSchema/v1/read`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags xmlSchema - v1
     * @name Update
     * @summary Update an xml schema doc
     * @request PUT:/xmlSchema/v1/update
     * @secure
     */
    update: (params?: RequestParams) =>
      this.request<XmlSchemaDoc, any>(`/xmlSchema/v1/update`, "PUT", params, null, BodyType.Json, true),
  };
  xslt = {
    /**
     * No description
     *
     * @tags xslt - v1
     * @name Read
     * @summary Get an xslt doc
     * @request POST:/xslt/v1/read
     * @secure
     */
    read: (body: DocRef, params?: RequestParams) =>
      this.request<XsltDoc, any>(`/xslt/v1/read`, "POST", params, body, BodyType.Json, true),

    /**
     * No description
     *
     * @tags xslt - v1
     * @name Update
     * @summary Update an xslt doc
     * @request PUT:/xslt/v1/update
     * @secure
     */
    update: (params?: RequestParams) =>
      this.request<XsltDoc, any>(`/xslt/v1/update`, "PUT", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags xslt - v1
     * @name Fetch
     * @request GET:/xslt/v1/{xsltId}
     * @secure
     */
    fetch: (xsltId: string, params?: RequestParams) =>
      this.request<XsltDoc, any>(`/xslt/v1/${xsltId}`, "GET", params, null, BodyType.Json, true),

    /**
     * No description
     *
     * @tags xslt - v1
     * @name Save
     * @request POST:/xslt/v1/{xsltId}
     * @secure
     */
    save: (xsltId: string, body: XsltDTO, params?: RequestParams) =>
      this.request<any, any>(`/xslt/v1/${xsltId}`, "POST", params, body, BodyType.Json, true),
  };
}
