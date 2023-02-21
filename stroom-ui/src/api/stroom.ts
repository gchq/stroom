/* eslint-disable */
/* tslint:disable */
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
  displayMode?: "TEXT" | "HEX" | "MARKER";
  errors?: string[];
  feedName?: string;

  /** The offset and length of a range of data in a sub-set of a query result set */
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
    | "MATCHES_REGEX"
  )[];
  name?: string;
  queryable?: boolean;
  type: string;
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
  /** Details of the page of results being returned. */
  pageResponse?: PageResponse;
  qualifiedFilterInput?: string;
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

export interface ApiKey {
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
  type?: string;

  /** @format int64 */
  updateTimeMs?: number;
  updateUser?: string;
  userEmail?: string;
  userId?: string;

  /** @format int32 */
  version?: number;
}

export interface ApiKeyResultPage {
  /** Details of the page of results being returned. */
  pageResponse?: PageResponse;
  qualifiedFilterInput?: string;
  values?: ApiKey[];
}

export interface ApplicationInstanceInfo {
  /** @format int64 */
  createTime?: number;
  userId?: string;
  uuid?: string;
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

  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
  docRef?: DocRef;
}

export type BooleanField = AbstractField;

export interface BuildInfo {
  /** @format int64 */
  buildTime?: number;
  buildVersion?: string;

  /** @format int64 */
  upTime?: number;
}

export interface BulkActionResult {
  explorerNodes?: ExplorerNode[];
  message?: string;
}

export interface CacheIdentity {
  basePropertyPath?: PropertyPath;
  cacheName?: string;
}

export interface CacheInfo {
  basePropertyPath?: PropertyPath;
  map?: Record<string, string>;
  name?: string;
  nodeName?: string;
}

export interface CacheInfoResponse {
  /** Details of the page of results being returned. */
  pageResponse?: PageResponse;
  values?: CacheInfo[];
}

export interface CacheNamesResponse {
  /** Details of the page of results being returned. */
  pageResponse?: PageResponse;
  values?: CacheIdentity[];
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

  /** @format int64 */
  discoverTime?: number;
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
  /** The client date/time settings */
  dateTimeSettings?: DateTimeSettings;

  /** A unique key to identify the instance of the search by. This key is used to identify multiple requests for the same search when running in incremental mode. */
  key?: QueryKey;

  /** @format int64 */
  now?: number;

  /** The query terms for the search */
  query?: Query;
  settings?: CoprocessorSettings[];
  shards?: number[];
  sourceTaskId?: TaskId;
  taskName?: string;
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
  type: string;
}

export interface ComponentSelectionHandler {
  componentId?: string;
  enabled?: boolean;

  /** A logical addOperator term in a query expression tree */
  expression?: ExpressionOperator;
  id?: string;
}

export interface ComponentSettings {
  type: string;
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

export interface CopyPermissionsFromParentRequest {
  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
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

export interface CreateApiKeyRequest {
  comments?: string;
  enabled?: boolean;

  /** @format int64 */
  expiresOnMs?: number;

  /** @pattern ^user$|^api$|^email_reset$ */
  tokenType: string;
  userId: string;
}

export interface CreateEntryRequest {
  annotation?: Annotation;
  data?: string;
  linkedEvents?: EventId[];
  type?: string;
}

export interface CreateProcessFilterRequest {
  autoPriority?: boolean;
  enabled?: boolean;

  /** @format int64 */
  maxMetaCreateTimeMs?: number;

  /** @format int64 */
  minMetaCreateTimeMs?: number;

  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
  pipeline?: DocRef;

  /** @format int32 */
  priority?: number;
  queryData?: QueryData;
  reprocess?: boolean;
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
  createTimeMs?: number;
  createUser?: string;
  dashboardConfig?: DashboardConfig;
  name?: string;
  type?: string;

  /** @format int64 */
  updateTimeMs?: number;
  updateUser?: string;
  uuid?: string;
  version?: string;
}

export interface DashboardSearchRequest {
  applicationInstanceUuid?: string;
  componentId?: string;
  componentResultRequests?: ComponentResultRequest[];
  dashboardUuid?: string;

  /** The client date/time settings */
  dateTimeSettings?: DateTimeSettings;

  /** A unique key to identify the instance of the search by. This key is used to identify multiple requests for the same search when running in incremental mode. */
  queryKey?: QueryKey;
  search?: Search;
  storeHistory?: boolean;

  /**
   * Set the maximum time (in ms) for the server to wait for a complete result set. The timeout applies to both incremental and non incremental queries, though the behaviour is slightly different. The timeout will make the server wait for which ever comes first out of the query completing or the timeout period being reached. If no value is supplied then for an incremental query a default value of 0 will be used (i.e. returning immediately) and for a non-incremental query the server's default timeout period will be used. For an incremental query, if the query has not completed by the end of the timeout period, it will return the currently know results with complete=false, however for a non-incremental query it will return no results, complete=false and details of the timeout in the error field
   * @format int64
   */
  timeout?: number;
}

export interface DashboardSearchResponse {
  complete?: boolean;
  errors?: string[];
  highlights?: string[];

  /** A unique key to identify the instance of the search by. This key is used to identify multiple requests for the same search when running in incremental mode. */
  queryKey?: QueryKey;
  results?: Result[];
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
  feed?: string;
  ruleName?: string;

  /** @format int32 */
  ruleNumber?: number;
  type?: string;
}

export interface DataRetentionDeleteSummaryRequest {
  criteria?: FindDataRetentionImpactCriteria;
  dataRetentionRules?: DataRetentionRules;
  queryId?: string;
}

export interface DataRetentionDeleteSummaryResponse {
  /** Details of the page of results being returned. */
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
  createTimeMs?: number;
  createUser?: string;
  name?: string;
  rules?: DataRetentionRule[];
  type?: string;

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

/**
 * The string formatting to apply to a date value
 */
export type DateTimeFormatSettings = FormatSettings & {
  pattern?: string;
  timeZone?: TimeZone;
  usePreferences?: boolean;
};

/**
 * The client date/time settings
 */
export interface DateTimeSettings {
  /** A date time formatting pattern string conforming to the specification of java.time.format.DateTimeFormatter */
  dateTimePattern?: string;

  /** The local zone id to use when formatting date values in the search results. The value is the string form of a java.time.ZoneId */
  localZoneId: string;

  /** The timezone to apply to a date time value */
  timeZone?: TimeZone;
}

export type DefaultLocation = Location;

export interface Dependency {
  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
  from?: DocRef;
  ok?: boolean;

  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
  to?: DocRef;
}

export interface DependencyCriteria {
  pageRequest?: PageRequest;
  partialName?: string;
  sort?: string;
  sortList?: CriteriaFieldSort[];
}

export interface DestroyRequest {
  applicationInstanceInfo?: ApplicationInstanceInfo;
  reason?: string;
}

export interface DestroySearchRequest {
  applicationInstanceUuid?: string;
  componentId?: string;
  dashboardUuid?: string;

  /** A unique key to identify the instance of the search by. This key is used to identify multiple requests for the same search when running in incremental mode. */
  queryKey?: QueryKey;
}

export interface DictionaryDoc {
  /** @format int64 */
  createTimeMs?: number;
  createUser?: string;
  data?: string;
  description?: string;
  imports?: DocRef[];
  name?: string;
  type?: string;

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
  group?: "STRUCTURE" | "DATA_PROCESSING" | "TRANSFORMATION" | "SEARCH" | "INDEXING" | "CONFIGURATION" | "SYSTEM";
  iconClassName?: string;
  type?: string;
}

export interface DocumentTypes {
  nonSystemTypes?: DocumentType[];
  visibleTypes?: DocumentType[];
}

export type DoubleField = AbstractField;

export interface DownloadSearchResultsRequest {
  componentId?: string;
  fileType?: "EXCEL" | "CSV" | "TSV";

  /** @format int32 */
  percent?: number;
  sample?: boolean;
  searchRequest?: DashboardSearchRequest;
}

export interface ElasticClusterDoc {
  connection?: ElasticConnectionConfig;

  /** @format int64 */
  createTimeMs?: number;
  createUser?: string;
  description?: string;
  name?: string;
  type?: string;

  /** @format int64 */
  updateTimeMs?: number;
  updateUser?: string;
  uuid?: string;
  version?: string;
}

export interface ElasticClusterTestResponse {
  message?: string;
  ok?: boolean;
}

export interface ElasticConnectionConfig {
  apiKeyId?: string;
  apiKeySecret?: string;
  caCertificate?: string;
  connectionUrls?: string[];

  /** @format int32 */
  socketTimeoutMillis?: number;
  useAuthentication?: boolean;
}

export interface ElasticIndexDoc {
  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
  clusterRef?: DocRef;

  /** @format int64 */
  createTimeMs?: number;
  createUser?: string;
  description?: string;
  fields?: ElasticIndexField[];
  indexName?: string;
  name?: string;

  /** A logical addOperator term in a query expression tree */
  retentionExpression?: ExpressionOperator;

  /** @format int32 */
  searchScrollSize?: number;

  /** @format int32 */
  searchSlices?: number;
  type?: string;

  /** @format int64 */
  updateTimeMs?: number;
  updateUser?: string;
  uuid?: string;
  version?: string;
}

export interface ElasticIndexField {
  fieldName?: string;
  fieldType?: string;
  fieldUse?: "ID" | "BOOLEAN" | "INTEGER" | "LONG" | "FLOAT" | "DOUBLE" | "DATE" | "TEXT" | "KEYWORD" | "IPV4_ADDRESS";
  indexed?: boolean;
}

export interface ElasticIndexTestResponse {
  message?: string;
  ok?: boolean;
}

export interface EntityEvent {
  action?: "CREATE" | "UPDATE" | "DELETE" | "CLEAR_CACHE";

  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
  docRef?: DocRef;

  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
  oldDocRef?: DocRef;
}

export interface Entry {
  key?: string;
  value?: string;
}

export interface EntryCounts {
  /** @format int64 */
  keyValueCount?: number;

  /** @format int64 */
  rangeValueCount?: number;
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
  iconClassName?: string;
  isFavourite?: boolean;
  name?: string;
  nodeState?: "OPEN" | "CLOSED" | "LEAF";
  rootNodeUuid?: string;
  tags?: string;
  type?: string;
  uniqueKey?: ExplorerNodeKey;
  uuid?: string;
}

export interface ExplorerNodeKey {
  rootNodeUuid?: string;
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
  destinationFolder?: ExplorerNode;
  explorerNodes?: ExplorerNode[];
  permissionInheritance?: "NONE" | "SOURCE" | "DESTINATION" | "COMBINED";
}

export interface ExplorerServiceCreateRequest {
  destinationFolder?: ExplorerNode;
  docName?: string;
  docType?: string;
  permissionInheritance?: "NONE" | "SOURCE" | "DESTINATION" | "COMBINED";
}

export interface ExplorerServiceDeleteRequest {
  docRefs?: DocRef[];
}

export interface ExplorerServiceMoveRequest {
  destinationFolder?: ExplorerNode;
  explorerNodes?: ExplorerNode[];
  permissionInheritance?: "NONE" | "SOURCE" | "DESTINATION" | "COMBINED";
}

export interface ExplorerServiceRenameRequest {
  docName?: string;
  explorerNode?: ExplorerNode;
}

export interface ExplorerTreeFilter {
  includedRootTypes?: string[];
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
  sort?: string;
  sortList?: CriteriaFieldSort[];
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
  type: string;
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

/**
 * A predicate term in a query expression tree
 */
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
    | "IS_NOT_NULL"
    | "MATCHES_REGEX";
  docRef?: DocRef;
  field?: string;
  value?: string;
};

export interface FeedDoc {
  classification?: string;
  contextEncoding?: string;

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
  displayMode?: "TEXT" | "HEX" | "MARKER";
  expandedSeverities?: ("INFO" | "WARN" | "ERROR" | "FATAL")[];

  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
  pipeline?: DocRef;

  /** @format int64 */
  recordCount?: number;
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
  openedItems?: ExplorerNodeKey[];
  qualifiedFilterInput?: string;
  rootNodes?: ExplorerNode[];
  temporaryOpenedItems?: ExplorerNodeKey[];
}

export interface FetchLinkedScriptRequest {
  loadedScripts?: DocRef[];

  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
  script?: DocRef;
}

export type FetchMarkerResult = AbstractFetchDataResult & { markers?: Marker[] };

export interface FetchNodeStatusResponse {
  /** Details of the page of results being returned. */
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

export interface FetchPropertyTypesResult {
  pipelineElementType?: PipelineElementType;
  propertyTypes?: Record<string, PipelinePropertyType>;
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

  /** @format int32 */
  group?: number;
  id?: string;
  name?: string;

  /** Describes the sorting applied to a field */
  sort?: Sort;
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

export interface FilterFieldDefinition {
  defaultField?: boolean;
  displayName?: string;
  filterQualifier?: string;
}

export interface FilterUsersRequest {
  quickFilterInput?: string;
  users?: SimpleUser[];
}

export interface FindDBTableCriteria {
  pageRequest?: PageRequest;
  sort?: string;
  sortList?: CriteriaFieldSort[];
}

export interface FindDataRetentionImpactCriteria {
  /** A logical addOperator term in a query expression tree */
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
  ensureVisible?: ExplorerNodeKey[];
  filter?: ExplorerTreeFilter;

  /** @format int32 */
  minDepth?: number;
  openItems?: ExplorerNodeKey[];
  temporaryOpenedItems?: ExplorerNodeKey[];
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

export interface FindJobNodeCriteria {
  jobName?: StringCriteria;
  nodeName?: StringCriteria;
  pageRequest?: PageRequest;
  sort?: string;
  sortList?: CriteriaFieldSort[];
}

export interface FindMetaCriteria {
  /** A logical addOperator term in a query expression tree */
  expression?: ExpressionOperator;
  fetchRelationships?: boolean;
  pageRequest?: PageRequest;
  sort?: string;
  sortList?: CriteriaFieldSort[];
}

export interface FindNodeStatusCriteria {
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

export interface FindUserNameCriteria {
  pageRequest?: PageRequest;
  quickFilterInput?: string;
  sort?: string;
  sortList?: CriteriaFieldSort[];
}

/**
 * A result structure used primarily for visualisation data
 */
export type FlatResult = Result & { size?: number; structure?: Field[]; values?: object[][] };

export type FloatField = AbstractField;

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
    | "406 - 102 - Data type is invalid"
    | "406 - 110 - Feed is not set to receive data"
    | "406 - 120 - Unexpected data type"
    | "406 - 200 - Unknown compression"
    | "401 - 300 - Client Certificate Required"
    | "401 - 301 - Client Token Required"
    | "403 - 310 - Client Certificate not authorised"
    | "403 - 311 - Client Token not authorised"
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
  importSettings?: ImportSettings;
  resourceKey?: ResourceKey;
}

export interface ImportConfigResponse {
  confirmList?: ImportState[];
  resourceKey?: ResourceKey;
}

export interface ImportSettings {
  enableFilters?: boolean;

  /** @format int64 */
  enableFiltersFromTime?: number;
  importMode?: "CREATE_CONFIRMATION" | "ACTION_CONFIRMATION" | "IGNORE_CONFIRMATION";

  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
  rootDocRef?: DocRef;
  useImportFolders?: boolean;
  useImportNames?: boolean;
}

export interface ImportState {
  action?: boolean;
  destPath?: string;

  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
  docRef?: DocRef;
  messageList?: Message[];
  sourcePath?: string;
  state?: "NEW" | "UPDATE" | "EQUAL" | "IGNORE";
  updatedFieldList?: string[];
}

export interface IndexDoc {
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

export type IpV4AddressField = AbstractField;

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
  createTimeMs?: number;
  createUser?: string;
  data?: string;
  description?: string;
  name?: string;
  type?: string;

  /** @format int64 */
  updateTimeMs?: number;
  updateUser?: string;
  uuid?: string;
  version?: string;
}

export type KeywordField = AbstractField;

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

/**
 * List of config properties
 */
export interface ListConfigResponse {
  nodeName?: string;

  /** Details of the page of results being returned. */
  pageResponse?: PageResponse;
  qualifiedFilterInput?: string;
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
  loginSuccessful?: boolean;
  message?: string;
  requirePasswordChange?: boolean;
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

  /** @format int32 */
  processorFilterId?: number;

  /** @format int64 */
  processorTaskId?: number;
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

export interface NodeSetJobsEnabledRequest {
  enabled?: boolean;
  excludeJobs?: string[];
  includeJobs?: string[];
}

export interface NodeSetJobsEnabledResponse {
  /** @format int32 */
  modifiedCount?: number;
}

export interface NodeStatusResult {
  master?: boolean;
  node?: Node;
}

/**
 * The definition of a format to apply to numeric data
 */
export type NumberFormatSettings = FormatSettings & { decimalPlaces?: number; useSeparator?: boolean };

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

export interface OverrideValueString {
  hasOverride?: boolean;
  value?: string;
}

export interface PageRequest {
  /** @format int32 */
  length?: number;

  /** @format int32 */
  offset?: number;
}

/**
 * Details of the page of results being returned.
 */
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

export interface ParamInfo {
  description?: string;
  name?: string;
  paramType?: "OPTIONAL" | "MANDATORY";
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

export interface PipelineData {
  elements?: PipelineElements;
  links?: PipelineLinks;
  pipelineReferences?: PipelineReferences;
  processors?: Processor[];
  properties?: PipelineProperties;
}

export interface PipelineDoc {
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
  /** @format int64 */
  defaultRecordLimit?: number;

  /** @format int64 */
  defaultTimeLimit?: number;
}

export interface ProcessingInfoResponse {
  createTime?: string;
  effectiveTime?: string;
  lastAccessedTime?: string;
  maps?: Record<string, EntryCounts>;
  processingState?: "LOAD_IN_PROGRESS" | "PURGE_IN_PROGRESS" | "COMPLETE" | "FAILED" | "TERMINATED" | "PURGE_FAILED";
  refStreamDefinition?: RefStreamDefinition;
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

  /** @format int64 */
  maxMetaCreateTimeMs?: number;

  /** @format int64 */
  minMetaCreateTimeMs?: number;
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
  message?: string;

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
  status?: "CREATED" | "COMPLETE" | "ERROR";

  /** @format int32 */
  version?: number;
}

export interface ProcessorListRow {
  expander?: Expander;
  type: string;
}

export interface ProcessorListRowResultPage {
  /** Details of the page of results being returned. */
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
  status?: "CREATED" | "QUEUED" | "ASSIGNED" | "PROCESSING" | "COMPLETE" | "FAILED" | "DELETED";

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

  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
  pipeline?: DocRef;

  /** @format int32 */
  priority?: number;
  status?: "CREATED" | "QUEUED" | "ASSIGNED" | "PROCESSING" | "COMPLETE" | "FAILED" | "DELETED";
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

  /** A logical addOperator term in a query expression tree */
  expression: ExpressionOperator;
  params?: Param[];
}

export type QueryComponentSettings = ComponentSettings & {
  automate?: Automate;
  dataSource?: DocRef;
  expression?: ExpressionOperator;
  selectionHandlers?: ComponentSelectionHandler[];
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
  params?: string;
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
  metaId?: number;

  /** @format int64 */
  recordIndex?: number;
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
  createTimeMs?: number;
  createUser?: string;
  fields?: AbstractField[];
  name?: string;
  rules?: ReceiveDataRule[];
  type?: string;

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
  processingState?: "LOAD_IN_PROGRESS" | "PURGE_IN_PROGRESS" | "COMPLETE" | "FAILED" | "TERMINATED" | "PURGE_FAILED";
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
  /** @format int64 */
  partIndex?: number;

  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
  pipelineDocRef?: DocRef;
  pipelineVersion?: string;

  /** @format int64 */
  streamId?: number;
}

export interface ReferenceLoader {
  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
  loaderPipeline: DocRef;

  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
  referenceFeed: DocRef;
  streamType?: string;
}

export type RemovePermissionEvent = PermissionChangeEvent & {
  documentUuid?: string;
  permission?: string;
  userUuid?: string;
};

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

/**
 * Base object for describing a set of result data
 */
export interface Result {
  /** The ID of the component that this result set was requested for. See ResultRequest in SearchRequest */
  componentId: string;

  /** If an error has occurred producing this result set then this will have details of the error */
  errors?: string[];
  type: string;
}

/**
 * A page of results.
 */
export interface ResultPageActivity {
  /** Details of the page of results being returned. */
  pageResponse?: PageResponse;
  values?: Activity[];
}

/**
 * A page of results.
 */
export interface ResultPageCustomRollUpMask {
  /** Details of the page of results being returned. */
  pageResponse?: PageResponse;
  values?: CustomRollUpMask[];
}

/**
 * A page of results.
 */
export interface ResultPageCustomRollUpMaskFields {
  /** Details of the page of results being returned. */
  pageResponse?: PageResponse;
  values?: CustomRollUpMaskFields[];
}

/**
 * A page of results.
 */
export interface ResultPageDBTableStatus {
  /** Details of the page of results being returned. */
  pageResponse?: PageResponse;
  values?: DBTableStatus[];
}

/**
 * A page of results.
 */
export interface ResultPageDependency {
  /** Details of the page of results being returned. */
  pageResponse?: PageResponse;
  values?: Dependency[];
}

/**
 * A page of results.
 */
export interface ResultPageFsVolume {
  /** Details of the page of results being returned. */
  pageResponse?: PageResponse;
  values?: FsVolume[];
}

/**
 * A page of results.
 */
export interface ResultPageIndexShard {
  /** Details of the page of results being returned. */
  pageResponse?: PageResponse;
  values?: IndexShard[];
}

/**
 * A page of results.
 */
export interface ResultPageIndexVolume {
  /** Details of the page of results being returned. */
  pageResponse?: PageResponse;
  values?: IndexVolume[];
}

/**
 * A page of results.
 */
export interface ResultPageIndexVolumeGroup {
  /** Details of the page of results being returned. */
  pageResponse?: PageResponse;
  values?: IndexVolumeGroup[];
}

/**
 * A page of results.
 */
export interface ResultPageJob {
  /** Details of the page of results being returned. */
  pageResponse?: PageResponse;
  values?: Job[];
}

/**
 * A page of results.
 */
export interface ResultPageJobNode {
  /** Details of the page of results being returned. */
  pageResponse?: PageResponse;
  values?: JobNode[];
}

/**
 * A page of results.
 */
export interface ResultPageMetaRow {
  /** Details of the page of results being returned. */
  pageResponse?: PageResponse;
  values?: MetaRow[];
}

/**
 * A page of results.
 */
export interface ResultPageProcessorTask {
  /** Details of the page of results being returned. */
  pageResponse?: PageResponse;
  values?: ProcessorTask[];
}

/**
 * A page of results.
 */
export interface ResultPageProcessorTaskSummary {
  /** Details of the page of results being returned. */
  pageResponse?: PageResponse;
  values?: ProcessorTaskSummary[];
}

/**
 * A page of results.
 */
export interface ResultPageStoredQuery {
  /** Details of the page of results being returned. */
  pageResponse?: PageResponse;
  values?: StoredQuery[];
}

/**
 * A page of results.
 */
export interface ResultPageString {
  /** Details of the page of results being returned. */
  pageResponse?: PageResponse;
  values?: string[];
}

/**
 * A page of results.
 */
export interface ResultPageUser {
  /** Details of the page of results being returned. */
  pageResponse?: PageResponse;
  values?: User[];
}

/**
 * A definition for how to return the raw results of the query in the SearchResponse, e.g. sorted, grouped, limited, etc.
 */
export interface ResultRequest {
  /** The ID of the component that will receive the results corresponding to this ResultRequest */
  componentId: string;
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
  createTimeMs?: number;
  createUser?: string;
  data?: string;
  dependencies?: DocRef[];
  description?: string;
  name?: string;
  type?: string;

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
}

export interface SearchAccountRequest {
  pageRequest?: PageRequest;
  quickFilter?: string;
  sort?: string;
  sortList?: CriteriaFieldSort[];
}

export interface SearchApiKeyRequest {
  pageRequest?: PageRequest;
  quickFilter?: string;
  sort?: string;
  sortList?: CriteriaFieldSort[];
}

/**
 * A request for new search or a follow up request for more data for an existing iterative search
 */
export interface SearchRequest {
  /** The client date/time settings */
  dateTimeSettings?: DateTimeSettings;

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
  complete?: boolean;
  errors?: string[];

  /** A list of strings to highlight in the UI that should correlate with the search query. */
  highlights: string[];

  /** A unique key to identify the instance of the search by. This key is used to identify multiple requests for the same search when running in incremental mode. */
  queryKey: QueryKey;
  results?: Result[];
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
  /** Details of the page of results being returned. */
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

export interface SimpleUser {
  name?: string;
  uuid?: string;
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
    | "MATCHES_REGEX"
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

/**
 * Describes the sorting applied to a field
 */
export interface Sort {
  /**
   * The direction to sort in, ASCENDING or DESCENDING
   * @example ASCENDING
   */
  direction: "ASCENDING" | "DESCENDING";

  /**
   * Where multiple fields are sorted this value describes the sort order, with 0 being the first field to sort on
   * @format int32
   * @example 0
   */
  order: number;
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

  /**
   * @format int32
   * @min 1
   */
  maxHexDumpLines?: number;
}

export interface SourceLocation {
  childType?: string;
  dataRange?: DataRange;
  highlights?: DataRange[];

  /** @format int64 */
  metaId?: number;

  /** @format int64 */
  partIndex?: number;

  /** @format int64 */
  recordIndex?: number;
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
  metaId?: number;

  /** @format int64 */
  partIndex?: number;

  /** @format int64 */
  recordIndex?: number;
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
  segmentedData?: boolean;
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

  /** The query terms for the search */
  query?: Query;

  /** @format int64 */
  updateTimeMs?: number;
  updateUser?: string;

  /** @format int32 */
  version?: number;
}

export type StreamLocation = Location & { partIndex?: number };

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

  /** TODO */
  queryId: string;
  showDetail?: boolean;
}

export type TableCoprocessorSettings = CoprocessorSettings & { componentIds?: string[]; tableSettings?: TableSettings };

/**
 * Object for describing a set of results in a table form that supports grouped data
 */
export type TableResult = Result & { fields?: Field[]; resultRange?: OffsetRange; rows?: Row[]; totalResults?: number };

export type TableResultRequest = ComponentResultRequest & {
  openGroups?: string[];
  requestedRange?: OffsetRange;
  tableSettings?: TableSettings;
};

/**
 * An object to describe how the query results should be returned, including which fields should be included and what sorting, grouping, filtering, limiting, etc. should be applied
 */
export interface TableSettings {
  extractValues?: boolean;

  /** A class for describing a unique reference to a 'document' in stroom.  A 'document' is an entity in stroom such as a data source dictionary or pipeline. */
  extractionPipeline?: DocRef;
  fields: Field[];

  /**
   * Defines the maximum number of results to return at each grouping level, e.g. '1000,10,1' means 1000 results at group level 0, 10 at level 1 and 1 at level 2. In the absence of this field system defaults will apply
   * @example 1000,10,1
   */
  maxResults?: number[];

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
  errors?: string[];

  /** Details of the page of results being returned. */
  pageResponse?: PageResponse;
  values?: TaskProgress[];
}

export interface TerminateTaskProgressRequest {
  criteria?: FindTaskCriteria;
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
  createTimeMs?: number;
  createUser?: string;
  data?: string;
  description?: string;
  name?: string;
  type?: string;

  /** @format int64 */
  updateTimeMs?: number;
  updateUser?: string;
  uuid?: string;
  version?: string;
}

export type TextField = AbstractField;

export interface ThemeConfig {
  backgroundAttachment?: string;
  backgroundColour?: string;
  backgroundImage?: string;
  backgroundOpacity?: string;
  backgroundPosition?: string;
  backgroundRepeat?: string;
  labelColours?: string;
  topMenuTextColour?: string;
  tubeOpacity?: string;
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
  use: "Local" | "UTC" | "Id" | "Offset";
}

export interface TokenResponse {
  access_token?: string;

  /** @format int64 */
  expires_in?: number;
  id_token?: string;
  refresh_token?: string;

  /** @format int64 */
  refresh_token_expires_in?: number;
  token_type?: string;
}

export interface UiConfig {
  aboutHtml?: string;
  activity?: ActivityConfig;

  /** @format int32 */
  applicationInstanceKeepAliveIntervalMs?: number;
  defaultMaxResults?: string;
  helpSubPathExpressions?: string;
  helpSubPathJobs?: string;
  helpSubPathProperties?: string;
  helpSubPathQuickFilter?: string;
  helpUrl?: string;
  htmlTitle?: string;
  maintenanceMessage?: string;
  namePattern?: string;

  /** @pattern ^return (true|false);$ */
  oncontextmenu?: string;
  process?: ProcessConfig;
  query?: QueryConfig;
  requireReactWrapper?: boolean;
  source?: SourceConfig;
  splash?: SplashConfig;
  theme?: ThemeConfig;
  welcomeHtml?: string;
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

export interface UrlResponse {
  url?: string;
}

export interface User {
  /** @format int64 */
  createTimeMs?: number;
  createUser?: string;
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

export interface UserPreferences {
  /** A date time formatting pattern string conforming to the specification of java.time.format.DateTimeFormatter */
  dateTimePattern?: string;
  density?: string;
  editorTheme?: string;
  font?: string;
  fontSize?: string;
  theme?: string;

  /** The timezone to apply to a date time value */
  timeZone?: TimeZone;
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

export interface ValidationResult {
  message?: string;
  severity?: "INFO" | "WARN" | "ERROR" | "FATAL";
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
  updateTimeMs?: number;
  updateUser?: string;
  uuid?: string;
  version?: string;
}

export interface Welcome {
  html?: string;
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
  updateTimeMs?: number;
  updateUser?: string;
  uuid?: string;
  version?: string;
}

export interface XsltDoc {
  /** @format int64 */
  createTimeMs?: number;
  createUser?: string;
  data?: string;
  description?: string;
  name?: string;
  type?: string;

  /** @format int64 */
  updateTimeMs?: number;
  updateUser?: string;
  uuid?: string;
  version?: string;
}

export type QueryParamsType = Record<string | number, any>;
export type ResponseFormat = keyof Omit<Body, "body" | "bodyUsed">;

export interface FullRequestParams extends Omit<RequestInit, "body"> {
  /** set parameter to `true` for call `securityWorker` for this request */
  secure?: boolean;
  /** request path */
  path: string;
  /** content type of request body */
  type?: ContentType;
  /** query params */
  query?: QueryParamsType;
  /** format of response (i.e. response.json() -> format: "json") */
  format?: ResponseFormat;
  /** request body */
  body?: unknown;
  /** base url */
  baseUrl?: string;
  /** request cancellation token */
  cancelToken?: CancelToken;
}

export type RequestParams = Omit<FullRequestParams, "body" | "method" | "query" | "path">;

export interface ApiConfig<SecurityDataType = unknown> {
  baseUrl?: string;
  baseApiParams?: Omit<RequestParams, "baseUrl" | "cancelToken" | "signal">;
  securityWorker?: (securityData: SecurityDataType | null) => Promise<RequestParams | void> | RequestParams | void;
}

export interface HttpResponse<D extends unknown, E extends unknown = unknown> extends Response {
  data: D;
  error: E;
}

type CancelToken = Symbol | string | number;

export enum ContentType {
  Json = "application/json",
  FormData = "multipart/form-data",
  UrlEncoded = "application/x-www-form-urlencoded",
}

export class HttpClient<SecurityDataType = unknown> {
  public baseUrl: string = "/api";
  private securityData: SecurityDataType | null = null;
  private securityWorker?: ApiConfig<SecurityDataType>["securityWorker"];
  private abortControllers = new Map<CancelToken, AbortController>();

  private baseApiParams: RequestParams = {
    credentials: "same-origin",
    headers: {},
    redirect: "follow",
    referrerPolicy: "no-referrer",
  };

  constructor(apiConfig: ApiConfig<SecurityDataType> = {}) {
    Object.assign(this, apiConfig);
  }

  public setSecurityData = (data: SecurityDataType | null) => {
    this.securityData = data;
  };

  private addQueryParam(query: QueryParamsType, key: string) {
    const value = query[key];

    return (
      encodeURIComponent(key) +
      "=" +
      encodeURIComponent(Array.isArray(value) ? value.join(",") : typeof value === "number" ? value : `${value}`)
    );
  }

  protected toQueryString(rawQuery?: QueryParamsType): string {
    const query = rawQuery || {};
    const keys = Object.keys(query).filter((key) => "undefined" !== typeof query[key]);
    return keys
      .map((key) =>
        typeof query[key] === "object" && !Array.isArray(query[key])
          ? this.toQueryString(query[key] as QueryParamsType)
          : this.addQueryParam(query, key),
      )
      .join("&");
  }

  protected addQueryParams(rawQuery?: QueryParamsType): string {
    const queryString = this.toQueryString(rawQuery);
    return queryString ? `?${queryString}` : "";
  }

  private contentFormatters: Record<ContentType, (input: any) => any> = {
    [ContentType.Json]: (input: any) =>
      input !== null && (typeof input === "object" || typeof input === "string") ? JSON.stringify(input) : input,
    [ContentType.FormData]: (input: any) =>
      Object.keys(input || {}).reduce((data, key) => {
        data.append(key, input[key]);
        return data;
      }, new FormData()),
    [ContentType.UrlEncoded]: (input: any) => this.toQueryString(input),
  };

  private mergeRequestParams(params1: RequestParams, params2?: RequestParams): RequestParams {
    return {
      ...this.baseApiParams,
      ...params1,
      ...(params2 || {}),
      headers: {
        ...(this.baseApiParams.headers || {}),
        ...(params1.headers || {}),
        ...((params2 && params2.headers) || {}),
      },
    };
  }

  private createAbortSignal = (cancelToken: CancelToken): AbortSignal | undefined => {
    if (this.abortControllers.has(cancelToken)) {
      const abortController = this.abortControllers.get(cancelToken);
      if (abortController) {
        return abortController.signal;
      }
      return void 0;
    }

    const abortController = new AbortController();
    this.abortControllers.set(cancelToken, abortController);
    return abortController.signal;
  };

  public abortRequest = (cancelToken: CancelToken) => {
    const abortController = this.abortControllers.get(cancelToken);

    if (abortController) {
      abortController.abort();
      this.abortControllers.delete(cancelToken);
    }
  };

  public request = async <T = any, E = any>({
    body,
    secure,
    path,
    type,
    query,
    format = "json",
    baseUrl,
    cancelToken,
    ...params
  }: FullRequestParams): Promise<HttpResponse<T, E>> => {
    const secureParams = (secure && this.securityWorker && (await this.securityWorker(this.securityData))) || {};
    const requestParams = this.mergeRequestParams(params, secureParams);
    const queryString = query && this.toQueryString(query);
    const payloadFormatter = this.contentFormatters[type || ContentType.Json];

    return fetch(`${baseUrl || this.baseUrl || ""}${path}${queryString ? `?${queryString}` : ""}`, {
      ...requestParams,
      headers: {
        ...(type && type !== ContentType.FormData ? { "Content-Type": type } : {}),
        ...(requestParams.headers || {}),
      },
      signal: cancelToken ? this.createAbortSignal(cancelToken) : void 0,
      body: typeof body === "undefined" || body === null ? null : payloadFormatter(body),
    }).then(async (response) => {
      const r = response as HttpResponse<T, E>;
      r.data = null as unknown as T;
      r.error = null as unknown as E;

      const data = await response[format]()
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

      if (cancelToken) {
        this.abortControllers.delete(cancelToken);
      }

      if (!response.ok) throw data;
      return data;
    });
  };
}

/**
 * @title Stroom API
 * @version v1/v2
 * @license Apache 2.0 (http://www.apache.org/licenses/LICENSE-2.0.html)
 * @baseUrl /api
 * @contact stroom (https://github.com/gchq/stroom)
 *
 * Various APIs for interacting with Stroom and its data
 */
export class Api<SecurityDataType extends unknown> extends HttpClient<SecurityDataType> {
  account = {
    /**
     * No description
     *
     * @tags Account
     * @name ListAccounts
     * @summary Get all accounts.
     * @request GET:/account/v1
     * @secure
     */
    listAccounts: (params: RequestParams = {}) =>
      this.request<any, AccountResultPage>({
        path: `/account/v1`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Account
     * @name CreateAccount
     * @summary Create an account.
     * @request POST:/account/v1
     * @secure
     */
    createAccount: (data: CreateAccountRequest, params: RequestParams = {}) =>
      this.request<any, number>({
        path: `/account/v1`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Account
     * @name SearchAccounts
     * @summary Search for an account by email.
     * @request POST:/account/v1/search
     * @secure
     */
    searchAccounts: (data: SearchAccountRequest, params: RequestParams = {}) =>
      this.request<any, AccountResultPage>({
        path: `/account/v1/search`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Account
     * @name DeleteAccount
     * @summary Delete an account by ID.
     * @request DELETE:/account/v1/{id}
     * @secure
     */
    deleteAccount: (id: number, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/account/v1/${id}`,
        method: "DELETE",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Account
     * @name FetchAccount
     * @summary Get an account by ID.
     * @request GET:/account/v1/{id}
     * @secure
     */
    fetchAccount: (id: number, params: RequestParams = {}) =>
      this.request<any, Account>({
        path: `/account/v1/${id}`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Account
     * @name UpdateAccount
     * @summary Update an account.
     * @request PUT:/account/v1/{id}
     * @secure
     */
    updateAccount: (id: number, data: UpdateAccountRequest, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/account/v1/${id}`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  activity = {
    /**
     * No description
     *
     * @tags Activities
     * @name ListActivities
     * @summary Lists activities
     * @request GET:/activity/v1
     * @secure
     */
    listActivities: (query?: { filter?: string }, params: RequestParams = {}) =>
      this.request<any, ResultPageActivity>({
        path: `/activity/v1`,
        method: "GET",
        query: query,
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Activities
     * @name CreateActivity
     * @summary Create an Activity
     * @request POST:/activity/v1
     * @secure
     */
    createActivity: (params: RequestParams = {}) =>
      this.request<any, Activity>({
        path: `/activity/v1`,
        method: "POST",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Activities
     * @name AcknowledgeSplash
     * @summary Acknowledge the slash screen
     * @request POST:/activity/v1/acknowledge
     * @secure
     */
    acknowledgeSplash: (data: AcknowledgeSplashRequest, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/activity/v1/acknowledge`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Activities
     * @name GetCurrentActivity
     * @summary Gets the current activity
     * @request GET:/activity/v1/current
     * @secure
     */
    getCurrentActivity: (params: RequestParams = {}) =>
      this.request<any, Activity>({
        path: `/activity/v1/current`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Activities
     * @name SetCurrentActivity
     * @summary Gets the current activity
     * @request PUT:/activity/v1/current
     * @secure
     */
    setCurrentActivity: (data: Activity, params: RequestParams = {}) =>
      this.request<any, Activity>({
        path: `/activity/v1/current`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Activities
     * @name ListActivityFieldDefinitions
     * @summary Lists activity field definitions
     * @request GET:/activity/v1/fields
     * @secure
     */
    listActivityFieldDefinitions: (params: RequestParams = {}) =>
      this.request<any, FilterFieldDefinition[]>({
        path: `/activity/v1/fields`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Activities
     * @name ValidateActivity
     * @summary Create an Activity
     * @request POST:/activity/v1/validate
     * @secure
     */
    validateActivity: (data: Activity, params: RequestParams = {}) =>
      this.request<any, ActivityValidationResult>({
        path: `/activity/v1/validate`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Activities
     * @name DeleteActivity
     * @summary Delete an activity
     * @request DELETE:/activity/v1/{id}
     * @secure
     */
    deleteActivity: (id: number, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/activity/v1/${id}`,
        method: "DELETE",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Activities
     * @name FetchActivity
     * @summary Fetch an Activity
     * @request GET:/activity/v1/{id}
     * @secure
     */
    fetchActivity: (id: number, params: RequestParams = {}) =>
      this.request<any, Activity>({
        path: `/activity/v1/${id}`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Activities
     * @name UpdateActivity
     * @summary Update an Activity
     * @request PUT:/activity/v1/{id}
     * @secure
     */
    updateActivity: (id: number, data: Activity, params: RequestParams = {}) =>
      this.request<any, Activity>({
        path: `/activity/v1/${id}`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  annotation = {
    /**
     * No description
     *
     * @tags Annotations
     * @name GetAnnotationDetail
     * @summary Gets an annotation
     * @request GET:/annotation/v1
     * @secure
     */
    getAnnotationDetail: (query?: { annotationId?: number }, params: RequestParams = {}) =>
      this.request<any, AnnotationDetail>({
        path: `/annotation/v1`,
        method: "GET",
        query: query,
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Annotations
     * @name CreateAnnotationEntry
     * @summary Gets an annotation
     * @request POST:/annotation/v1
     * @secure
     */
    createAnnotationEntry: (data: CreateEntryRequest, params: RequestParams = {}) =>
      this.request<any, AnnotationDetail>({
        path: `/annotation/v1`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Annotations
     * @name GetAnnotationComments
     * @summary Gets a list of predefined comments
     * @request GET:/annotation/v1/comment
     * @secure
     */
    getAnnotationComments: (query?: { filter?: string }, params: RequestParams = {}) =>
      this.request<any, string[]>({
        path: `/annotation/v1/comment`,
        method: "GET",
        query: query,
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Annotations
     * @name LinkAnnotationEvents
     * @summary Links an annotation to an event
     * @request POST:/annotation/v1/link
     * @secure
     */
    linkAnnotationEvents: (data: EventLink, params: RequestParams = {}) =>
      this.request<any, EventId[]>({
        path: `/annotation/v1/link`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Annotations
     * @name GetAnnotationLinkedEvents
     * @summary Gets a list of events linked to this annotation
     * @request GET:/annotation/v1/linkedEvents
     * @secure
     */
    getAnnotationLinkedEvents: (query?: { annotationId?: number }, params: RequestParams = {}) =>
      this.request<any, EventId[]>({
        path: `/annotation/v1/linkedEvents`,
        method: "GET",
        query: query,
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Annotations
     * @name SetAnnotationAssignedTo
     * @summary Bulk action to set the assignment for several annotations
     * @request POST:/annotation/v1/setAssignedTo
     * @secure
     */
    setAnnotationAssignedTo: (data: SetAssignedToRequest, params: RequestParams = {}) =>
      this.request<any, number>({
        path: `/annotation/v1/setAssignedTo`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Annotations
     * @name SetAnnotationStatus
     * @summary Bulk action to set the status for several annotations
     * @request POST:/annotation/v1/setStatus
     * @secure
     */
    setAnnotationStatus: (data: SetStatusRequest, params: RequestParams = {}) =>
      this.request<any, number>({
        path: `/annotation/v1/setStatus`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Annotations
     * @name GetAnnotationDStatus
     * @summary Gets a list of allowed statuses
     * @request GET:/annotation/v1/status
     * @secure
     */
    getAnnotationDStatus: (query?: { filter?: string }, params: RequestParams = {}) =>
      this.request<any, string[]>({
        path: `/annotation/v1/status`,
        method: "GET",
        query: query,
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Annotations
     * @name UnlinkAnnotationEvents
     * @summary Unlinks an annotation from an event
     * @request POST:/annotation/v1/unlink
     * @secure
     */
    unlinkAnnotationEvents: (data: EventLink, params: RequestParams = {}) =>
      this.request<any, EventId[]>({
        path: `/annotation/v1/unlink`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  apikey = {
    /**
     * No description
     *
     * @tags Api Keys
     * @name DeleteAllApiKeys
     * @summary Delete all API keys.
     * @request DELETE:/apikey/v1
     * @secure
     */
    deleteAllApiKeys: (params: RequestParams = {}) =>
      this.request<any, number>({
        path: `/apikey/v1`,
        method: "DELETE",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Api Keys
     * @name CreateApiKey
     * @summary Create a new API key.
     * @request POST:/apikey/v1
     * @secure
     */
    createApiKey: (data: CreateApiKeyRequest, params: RequestParams = {}) =>
      this.request<any, ApiKey>({
        path: `/apikey/v1`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Api Keys
     * @name DeleteApiKeyByData
     * @summary Delete an API key by the data itself.
     * @request DELETE:/apikey/v1/byData/{data}
     * @secure
     */
    deleteApiKeyByData: (data: string, params: RequestParams = {}) =>
      this.request<any, number>({
        path: `/apikey/v1/byData/${data}`,
        method: "DELETE",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Api Keys
     * @name FetchApiKeyByData
     * @summary Read a API key by the data itself.
     * @request GET:/apikey/v1/byData/{data}
     * @secure
     */
    fetchApiKeyByData: (data: string, params: RequestParams = {}) =>
      this.request<any, ApiKey>({
        path: `/apikey/v1/byData/${data}`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Api Keys
     * @name GetDefaultApiKeyExpirySeconds
     * @summary Get the default time taken for API keys to expire
     * @request GET:/apikey/v1/noauth/getDefaultApiKeyExpirySeconds
     * @secure
     */
    getDefaultApiKeyExpirySeconds: (params: RequestParams = {}) =>
      this.request<any, number>({
        path: `/apikey/v1/noauth/getDefaultApiKeyExpirySeconds`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Api Keys
     * @name SearchApiKeys
     * @summary Submit a search request for API keys
     * @request POST:/apikey/v1/search
     * @secure
     */
    searchApiKeys: (data: SearchApiKeyRequest, params: RequestParams = {}) =>
      this.request<any, ApiKeyResultPage>({
        path: `/apikey/v1/search`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Api Keys
     * @name DeleteApiKey
     * @summary Delete a API key by ID.
     * @request DELETE:/apikey/v1/{id}
     * @secure
     */
    deleteApiKey: (id: number, params: RequestParams = {}) =>
      this.request<any, number>({
        path: `/apikey/v1/${id}`,
        method: "DELETE",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Api Keys
     * @name FetchApiKey
     * @summary Read a API key by ID.
     * @request GET:/apikey/v1/{id}
     * @secure
     */
    fetchApiKey: (id: number, params: RequestParams = {}) =>
      this.request<any, ApiKey>({
        path: `/apikey/v1/${id}`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Api Keys
     * @name ToggleApiKeyEnabled
     * @summary Enable or disable the state of an API key.
     * @request GET:/apikey/v1/{id}/enabled
     * @secure
     */
    toggleApiKeyEnabled: (id: number, query: { enabled: boolean }, params: RequestParams = {}) =>
      this.request<any, number>({
        path: `/apikey/v1/${id}/enabled`,
        method: "GET",
        query: query,
        secure: true,
        ...params,
      }),
  };
  applicationInstance = {
    /**
     * No description
     *
     * @tags Application
     * @name ApplicationInstanceRegister
     * @summary Register a new application instance
     * @request GET:/application-instance/v1/register
     * @secure
     */
    applicationInstanceRegister: (params: RequestParams = {}) =>
      this.request<any, ApplicationInstanceInfo>({
        path: `/application-instance/v1/register`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Application
     * @name ApplicationInstanceRemove
     * @summary Remove an application instance
     * @request POST:/application-instance/v1/remove
     * @secure
     */
    applicationInstanceRemove: (data: DestroyRequest, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/application-instance/v1/remove`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  authentication = {
    /**
     * No description
     *
     * @tags Authentication
     * @name NeedsPasswordChange
     * @summary Check if a user's password needs changing.
     * @request GET:/authentication/v1/needsPasswordChange
     * @secure
     */
    needsPasswordChange: (query?: { email?: string }, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/authentication/v1/needsPasswordChange`,
        method: "GET",
        query: query,
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Authentication
     * @name ChangePassword
     * @summary Change a user's password.
     * @request POST:/authentication/v1/noauth/changePassword
     * @secure
     */
    changePassword: (data: ChangePasswordRequest, params: RequestParams = {}) =>
      this.request<any, ChangePasswordResponse>({
        path: `/authentication/v1/noauth/changePassword`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Authentication
     * @name ConfirmPassword
     * @summary Confirm an authenticated user's current password.
     * @request POST:/authentication/v1/noauth/confirmPassword
     * @secure
     */
    confirmPassword: (data: ConfirmPasswordRequest, params: RequestParams = {}) =>
      this.request<any, ConfirmPasswordResponse>({
        path: `/authentication/v1/noauth/confirmPassword`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Authentication
     * @name FetchPasswordPolicy
     * @summary Get the password policy
     * @request GET:/authentication/v1/noauth/fetchPasswordPolicy
     * @secure
     */
    fetchPasswordPolicy: (params: RequestParams = {}) =>
      this.request<any, PasswordPolicyConfig>({
        path: `/authentication/v1/noauth/fetchPasswordPolicy`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Authentication
     * @name GetAuthenticationState
     * @summary Get the current authentication state
     * @request GET:/authentication/v1/noauth/getAuthenticationState
     * @secure
     */
    getAuthenticationState: (params: RequestParams = {}) =>
      this.request<any, AuthenticationState>({
        path: `/authentication/v1/noauth/getAuthenticationState`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Authentication
     * @name Login
     * @summary Handle a login request made using username and password credentials.
     * @request POST:/authentication/v1/noauth/login
     * @secure
     */
    login: (data: LoginRequest, params: RequestParams = {}) =>
      this.request<any, LoginResponse>({
        path: `/authentication/v1/noauth/login`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Authentication
     * @name Logout
     * @summary Log a user out of their session
     * @request GET:/authentication/v1/noauth/logout
     * @secure
     */
    logout: (query: { redirect_uri: string }, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/authentication/v1/noauth/logout`,
        method: "GET",
        query: query,
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Authentication
     * @name ResetEmail
     * @summary Reset a user account using an email address.
     * @request GET:/authentication/v1/noauth/reset/{email}
     * @secure
     */
    resetEmail: (email: string, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/authentication/v1/noauth/reset/${email}`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Authentication
     * @name ResetPassword
     * @summary Reset an authenticated user's password.
     * @request POST:/authentication/v1/resetPassword
     * @secure
     */
    resetPassword: (data: ResetPasswordRequest, params: RequestParams = {}) =>
      this.request<any, ChangePasswordResponse>({
        path: `/authentication/v1/resetPassword`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  cache = {
    /**
     * No description
     *
     * @tags Caches
     * @name ClearCache
     * @summary Clears a cache
     * @request DELETE:/cache/v1
     * @secure
     */
    clearCache: (query?: { cacheName?: string; nodeName?: string }, params: RequestParams = {}) =>
      this.request<any, number>({
        path: `/cache/v1`,
        method: "DELETE",
        query: query,
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Caches
     * @name GetCacheInfo
     * @summary Gets cache info
     * @request GET:/cache/v1/info
     * @secure
     */
    getCacheInfo: (query?: { cacheName?: string; nodeName?: string }, params: RequestParams = {}) =>
      this.request<any, CacheInfoResponse>({
        path: `/cache/v1/info`,
        method: "GET",
        query: query,
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Caches
     * @name ListCaches
     * @summary Lists caches
     * @request GET:/cache/v1/list
     * @secure
     */
    listCaches: (query?: { nodeName?: string }, params: RequestParams = {}) =>
      this.request<any, CacheNamesResponse>({
        path: `/cache/v1/list`,
        method: "GET",
        query: query,
        secure: true,
        ...params,
      }),
  };
  cluster = {
    /**
     * No description
     *
     * @tags Cluster lock
     * @name KeepClusterLockAlive
     * @summary Keep a lock alive
     * @request PUT:/cluster/lock/v1/keepALive/{nodeName}
     * @secure
     */
    keepClusterLockAlive: (nodeName: string, data: ClusterLockKey, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/cluster/lock/v1/keepALive/${nodeName}`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Cluster lock
     * @name ReleaseClusterLock
     * @summary Release a lock
     * @request PUT:/cluster/lock/v1/release/{nodeName}
     * @secure
     */
    releaseClusterLock: (nodeName: string, data: ClusterLockKey, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/cluster/lock/v1/release/${nodeName}`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Cluster lock
     * @name TryClusterLock
     * @summary Try to lock
     * @request PUT:/cluster/lock/v1/try/{nodeName}
     * @secure
     */
    tryClusterLock: (nodeName: string, data: ClusterLockKey, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/cluster/lock/v1/try/${nodeName}`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  config = {
    /**
     * No description
     *
     * @tags Global Config
     * @name CreateConfigProperty
     * @summary Create a configuration property
     * @request POST:/config/v1
     * @secure
     */
    createConfigProperty: (data: ConfigProperty, params: RequestParams = {}) =>
      this.request<any, ConfigProperty>({
        path: `/config/v1`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Global Config
     * @name UpdateConfigProperty
     * @summary Update a configuration property
     * @request PUT:/config/v1/clusterProperties/{propertyName}
     * @secure
     */
    updateConfigProperty: (propertyName: string, data: ConfigProperty, params: RequestParams = {}) =>
      this.request<any, ConfigProperty>({
        path: `/config/v1/clusterProperties/${propertyName}`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Global Config
     * @name GetConfigYamlValueByNodeAndName
     * @summary Get the property value from the YAML configuration in the specified node.
     * @request GET:/config/v1/clusterProperties/{propertyName}/yamlOverrideValue/{nodeName}
     * @secure
     */
    getConfigYamlValueByNodeAndName: (propertyName: string, nodeName: string, params: RequestParams = {}) =>
      this.request<any, OverrideValueString>({
        path: `/config/v1/clusterProperties/${propertyName}/yamlOverrideValue/${nodeName}`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Global Config
     * @name FetchUiConfig
     * @summary Fetch the UI configuration
     * @request GET:/config/v1/noauth/fetchUiConfig
     * @secure
     */
    fetchUiConfig: (params: RequestParams = {}) =>
      this.request<any, UiConfig>({
        path: `/config/v1/noauth/fetchUiConfig`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Global Config
     * @name ListConfigPropertiesByNode
     * @summary List all properties matching the criteria on the requested node.
     * @request POST:/config/v1/nodeProperties/{nodeName}
     * @secure
     */
    listConfigPropertiesByNode: (nodeName: string, data: GlobalConfigCriteria, params: RequestParams = {}) =>
      this.request<any, ListConfigResponse>({
        path: `/config/v1/nodeProperties/${nodeName}`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Global Config
     * @name ListConfigProperties
     * @summary List all properties matching the criteria on the current node.
     * @request POST:/config/v1/properties
     * @secure
     */
    listConfigProperties: (data: GlobalConfigCriteria, params: RequestParams = {}) =>
      this.request<any, ListConfigResponse>({
        path: `/config/v1/properties`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Global Config
     * @name GetConfigPropertyByName
     * @summary Fetch a property by its name, e.g. 'stroom.path.home'
     * @request GET:/config/v1/properties/{propertyName}
     * @secure
     */
    getConfigPropertyByName: (propertyName: string, params: RequestParams = {}) =>
      this.request<any, ConfigProperty>({
        path: `/config/v1/properties/${propertyName}`,
        method: "GET",
        secure: true,
        ...params,
      }),
  };
  content = {
    /**
     * No description
     *
     * @tags Content
     * @name ExportContent
     * @summary Export content
     * @request POST:/content/v1/export
     * @secure
     */
    exportContent: (data: DocRefs, params: RequestParams = {}) =>
      this.request<any, ResourceGeneration>({
        path: `/content/v1/export`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Content
     * @name FetchContentDependencies
     * @summary Fetch content dependencies
     * @request POST:/content/v1/fetchDependencies
     * @secure
     */
    fetchContentDependencies: (data: DependencyCriteria, params: RequestParams = {}) =>
      this.request<any, ResultPageDependency>({
        path: `/content/v1/fetchDependencies`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Content
     * @name ImportContent
     * @summary Import content
     * @request POST:/content/v1/import
     * @secure
     */
    importContent: (data: ImportConfigRequest, params: RequestParams = {}) =>
      this.request<any, ImportConfigResponse>({
        path: `/content/v1/import`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  dashboard = {
    /**
     * No description
     *
     * @tags Dashboards
     * @name DashboardDestroySearch
     * @summary Destroy a running search
     * @request POST:/dashboard/v1/destroy
     * @secure
     */
    dashboardDestroySearch: (data: DestroySearchRequest, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/dashboard/v1/destroy`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Dashboards
     * @name DownloadDashboardQuery
     * @summary Download a query
     * @request POST:/dashboard/v1/downloadQuery
     * @secure
     */
    downloadDashboardQuery: (data: DashboardSearchRequest, params: RequestParams = {}) =>
      this.request<any, ResourceGeneration>({
        path: `/dashboard/v1/downloadQuery`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Dashboards
     * @name DownloadDashboardSearchResults
     * @summary Download search results
     * @request POST:/dashboard/v1/downloadSearchResults
     * @secure
     */
    downloadDashboardSearchResults: (data: DownloadSearchResultsRequest, params: RequestParams = {}) =>
      this.request<any, ResourceGeneration>({
        path: `/dashboard/v1/downloadSearchResults`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Dashboards
     * @name FetchTimeZones
     * @summary Fetch time zone data from the server
     * @request GET:/dashboard/v1/fetchTimeZones
     * @secure
     */
    fetchTimeZones: (params: RequestParams = {}) =>
      this.request<any, string[]>({
        path: `/dashboard/v1/fetchTimeZones`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Dashboards
     * @name FetchDashboardFunctions
     * @summary Fetch all expression functions
     * @request GET:/dashboard/v1/functions
     * @secure
     */
    fetchDashboardFunctions: (params: RequestParams = {}) =>
      this.request<any, FunctionSignature[]>({
        path: `/dashboard/v1/functions`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Dashboards
     * @name DashboardSearch
     * @summary Perform a new search or get new results
     * @request POST:/dashboard/v1/search
     * @secure
     */
    dashboardSearch: (data: DashboardSearchRequest, params: RequestParams = {}) =>
      this.request<any, DashboardSearchResponse>({
        path: `/dashboard/v1/search`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Dashboards
     * @name ValidateDashboardExpression
     * @summary Validate an expression
     * @request POST:/dashboard/v1/validateExpression
     * @secure
     */
    validateDashboardExpression: (data: string, params: RequestParams = {}) =>
      this.request<any, ValidateExpressionResult>({
        path: `/dashboard/v1/validateExpression`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Dashboards
     * @name FetchDashboard
     * @summary Fetch a dashboard doc by its UUID
     * @request GET:/dashboard/v1/{uuid}
     * @secure
     */
    fetchDashboard: (uuid: string, params: RequestParams = {}) =>
      this.request<any, DashboardDoc>({
        path: `/dashboard/v1/${uuid}`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Dashboards
     * @name UpdateDashboard
     * @summary Update a dashboard doc
     * @request PUT:/dashboard/v1/{uuid}
     * @secure
     */
    updateDashboard: (uuid: string, data: DashboardDoc, params: RequestParams = {}) =>
      this.request<any, DashboardDoc>({
        path: `/dashboard/v1/${uuid}`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  data = {
    /**
     * No description
     *
     * @tags Data
     * @name DownloadData
     * @summary Download matching data
     * @request POST:/data/v1/download
     * @secure
     */
    downloadData: (data: FindMetaCriteria, params: RequestParams = {}) =>
      this.request<any, ResourceGeneration>({
        path: `/data/v1/download`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Data
     * @name FetchData
     * @summary Fetch matching data
     * @request POST:/data/v1/fetch
     * @secure
     */
    fetchData: (data: FetchDataRequest, params: RequestParams = {}) =>
      this.request<any, AbstractFetchDataResult>({
        path: `/data/v1/fetch`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Data
     * @name UploadData
     * @summary Upload data
     * @request POST:/data/v1/upload
     * @secure
     */
    uploadData: (data: UploadDataRequest, params: RequestParams = {}) =>
      this.request<any, ResourceKey>({
        path: `/data/v1/upload`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Data
     * @name ViewDataInfo
     * @summary Find full info about a data item
     * @request GET:/data/v1/{id}/info
     * @secure
     */
    viewDataInfo: (id: number, params: RequestParams = {}) =>
      this.request<any, DataInfoSection[]>({
        path: `/data/v1/${id}/info`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Data
     * @name GetChildStreamTypes
     * @summary List child types for a stream
     * @request GET:/data/v1/{id}/parts/{partNo}/child-types
     * @secure
     */
    getChildStreamTypes: (id: number, partNo: number, params: RequestParams = {}) =>
      this.request<any, string[]>({
        path: `/data/v1/${id}/parts/${partNo}/child-types`,
        method: "GET",
        secure: true,
        ...params,
      }),
  };
  dataDownload = {
    /**
     * No description
     *
     * @tags Data Download
     * @name DownloadZip
     * @summary Retrieve content matching the provided criteria as a zip file
     * @request POST:/dataDownload/v1/downloadZip
     * @secure
     */
    downloadZip: (data: FindMetaCriteria, params: RequestParams = {}) =>
      this.request<any, void>({
        path: `/dataDownload/v1/downloadZip`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  dataRetentionRules = {
    /**
     * No description
     *
     * @tags Data Retention Rules
     * @name FetchDataRetentionRules
     * @summary Get data retention rules
     * @request GET:/dataRetentionRules/v1
     * @secure
     */
    fetchDataRetentionRules: (params: RequestParams = {}) =>
      this.request<any, DataRetentionRules>({
        path: `/dataRetentionRules/v1`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Data Retention Rules
     * @name UpdateDataRetentionRules
     * @summary Update data retention rules
     * @request PUT:/dataRetentionRules/v1
     * @secure
     */
    updateDataRetentionRules: (data: DataRetentionRules, params: RequestParams = {}) =>
      this.request<any, DataRetentionRules>({
        path: `/dataRetentionRules/v1`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Data Retention Rules
     * @name GetDataRetentionImpactSummary
     * @summary Get a summary of meta deletions with the passed data retention rules
     * @request POST:/dataRetentionRules/v1/impactSummary
     * @secure
     */
    getDataRetentionImpactSummary: (data: DataRetentionDeleteSummaryRequest, params: RequestParams = {}) =>
      this.request<any, DataRetentionDeleteSummaryResponse>({
        path: `/dataRetentionRules/v1/impactSummary`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Data Retention Rules
     * @name StopDataRetentionImpactSummary
     * @summary Stop a running query
     * @request DELETE:/dataRetentionRules/v1/impactSummary/{queryId}
     * @secure
     */
    stopDataRetentionImpactSummary: (queryId: string, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/dataRetentionRules/v1/impactSummary/${queryId}`,
        method: "DELETE",
        secure: true,
        ...params,
      }),
  };
  dataSource = {
    /**
     * No description
     *
     * @tags Data Sources
     * @name FetchDataSourceFields
     * @summary Fetch data source fields
     * @request POST:/dataSource/v1/fetchFields
     * @secure
     */
    fetchDataSourceFields: (data: DocRef, params: RequestParams = {}) =>
      this.request<any, AbstractField[]>({
        path: `/dataSource/v1/fetchFields`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  dbStatus = {
    /**
     * No description
     *
     * @tags Database Status
     * @name GetDbSystemTableStatus
     * @summary Find status of the DB
     * @request GET:/dbStatus/v1
     * @secure
     */
    getDbSystemTableStatus: (params: RequestParams = {}) =>
      this.request<any, ResultPageDBTableStatus>({
        path: `/dbStatus/v1`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Database Status
     * @name FindDbSystemTableStatus
     * @summary Find status of the DB
     * @request POST:/dbStatus/v1
     * @secure
     */
    findDbSystemTableStatus: (data: FindDBTableCriteria, params: RequestParams = {}) =>
      this.request<any, ResultPageDBTableStatus>({
        path: `/dbStatus/v1`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  dictionary = {
    /**
     * No description
     *
     * @tags Dictionaries (v1)
     * @name DownloadDictionary
     * @summary Download a dictionary doc
     * @request POST:/dictionary/v1/download
     * @secure
     */
    downloadDictionary: (data: DocRef, params: RequestParams = {}) =>
      this.request<any, ResourceGeneration>({
        path: `/dictionary/v1/download`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Dictionaries (v1)
     * @name FetchDictionary
     * @summary Fetch a dictionary doc by its UUID
     * @request GET:/dictionary/v1/{uuid}
     * @secure
     */
    fetchDictionary: (uuid: string, params: RequestParams = {}) =>
      this.request<any, DictionaryDoc>({
        path: `/dictionary/v1/${uuid}`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Dictionaries (v1)
     * @name UpdateDictionary
     * @summary Update a dictionary doc
     * @request PUT:/dictionary/v1/{uuid}
     * @secure
     */
    updateDictionary: (uuid: string, data: DictionaryDoc, params: RequestParams = {}) =>
      this.request<any, DictionaryDoc>({
        path: `/dictionary/v1/${uuid}`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  docFav = {
    /**
     * No description
     *
     * @tags Document Favourites
     * @name CreateDocFav
     * @summary Set a document as a favourite for the current user
     * @request POST:/docFav/v1/create
     * @secure
     */
    createDocFav: (data: DocRef, params: RequestParams = {}) =>
      this.request<any, void>({
        path: `/docFav/v1/create`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Document Favourites
     * @name DeleteDocFav
     * @summary Unset a document as favourite for the current user
     * @request DELETE:/docFav/v1/delete
     * @secure
     */
    deleteDocFav: (data: DocRef, params: RequestParams = {}) =>
      this.request<any, void>({
        path: `/docFav/v1/delete`,
        method: "DELETE",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Document Favourites
     * @name FetchDocFavs
     * @summary Retrieve all DocRefs the current user has marked as favourite
     * @request GET:/docFav/v1/fetchDocFavs
     * @secure
     */
    fetchDocFavs: (params: RequestParams = {}) =>
      this.request<any, DocRef[]>({
        path: `/docFav/v1/fetchDocFavs`,
        method: "GET",
        secure: true,
        ...params,
      }),
  };
  elasticCluster = {
    /**
     * No description
     *
     * @tags Elastic Clusters
     * @name TestElasticCluster
     * @summary Test connection to the Elasticsearch cluster
     * @request POST:/elasticCluster/v1/testCluster
     * @secure
     */
    testElasticCluster: (data: ElasticClusterDoc, params: RequestParams = {}) =>
      this.request<any, ElasticClusterTestResponse>({
        path: `/elasticCluster/v1/testCluster`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Elastic Clusters
     * @name FetchElasticCluster
     * @summary Fetch an Elasticsearch cluster doc by its UUID
     * @request GET:/elasticCluster/v1/{uuid}
     * @secure
     */
    fetchElasticCluster: (uuid: string, params: RequestParams = {}) =>
      this.request<any, ElasticClusterDoc>({
        path: `/elasticCluster/v1/${uuid}`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Elastic Clusters
     * @name UpdateElasticCluster
     * @summary Update an Elasticsearch cluster doc
     * @request PUT:/elasticCluster/v1/{uuid}
     * @secure
     */
    updateElasticCluster: (uuid: string, data: ElasticClusterDoc, params: RequestParams = {}) =>
      this.request<any, ElasticClusterDoc>({
        path: `/elasticCluster/v1/${uuid}`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  elasticIndex = {
    /**
     * No description
     *
     * @tags Elastic Indices
     * @name TestElasticIndex
     * @summary Test the Elasticsearch index
     * @request POST:/elasticIndex/v1/testIndex
     * @secure
     */
    testElasticIndex: (data: ElasticIndexDoc, params: RequestParams = {}) =>
      this.request<any, ElasticIndexTestResponse>({
        path: `/elasticIndex/v1/testIndex`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Elastic Indices
     * @name FetchElasticIndex
     * @summary Fetch an Elasticsearch index doc by its UUID
     * @request GET:/elasticIndex/v1/{uuid}
     * @secure
     */
    fetchElasticIndex: (uuid: string, params: RequestParams = {}) =>
      this.request<any, ElasticIndexDoc>({
        path: `/elasticIndex/v1/${uuid}`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Elastic Indices
     * @name UpdateElasticIndex
     * @summary Update an Elasticsearch index doc
     * @request PUT:/elasticIndex/v1/{uuid}
     * @secure
     */
    updateElasticIndex: (uuid: string, data: ElasticIndexDoc, params: RequestParams = {}) =>
      this.request<any, ElasticIndexDoc>({
        path: `/elasticIndex/v1/${uuid}`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  entityEvent = {
    /**
     * No description
     *
     * @tags Entity Events
     * @name FireEntityEvent
     * @summary Sends an entity event
     * @request PUT:/entityEvent/v1/{nodeName}
     * @secure
     */
    fireEntityEvent: (nodeName: string, data: EntityEvent, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/entityEvent/v1/${nodeName}`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  explorer = {
    /**
     * No description
     *
     * @tags Explorer (v2)
     * @name CopyExplorerItems
     * @summary Copy explorer items
     * @request POST:/explorer/v2/copy
     * @secure
     */
    copyExplorerItems: (data: ExplorerServiceCopyRequest, params: RequestParams = {}) =>
      this.request<any, BulkActionResult>({
        path: `/explorer/v2/copy`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Explorer (v2)
     * @name CreateExplorerItem
     * @summary Create explorer item
     * @request POST:/explorer/v2/create
     * @secure
     */
    createExplorerItem: (data: ExplorerServiceCreateRequest, params: RequestParams = {}) =>
      this.request<any, ExplorerNode>({
        path: `/explorer/v2/create`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Explorer (v2)
     * @name DeleteExplorerItems
     * @summary Delete explorer items
     * @request DELETE:/explorer/v2/delete
     * @secure
     */
    deleteExplorerItems: (data: ExplorerServiceDeleteRequest, params: RequestParams = {}) =>
      this.request<any, BulkActionResult>({
        path: `/explorer/v2/delete`,
        method: "DELETE",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Explorer (v2)
     * @name FetchExplorerDocRefs
     * @summary Fetch document references
     * @request POST:/explorer/v2/fetchDocRefs
     * @secure
     */
    fetchExplorerDocRefs: (data: DocRef[], params: RequestParams = {}) =>
      this.request<any, DocRef[]>({
        path: `/explorer/v2/fetchDocRefs`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Explorer (v2)
     * @name FetchExplorerDocumentTypes
     * @summary Fetch document types
     * @request GET:/explorer/v2/fetchDocumentTypes
     * @secure
     */
    fetchExplorerDocumentTypes: (params: RequestParams = {}) =>
      this.request<any, DocumentTypes>({
        path: `/explorer/v2/fetchDocumentTypes`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Explorer (v2)
     * @name FetchExplorerNodes
     * @summary Fetch explorer nodes
     * @request POST:/explorer/v2/fetchExplorerNodes
     * @secure
     */
    fetchExplorerNodes: (data: FindExplorerNodeCriteria, params: RequestParams = {}) =>
      this.request<any, FetchExplorerNodeResult>({
        path: `/explorer/v2/fetchExplorerNodes`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Explorer (v2)
     * @name FetchExplorerPermissions
     * @summary Fetch permissions for explorer items
     * @request POST:/explorer/v2/fetchExplorerPermissions
     * @secure
     */
    fetchExplorerPermissions: (data: ExplorerNode[], params: RequestParams = {}) =>
      this.request<any, ExplorerNodePermissions[]>({
        path: `/explorer/v2/fetchExplorerPermissions`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Explorer (v2)
     * @name GetRootNodeRef
     * @summary Get a node from a document reference, decorated with its root node UUID
     * @request POST:/explorer/v2/getFromDocRef
     * @secure
     */
    getRootNodeRef: (data: DocRef, params: RequestParams = {}) =>
      this.request<any, ExplorerNode>({
        path: `/explorer/v2/getFromDocRef`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Explorer (v2)
     * @name FetchExplorerItemInfo
     * @summary Get document info
     * @request POST:/explorer/v2/info
     * @secure
     */
    fetchExplorerItemInfo: (data: DocRef, params: RequestParams = {}) =>
      this.request<any, DocRefInfo>({
        path: `/explorer/v2/info`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Explorer (v2)
     * @name MoveExplorerItems
     * @summary Move explorer items
     * @request PUT:/explorer/v2/move
     * @secure
     */
    moveExplorerItems: (data: ExplorerServiceMoveRequest, params: RequestParams = {}) =>
      this.request<any, BulkActionResult>({
        path: `/explorer/v2/move`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Explorer (v2)
     * @name RenameExplorerItems
     * @summary Rename explorer items
     * @request PUT:/explorer/v2/rename
     * @secure
     */
    renameExplorerItems: (data: ExplorerServiceRenameRequest, params: RequestParams = {}) =>
      this.request<any, ExplorerNode>({
        path: `/explorer/v2/rename`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  export = {
    /**
     * No description
     *
     * @tags Export
     * @name ExportAllContent
     * @summary Exports all configuration to a file.
     * @request GET:/export/v1
     * @secure
     */
    exportAllContent: (params: RequestParams = {}) =>
      this.request<any, void>({
        path: `/export/v1`,
        method: "GET",
        secure: true,
        ...params,
      }),
  };
  feed = {
    /**
     * No description
     *
     * @tags Feeds
     * @name FetchSupportedEncodings
     * @summary Fetch supported encodings
     * @request GET:/feed/v1/fetchSupportedEncodings
     * @secure
     */
    fetchSupportedEncodings: (params: RequestParams = {}) =>
      this.request<any, string[]>({
        path: `/feed/v1/fetchSupportedEncodings`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Feeds
     * @name FetchFeed
     * @summary Fetch a feed doc by its UUID
     * @request GET:/feed/v1/{uuid}
     * @secure
     */
    fetchFeed: (uuid: string, params: RequestParams = {}) =>
      this.request<any, FeedDoc>({
        path: `/feed/v1/${uuid}`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Feeds
     * @name UpdateFeed
     * @summary Update a feed doc
     * @request PUT:/feed/v1/{uuid}
     * @secure
     */
    updateFeed: (uuid: string, data: FeedDoc, params: RequestParams = {}) =>
      this.request<any, FeedDoc>({
        path: `/feed/v1/${uuid}`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  feedStatus = {
    /**
     * No description
     *
     * @tags Feed Status
     * @name GetFeedStatus
     * @summary Submit a request to get the status of a feed
     * @request POST:/feedStatus/v1/getFeedStatus
     * @secure
     */
    getFeedStatus: (data: GetFeedStatusRequest, params: RequestParams = {}) =>
      this.request<any, GetFeedStatusResponse>({
        path: `/feedStatus/v1/getFeedStatus`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  fsVolume = {
    /**
     * No description
     *
     * @tags Filesystem Volumes
     * @name CreateFsVolume
     * @summary Create a volume
     * @request POST:/fsVolume/v1
     * @secure
     */
    createFsVolume: (data: FsVolume, params: RequestParams = {}) =>
      this.request<any, FsVolume>({
        path: `/fsVolume/v1`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Filesystem Volumes
     * @name FindFsVolumes
     * @summary Finds volumes
     * @request POST:/fsVolume/v1/find
     * @secure
     */
    findFsVolumes: (data: FindFsVolumeCriteria, params: RequestParams = {}) =>
      this.request<any, ResultPageFsVolume>({
        path: `/fsVolume/v1/find`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Filesystem Volumes
     * @name RescanFsVolumes
     * @summary Rescans volumes
     * @request GET:/fsVolume/v1/rescan
     * @secure
     */
    rescanFsVolumes: (params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/fsVolume/v1/rescan`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Filesystem Volumes
     * @name ValidateFsVolume
     * @summary Validates a volume
     * @request POST:/fsVolume/v1/validate
     * @secure
     */
    validateFsVolume: (data: FsVolume, params: RequestParams = {}) =>
      this.request<any, ValidationResult>({
        path: `/fsVolume/v1/validate`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Filesystem Volumes
     * @name DeleteFsVolume
     * @summary Delete a volume
     * @request DELETE:/fsVolume/v1/{id}
     * @secure
     */
    deleteFsVolume: (id: number, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/fsVolume/v1/${id}`,
        method: "DELETE",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Filesystem Volumes
     * @name FetchFsVolume
     * @summary Get a volume
     * @request GET:/fsVolume/v1/{id}
     * @secure
     */
    fetchFsVolume: (id: number, params: RequestParams = {}) =>
      this.request<any, FsVolume>({
        path: `/fsVolume/v1/${id}`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Filesystem Volumes
     * @name UpdateFsVolume
     * @summary Update a volume
     * @request PUT:/fsVolume/v1/{id}
     * @secure
     */
    updateFsVolume: (id: number, data: FsVolume, params: RequestParams = {}) =>
      this.request<any, FsVolume>({
        path: `/fsVolume/v1/${id}`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  index = {
    /**
     * No description
     *
     * @tags Indexes (v2)
     * @name DeleteIndexShards
     * @summary Delete matching index shards
     * @request POST:/index/v2/shard/delete
     * @secure
     */
    deleteIndexShards: (data: FindIndexShardCriteria, query?: { nodeName?: string }, params: RequestParams = {}) =>
      this.request<any, number>({
        path: `/index/v2/shard/delete`,
        method: "POST",
        query: query,
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Indexes (v2)
     * @name FindIndexShards
     * @summary Find matching index shards
     * @request POST:/index/v2/shard/find
     * @secure
     */
    findIndexShards: (data: FindIndexShardCriteria, params: RequestParams = {}) =>
      this.request<any, ResultPageIndexShard>({
        path: `/index/v2/shard/find`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Indexes (v2)
     * @name FlushIndexShards
     * @summary Flush matching index shards
     * @request POST:/index/v2/shard/flush
     * @secure
     */
    flushIndexShards: (data: FindIndexShardCriteria, query?: { nodeName?: string }, params: RequestParams = {}) =>
      this.request<any, number>({
        path: `/index/v2/shard/flush`,
        method: "POST",
        query: query,
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Indexes (v2)
     * @name FetchIndex
     * @summary Fetch a index doc by its UUID
     * @request GET:/index/v2/{uuid}
     * @secure
     */
    fetchIndex: (uuid: string, params: RequestParams = {}) =>
      this.request<any, IndexDoc>({
        path: `/index/v2/${uuid}`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Indexes (v2)
     * @name UpdateIndex
     * @summary Update an index doc
     * @request PUT:/index/v2/{uuid}
     * @secure
     */
    updateIndex: (uuid: string, data: IndexDoc, params: RequestParams = {}) =>
      this.request<any, IndexDoc>({
        path: `/index/v2/${uuid}`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Index Volumes
     * @name CreateIndexVolume
     * @summary Creates an index volume
     * @request POST:/index/volume/v2
     * @secure
     */
    createIndexVolume: (data: IndexVolume, params: RequestParams = {}) =>
      this.request<any, IndexVolume>({
        path: `/index/volume/v2`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Index Volumes
     * @name FindIndexVolumes
     * @summary Finds index volumes matching request
     * @request POST:/index/volume/v2/find
     * @secure
     */
    findIndexVolumes: (data: ExpressionCriteria, params: RequestParams = {}) =>
      this.request<any, ResultPageIndexVolume>({
        path: `/index/volume/v2/find`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Index Volumes
     * @name RescanIndexVolumes
     * @summary Rescans index volumes
     * @request GET:/index/volume/v2/rescan
     * @secure
     */
    rescanIndexVolumes: (query?: { nodeName?: string }, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/index/volume/v2/rescan`,
        method: "GET",
        query: query,
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Index Volumes
     * @name ValidateIndexVolume
     * @summary Validates an index volume
     * @request POST:/index/volume/v2/validate
     * @secure
     */
    validateIndexVolume: (data: IndexVolume, params: RequestParams = {}) =>
      this.request<any, ValidationResult>({
        path: `/index/volume/v2/validate`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Index Volumes
     * @name DeleteIndexVolume
     * @summary Deletes an index volume
     * @request DELETE:/index/volume/v2/{id}
     * @secure
     */
    deleteIndexVolume: (id: number, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/index/volume/v2/${id}`,
        method: "DELETE",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Index Volumes
     * @name FetchIndexVolume
     * @summary Fetch an index volume
     * @request GET:/index/volume/v2/{id}
     * @secure
     */
    fetchIndexVolume: (id: number, params: RequestParams = {}) =>
      this.request<any, IndexVolume>({
        path: `/index/volume/v2/${id}`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Index Volumes
     * @name UpdateIndexVolume
     * @summary Updates an index volume
     * @request PUT:/index/volume/v2/{id}
     * @secure
     */
    updateIndexVolume: (id: number, data: IndexVolume, params: RequestParams = {}) =>
      this.request<any, IndexVolume>({
        path: `/index/volume/v2/${id}`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Index Volume Groups
     * @name CreateIndexVolumeGroup
     * @summary Creates an index volume group
     * @request POST:/index/volumeGroup/v2
     * @secure
     */
    createIndexVolumeGroup: (data: string, params: RequestParams = {}) =>
      this.request<any, IndexVolumeGroup>({
        path: `/index/volumeGroup/v2`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Index Volume Groups
     * @name FetchIndexVolumeGroupByName
     * @summary Gets an index volume group by name
     * @request GET:/index/volumeGroup/v2/fetchByName/{name}
     * @secure
     */
    fetchIndexVolumeGroupByName: (name: string, params: RequestParams = {}) =>
      this.request<any, IndexVolumeGroup>({
        path: `/index/volumeGroup/v2/fetchByName/${name}`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Index Volume Groups
     * @name FindIndexVolumeGroups
     * @summary Finds index volume groups matching request
     * @request POST:/index/volumeGroup/v2/find
     * @secure
     */
    findIndexVolumeGroups: (data: ExpressionCriteria, params: RequestParams = {}) =>
      this.request<any, ResultPageIndexVolumeGroup>({
        path: `/index/volumeGroup/v2/find`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Index Volume Groups
     * @name DeleteIndexVolumeGroup
     * @summary Deletes an index volume group
     * @request DELETE:/index/volumeGroup/v2/{id}
     * @secure
     */
    deleteIndexVolumeGroup: (id: number, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/index/volumeGroup/v2/${id}`,
        method: "DELETE",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Index Volume Groups
     * @name FetchIndexVolumeGroup
     * @summary Gets an index volume group
     * @request GET:/index/volumeGroup/v2/{id}
     * @secure
     */
    fetchIndexVolumeGroup: (id: number, params: RequestParams = {}) =>
      this.request<any, IndexVolumeGroup>({
        path: `/index/volumeGroup/v2/${id}`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Index Volume Groups
     * @name UpdateIndexVolumeGroup
     * @summary Updates an index volume group
     * @request PUT:/index/volumeGroup/v2/{id}
     * @secure
     */
    updateIndexVolumeGroup: (id: number, data: IndexVolumeGroup, params: RequestParams = {}) =>
      this.request<any, IndexVolumeGroup>({
        path: `/index/volumeGroup/v2/${id}`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  job = {
    /**
     * No description
     *
     * @tags Jobs
     * @name ListJobs
     * @summary Lists jobs
     * @request GET:/job/v1
     * @secure
     */
    listJobs: (params: RequestParams = {}) =>
      this.request<any, ResultPageJob>({
        path: `/job/v1`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Jobs
     * @name SetNodeJobsEnabled
     * @summary Sets the enabled state of jobs for the selected node. If both `includeJobs` and `excludeJobs` are unspecified or empty, this action will apply to ALL jobs.
     * @request PUT:/job/v1/setJobsEnabled/{nodeName}
     * @secure
     */
    setNodeJobsEnabled: (nodeName: string, data: NodeSetJobsEnabledRequest, params: RequestParams = {}) =>
      this.request<any, NodeSetJobsEnabledResponse>({
        path: `/job/v1/setJobsEnabled/${nodeName}`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Jobs
     * @name SetJobEnabled
     * @summary Sets the enabled status of the job
     * @request PUT:/job/v1/{id}/enabled
     * @secure
     */
    setJobEnabled: (id: number, data: boolean, params: RequestParams = {}) =>
      this.request<any, void>({
        path: `/job/v1/${id}/enabled`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  jobNode = {
    /**
     * No description
     *
     * @tags Jobs (Node)
     * @name ListJobNodes
     * @summary Lists job nodes
     * @request GET:/jobNode/v1
     * @secure
     */
    listJobNodes: (query?: { jobName?: string; nodeName?: string }, params: RequestParams = {}) =>
      this.request<any, ResultPageJobNode>({
        path: `/jobNode/v1`,
        method: "GET",
        query: query,
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Jobs (Node)
     * @name FindJobNodes
     * @summary Finds job nodes matching criteria and sort order
     * @request POST:/jobNode/v1/find
     * @secure
     */
    findJobNodes: (data: FindJobNodeCriteria, params: RequestParams = {}) =>
      this.request<any, ResultPageJobNode>({
        path: `/jobNode/v1/find`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Jobs (Node)
     * @name FetchJobNodeInfo
     * @summary Gets current info for a job node
     * @request GET:/jobNode/v1/info
     * @secure
     */
    fetchJobNodeInfo: (query?: { jobName?: string; nodeName?: string }, params: RequestParams = {}) =>
      this.request<any, JobNodeInfo>({
        path: `/jobNode/v1/info`,
        method: "GET",
        query: query,
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Jobs (Node)
     * @name SetJobNodeEnabled
     * @summary Sets the enabled status of the job node
     * @request PUT:/jobNode/v1/{id}/enabled
     * @secure
     */
    setJobNodeEnabled: (id: number, data: boolean, params: RequestParams = {}) =>
      this.request<any, void>({
        path: `/jobNode/v1/${id}/enabled`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Jobs (Node)
     * @name SetJobNodeSchedule
     * @summary Sets the schedule job node
     * @request PUT:/jobNode/v1/{id}/schedule
     * @secure
     */
    setJobNodeSchedule: (id: number, data: string, params: RequestParams = {}) =>
      this.request<any, void>({
        path: `/jobNode/v1/${id}/schedule`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Jobs (Node)
     * @name SetJobNodeTaskLimit
     * @summary Sets the task limit for the job node
     * @request PUT:/jobNode/v1/{id}/taskLimit
     * @secure
     */
    setJobNodeTaskLimit: (id: number, data: number, params: RequestParams = {}) =>
      this.request<any, void>({
        path: `/jobNode/v1/${id}/taskLimit`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  kafkaConfig = {
    /**
     * No description
     *
     * @tags Kafka Config
     * @name DownloadKafkaConfig
     * @summary Download a kafkaConfig doc
     * @request POST:/kafkaConfig/v1/download
     * @secure
     */
    downloadKafkaConfig: (data: DocRef, params: RequestParams = {}) =>
      this.request<any, ResourceGeneration>({
        path: `/kafkaConfig/v1/download`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Kafka Config
     * @name FetchKafkaConfig
     * @summary Fetch a kafkaConfig doc by its UUID
     * @request GET:/kafkaConfig/v1/{uuid}
     * @secure
     */
    fetchKafkaConfig: (uuid: string, params: RequestParams = {}) =>
      this.request<any, KafkaConfigDoc>({
        path: `/kafkaConfig/v1/${uuid}`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Kafka Config
     * @name UpdateKafkaConfig
     * @summary Update a kafkaConfig doc
     * @request PUT:/kafkaConfig/v1/{uuid}
     * @secure
     */
    updateKafkaConfig: (uuid: string, data: KafkaConfigDoc, params: RequestParams = {}) =>
      this.request<any, KafkaConfigDoc>({
        path: `/kafkaConfig/v1/${uuid}`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  meta = {
    /**
     * No description
     *
     * @tags Meta
     * @name FindMetaRow
     * @summary Find matching meta data
     * @request POST:/meta/v1/find
     * @secure
     */
    findMetaRow: (data: FindMetaCriteria, params: RequestParams = {}) =>
      this.request<any, ResultPageMetaRow>({
        path: `/meta/v1/find`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Meta
     * @name GetMetaReprocessSelectionSummary
     * @summary Get a summary of the parent items of the selected meta data
     * @request POST:/meta/v1/getReprocessSelectionSummary
     * @secure
     */
    getMetaReprocessSelectionSummary: (data: FindMetaCriteria, params: RequestParams = {}) =>
      this.request<any, SelectionSummary>({
        path: `/meta/v1/getReprocessSelectionSummary`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Meta
     * @name GetMetaSelectionSummary
     * @summary Get a summary of the selected meta data
     * @request POST:/meta/v1/getSelectionSummary
     * @secure
     */
    getMetaSelectionSummary: (data: FindMetaCriteria, params: RequestParams = {}) =>
      this.request<any, SelectionSummary>({
        path: `/meta/v1/getSelectionSummary`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Meta
     * @name GetStreamTypes
     * @summary Get a list of possible stream types
     * @request GET:/meta/v1/getTypes
     * @secure
     */
    getStreamTypes: (params: RequestParams = {}) =>
      this.request<any, string[]>({
        path: `/meta/v1/getTypes`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Meta
     * @name UpdateMetaStatus
     * @summary Update status on matching meta data
     * @request PUT:/meta/v1/update/status
     * @secure
     */
    updateMetaStatus: (data: UpdateStatusRequest, params: RequestParams = {}) =>
      this.request<any, number>({
        path: `/meta/v1/update/status`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Meta
     * @name Fetch
     * @summary Get a meta record for a given id, if permitted.
     * @request GET:/meta/v1/{id}
     * @secure
     */
    fetch: (id: number, params: RequestParams = {}) =>
      this.request<any, Meta>({
        path: `/meta/v1/${id}`,
        method: "GET",
        secure: true,
        ...params,
      }),
  };
  node = {
    /**
     * No description
     *
     * @tags Nodes
     * @name ListAllNodes
     * @summary Lists all nodes
     * @request GET:/node/v1/all
     * @secure
     */
    listAllNodes: (params: RequestParams = {}) =>
      this.request<any, string[]>({
        path: `/node/v1/all`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Nodes
     * @name ListEnabledNodes
     * @summary Lists enabled nodes
     * @request GET:/node/v1/enabled
     * @secure
     */
    listEnabledNodes: (params: RequestParams = {}) =>
      this.request<any, string[]>({
        path: `/node/v1/enabled`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Nodes
     * @name SetNodeEnabled
     * @summary Sets whether a node is enabled
     * @request PUT:/node/v1/enabled/{nodeName}
     * @secure
     */
    setNodeEnabled: (nodeName: string, data: boolean, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/node/v1/enabled/${nodeName}`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Nodes
     * @name FindNodes
     * @summary Finds nodes matching criteria and sort order
     * @request POST:/node/v1/find
     * @secure
     */
    findNodes: (data: FindNodeStatusCriteria, params: RequestParams = {}) =>
      this.request<any, FetchNodeStatusResponse>({
        path: `/node/v1/find`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Nodes
     * @name FetchNodeInfo
     * @summary Gets detailed information about a node
     * @request GET:/node/v1/info/{nodeName}
     * @secure
     */
    fetchNodeInfo: (nodeName: string, params: RequestParams = {}) =>
      this.request<any, ClusterNodeInfo>({
        path: `/node/v1/info/${nodeName}`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Nodes
     * @name PingNode
     * @summary Gets a ping time for a node
     * @request GET:/node/v1/ping/{nodeName}
     * @secure
     */
    pingNode: (nodeName: string, params: RequestParams = {}) =>
      this.request<any, number>({
        path: `/node/v1/ping/${nodeName}`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Nodes
     * @name SetNodePriority
     * @summary Sets the priority of a node
     * @request PUT:/node/v1/priority/{nodeName}
     * @secure
     */
    setNodePriority: (nodeName: string, data: number, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/node/v1/priority/${nodeName}`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  oauth2 = {
    /**
     * No description
     *
     * @tags API Keys, Authentication
     * @name OpenIdConfiguration
     * @summary Provides discovery for openid configuration
     * @request GET:/oauth2/v1/noauth/.well-known/openid-configuration
     * @secure
     */
    openIdConfiguration: (params: RequestParams = {}) =>
      this.request<any, string>({
        path: `/oauth2/v1/noauth/.well-known/openid-configuration`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Authentication
     * @name OpenIdAuth
     * @summary Submit an OpenId AuthenticationRequest.
     * @request GET:/oauth2/v1/noauth/auth
     * @secure
     */
    openIdAuth: (
      query: {
        scope: string;
        response_type: string;
        client_id: string;
        redirect_uri: string;
        nonce?: string;
        state?: string;
        prompt?: string;
      },
      params: RequestParams = {},
    ) =>
      this.request<any, void>({
        path: `/oauth2/v1/noauth/auth`,
        method: "GET",
        query: query,
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags API Keys, Authentication
     * @name OpenIdCerts
     * @summary Provides access to this service's current public key. A client may use these keys to verify JWTs issued by this service.
     * @request GET:/oauth2/v1/noauth/certs
     * @secure
     */
    openIdCerts: (params: RequestParams = {}) =>
      this.request<any, Record<string, Record<string, object>[]>>({
        path: `/oauth2/v1/noauth/certs`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Authentication
     * @name OpenIdToken
     * @summary Get a token from an access code or refresh token
     * @request POST:/oauth2/v1/noauth/token
     * @secure
     */
    openIdToken: (params: RequestParams = {}) =>
      this.request<any, TokenResponse>({
        path: `/oauth2/v1/noauth/token`,
        method: "POST",
        secure: true,
        ...params,
      }),
  };
  permission = {
    /**
     * No description
     *
     * @tags Application Permissions
     * @name GetUserAndPermissions
     * @summary User and app permissions for the current session
     * @request GET:/permission/app/v1
     * @secure
     */
    getUserAndPermissions: (params: RequestParams = {}) =>
      this.request<any, UserAndPermissions>({
        path: `/permission/app/v1`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Application Permissions
     * @name ChangeUserPermissions
     * @summary User and app permissions for the current session
     * @request POST:/permission/app/v1/changeUser
     * @secure
     */
    changeUserPermissions: (data: ChangeUserRequest, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/permission/app/v1/changeUser`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Application Permissions
     * @name FetchAllPermissions
     * @summary Get all possible permissions
     * @request GET:/permission/app/v1/fetchAllPermissions
     * @secure
     */
    fetchAllPermissions: (params: RequestParams = {}) =>
      this.request<any, string[]>({
        path: `/permission/app/v1/fetchAllPermissions`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Application Permissions
     * @name FetchUserAppPermissions
     * @summary User and app permissions for the specified user
     * @request POST:/permission/app/v1/fetchUserAppPermissions
     * @secure
     */
    fetchUserAppPermissions: (data: User, params: RequestParams = {}) =>
      this.request<any, UserAndPermissions>({
        path: `/permission/app/v1/fetchUserAppPermissions`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Application Permissions
     * @name FirePermissionChangeEvent
     * @summary Fires a permission change event
     * @request POST:/permission/changeEvent/v1/fireChange/{nodeName}
     * @secure
     */
    firePermissionChangeEvent: (nodeName: string, data: PermissionChangeRequest, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/permission/changeEvent/v1/fireChange/${nodeName}`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Doc Permissions
     * @name ChangeDocumentPermissions
     * @summary Change document permissions
     * @request POST:/permission/doc/v1/changeDocumentPermissions
     * @secure
     */
    changeDocumentPermissions: (data: ChangeDocumentPermissionsRequest, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/permission/doc/v1/changeDocumentPermissions`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Doc Permissions
     * @name CheckDocumentPermission
     * @summary Check document permission
     * @request POST:/permission/doc/v1/checkDocumentPermission
     * @secure
     */
    checkDocumentPermission: (data: CheckDocumentPermissionRequest, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/permission/doc/v1/checkDocumentPermission`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Doc Permissions
     * @name CopyPermissionFromParent
     * @summary Copy permissions from parent
     * @request POST:/permission/doc/v1/copyPermissionsFromParent
     * @secure
     */
    copyPermissionFromParent: (data: CopyPermissionsFromParentRequest, params: RequestParams = {}) =>
      this.request<any, DocumentPermissions>({
        path: `/permission/doc/v1/copyPermissionsFromParent`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Doc Permissions
     * @name FetchAllDocumentPermissions
     * @summary Fetch document permissions
     * @request POST:/permission/doc/v1/fetchAllDocumentPermissions
     * @secure
     */
    fetchAllDocumentPermissions: (data: FetchAllDocumentPermissionsRequest, params: RequestParams = {}) =>
      this.request<any, DocumentPermissions>({
        path: `/permission/doc/v1/fetchAllDocumentPermissions`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Doc Permissions
     * @name FilterUsers
     * @summary Get all permissions for a given document type
     * @request POST:/permission/doc/v1/filterUsers
     * @secure
     */
    filterUsers: (data: FilterUsersRequest, params: RequestParams = {}) =>
      this.request<any, SimpleUser[]>({
        path: `/permission/doc/v1/filterUsers`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Doc Permissions
     * @name GetPermissionForDocType
     * @summary Get all permissions for a given document type
     * @request GET:/permission/doc/v1/getPermissionForDocType/${docType}
     * @secure
     */
    getPermissionForDocType: (docType: string, params: RequestParams = {}) =>
      this.request<any, string[]>({
        path: `/permission/doc/v1/getPermissionForDocType/$${docType}`,
        method: "GET",
        secure: true,
        ...params,
      }),
  };
  pipeline = {
    /**
     * No description
     *
     * @tags Pipelines
     * @name FetchPipelineData
     * @summary Fetch data for a pipeline
     * @request POST:/pipeline/v1/fetchPipelineData
     * @secure
     */
    fetchPipelineData: (data: DocRef, params: RequestParams = {}) =>
      this.request<any, PipelineData[]>({
        path: `/pipeline/v1/fetchPipelineData`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Pipelines
     * @name FetchPipelineXml
     * @summary Fetch the XML for a pipeline
     * @request POST:/pipeline/v1/fetchPipelineXml
     * @secure
     */
    fetchPipelineXml: (data: DocRef, params: RequestParams = {}) =>
      this.request<any, FetchPipelineXmlResponse>({
        path: `/pipeline/v1/fetchPipelineXml`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Pipelines
     * @name GetPipelinePropertyTypes
     * @summary Get pipeline property types
     * @request GET:/pipeline/v1/propertyTypes
     * @secure
     */
    getPipelinePropertyTypes: (params: RequestParams = {}) =>
      this.request<any, FetchPropertyTypesResult[]>({
        path: `/pipeline/v1/propertyTypes`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Pipelines
     * @name SavePipelineXml
     * @summary Update a pipeline doc with XML directly
     * @request PUT:/pipeline/v1/savePipelineXml
     * @secure
     */
    savePipelineXml: (data: SavePipelineXmlRequest, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/pipeline/v1/savePipelineXml`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Pipelines
     * @name FetchPipeline
     * @summary Fetch a pipeline doc by its UUID
     * @request GET:/pipeline/v1/{uuid}
     * @secure
     */
    fetchPipeline: (uuid: string, params: RequestParams = {}) =>
      this.request<any, PipelineDoc>({
        path: `/pipeline/v1/${uuid}`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Pipelines
     * @name UpdatePipeline
     * @summary Update a pipeline doc
     * @request PUT:/pipeline/v1/{uuid}
     * @secure
     */
    updatePipeline: (uuid: string, data: PipelineDoc, params: RequestParams = {}) =>
      this.request<any, PipelineDoc>({
        path: `/pipeline/v1/${uuid}`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  preferences = {
    /**
     * No description
     *
     * @tags Preferences
     * @name FetchUserPreferences
     * @summary Fetch user preferences.
     * @request GET:/preferences/v1
     * @secure
     */
    fetchUserPreferences: (params: RequestParams = {}) =>
      this.request<any, UserPreferences>({
        path: `/preferences/v1`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Preferences
     * @name UpdateUserPreferences
     * @summary Update user preferences
     * @request POST:/preferences/v1
     * @secure
     */
    updateUserPreferences: (data: UserPreferences, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/preferences/v1`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Preferences
     * @name ResetToDefaultUserPreferences
     * @summary Resets preferences to the defaults
     * @request POST:/preferences/v1/resetToDefaultUserPreferences
     * @secure
     */
    resetToDefaultUserPreferences: (params: RequestParams = {}) =>
      this.request<any, UserPreferences>({
        path: `/preferences/v1/resetToDefaultUserPreferences`,
        method: "POST",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Preferences
     * @name SetDefaultUserPreferences
     * @summary Sets the default preferences for all users
     * @request POST:/preferences/v1/setDefaultUserPreferences
     * @secure
     */
    setDefaultUserPreferences: (data: UserPreferences, params: RequestParams = {}) =>
      this.request<any, UserPreferences>({
        path: `/preferences/v1/setDefaultUserPreferences`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  processor = {
    /**
     * No description
     *
     * @tags Processors
     * @name DeleteProcessor
     * @summary Deletes a processor
     * @request DELETE:/processor/v1/{id}
     * @secure
     */
    deleteProcessor: (id: number, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/processor/v1/${id}`,
        method: "DELETE",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Processors
     * @name FetchProcessor
     * @summary Fetch a processor
     * @request GET:/processor/v1/{id}
     * @secure
     */
    fetchProcessor: (id: number, params: RequestParams = {}) =>
      this.request<any, Processor>({
        path: `/processor/v1/${id}`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Processors
     * @name SetProcessorEnabled
     * @summary Sets the enabled/disabled state for a processor
     * @request PUT:/processor/v1/{id}/enabled
     * @secure
     */
    setProcessorEnabled: (id: number, data: boolean, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/processor/v1/${id}/enabled`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  processorFilter = {
    /**
     * No description
     *
     * @tags Processor Filters
     * @name CreateProcessorFilter
     * @summary Creates a filter
     * @request POST:/processorFilter/v1
     * @secure
     */
    createProcessorFilter: (data: CreateProcessFilterRequest, params: RequestParams = {}) =>
      this.request<any, ProcessorFilter>({
        path: `/processorFilter/v1`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Processor Filters
     * @name FindProcessorFilters
     * @summary Finds processors and filters matching request
     * @request POST:/processorFilter/v1/find
     * @secure
     */
    findProcessorFilters: (data: FetchProcessorRequest, params: RequestParams = {}) =>
      this.request<any, ProcessorListRowResultPage>({
        path: `/processorFilter/v1/find`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Processor Filters
     * @name ReprocessData
     * @summary Create filters to reprocess data
     * @request POST:/processorFilter/v1/reprocess
     * @secure
     */
    reprocessData: (data: CreateProcessFilterRequest, params: RequestParams = {}) =>
      this.request<any, ReprocessDataInfo[]>({
        path: `/processorFilter/v1/reprocess`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Processor Filters
     * @name DeleteProcessorFilter
     * @summary Deletes a filter
     * @request DELETE:/processorFilter/v1/{id}
     * @secure
     */
    deleteProcessorFilter: (id: number, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/processorFilter/v1/${id}`,
        method: "DELETE",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Processor Filters
     * @name FetchProcessorFilter
     * @summary Fetch a filter
     * @request GET:/processorFilter/v1/{id}
     * @secure
     */
    fetchProcessorFilter: (id: number, params: RequestParams = {}) =>
      this.request<any, ProcessorFilter>({
        path: `/processorFilter/v1/${id}`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Processor Filters
     * @name UpdateProcessorFilter
     * @summary Updates a filter
     * @request PUT:/processorFilter/v1/{id}
     * @secure
     */
    updateProcessorFilter: (id: number, data: ProcessorFilter, params: RequestParams = {}) =>
      this.request<any, ProcessorFilter>({
        path: `/processorFilter/v1/${id}`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Processor Filters
     * @name SetProcessorFilterEnabled
     * @summary Sets the enabled/disabled state for a filter
     * @request PUT:/processorFilter/v1/{id}/enabled
     * @secure
     */
    setProcessorFilterEnabled: (id: number, data: boolean, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/processorFilter/v1/${id}/enabled`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Processor Filters
     * @name SetProcessorFilterPriority
     * @summary Sets the priority for a filter
     * @request PUT:/processorFilter/v1/{id}/priority
     * @secure
     */
    setProcessorFilterPriority: (id: number, data: number, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/processorFilter/v1/${id}/priority`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  processorTask = {
    /**
     * No description
     *
     * @tags Processor Tasks
     * @name AbandonProcessorTasks
     * @summary Abandon some tasks
     * @request POST:/processorTask/v1/abandon/{nodeName}
     * @secure
     */
    abandonProcessorTasks: (nodeName: string, data: ProcessorTaskList, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/processorTask/v1/abandon/${nodeName}`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Processor Tasks
     * @name AssignProcessorTasks
     * @summary Assign some tasks
     * @request POST:/processorTask/v1/assign/{nodeName}
     * @secure
     */
    assignProcessorTasks: (nodeName: string, data: AssignTasksRequest, params: RequestParams = {}) =>
      this.request<any, ProcessorTaskList>({
        path: `/processorTask/v1/assign/${nodeName}`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Processor Tasks
     * @name FindProcessorTasks
     * @summary Finds processors tasks
     * @request POST:/processorTask/v1/find
     * @secure
     */
    findProcessorTasks: (data: ExpressionCriteria, params: RequestParams = {}) =>
      this.request<any, ResultPageProcessorTask>({
        path: `/processorTask/v1/find`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Processor Tasks
     * @name FindProcessorTaskSummary
     * @summary Finds processor task summaries
     * @request POST:/processorTask/v1/summary
     * @secure
     */
    findProcessorTaskSummary: (data: ExpressionCriteria, params: RequestParams = {}) =>
      this.request<any, ResultPageProcessorTaskSummary>({
        path: `/processorTask/v1/summary`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  refData = {
    /**
     * No description
     *
     * @tags Reference Data
     * @name ClearBufferPool
     * @summary Clear all buffers currently available in the buffer pool to reclaim memory. Performed on the named node or all nodes if null.
     * @request DELETE:/refData/v1/clearBufferPool
     * @secure
     */
    clearBufferPool: (query?: { nodeName?: string }, params: RequestParams = {}) =>
      this.request<any, void>({
        path: `/refData/v1/clearBufferPool`,
        method: "DELETE",
        query: query,
        secure: true,
        ...params,
      }),

    /**
     * @description This is primarily intended  for small scale debugging in non-production environments. If no limit is set a default limit is applied else the results will be limited to limit entries.
     *
     * @tags Reference Data
     * @name GetReferenceStoreEntries
     * @summary List entries from the reference data store on the node called.
     * @request GET:/refData/v1/entries
     * @secure
     */
    getReferenceStoreEntries: (
      query?: { limit?: number; refStreamId?: number; mapName?: string },
      params: RequestParams = {},
    ) =>
      this.request<any, RefStoreEntry[]>({
        path: `/refData/v1/entries`,
        method: "GET",
        query: query,
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Reference Data
     * @name LookupReferenceData
     * @summary Perform a reference data lookup using the supplied lookup request. Reference data will be loaded if required using the supplied reference pipeline. Performed on this node only.
     * @request POST:/refData/v1/lookup
     * @secure
     */
    lookupReferenceData: (data: RefDataLookupRequest, params: RequestParams = {}) =>
      this.request<any, string>({
        path: `/refData/v1/lookup`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Reference Data
     * @name PurgeReferenceDataByAge
     * @summary Explicitly delete all entries that are older than purgeAge. Performed on the named node, or all nodes if null.
     * @request DELETE:/refData/v1/purgeByAge/{purgeAge}
     * @secure
     */
    purgeReferenceDataByAge: (purgeAge: string, query?: { nodeName?: string }, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/refData/v1/purgeByAge/${purgeAge}`,
        method: "DELETE",
        query: query,
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Reference Data
     * @name PurgeReferenceDataByStream
     * @summary Delete all entries for a reference stream. Performed on the named node or all nodes if null.
     * @request DELETE:/refData/v1/purgeByStream/{refStreamId}
     * @secure
     */
    purgeReferenceDataByStream: (refStreamId: number, query?: { nodeName?: string }, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/refData/v1/purgeByStream/${refStreamId}`,
        method: "DELETE",
        query: query,
        secure: true,
        ...params,
      }),

    /**
     * @description This is primarily intended  for small scale debugging in non-production environments. If no limit is set a default limit is applied else the results will be limited to limit entries. Performed on this node only.
     *
     * @tags Reference Data
     * @name GetReferenceStreamProcessingInfoEntries
     * @summary List processing info entries for all ref streams
     * @request GET:/refData/v1/refStreamInfo
     * @secure
     */
    getReferenceStreamProcessingInfoEntries: (
      query?: { limit?: number; refStreamId?: number; mapName?: string },
      params: RequestParams = {},
    ) =>
      this.request<any, ProcessingInfoResponse[]>({
        path: `/refData/v1/refStreamInfo`,
        method: "GET",
        query: query,
        secure: true,
        ...params,
      }),
  };
  remoteSearch = {
    /**
     * No description
     *
     * @tags Remote Search
     * @name DestroyRemoteSearch
     * @summary Destroy search results
     * @request GET:/remoteSearch/v1/destroy
     * @secure
     */
    destroyRemoteSearch: (query?: { queryKey?: string }, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/remoteSearch/v1/destroy`,
        method: "GET",
        query: query,
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Remote Search
     * @name PollRemoteSearch
     * @summary Poll the server for search results for the supplied queryKey
     * @request GET:/remoteSearch/v1/poll
     * @secure
     */
    pollRemoteSearch: (query?: { queryKey?: string }, params: RequestParams = {}) =>
      this.request<any, void>({
        path: `/remoteSearch/v1/poll`,
        method: "GET",
        query: query,
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Remote Search
     * @name StartRemoteSearch
     * @summary Start a search
     * @request POST:/remoteSearch/v1/start
     * @secure
     */
    startRemoteSearch: (data: ClusterSearchTask, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/remoteSearch/v1/start`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  ruleset = {
    /**
     * No description
     *
     * @tags Rule Set
     * @name ExportReceiveDataRules
     * @summary Submit an export request
     * @request POST:/ruleset/v2/export
     * @secure
     */
    exportReceiveDataRules: (data: DocRef, params: RequestParams = {}) =>
      this.request<any, Base64EncodedDocumentData>({
        path: `/ruleset/v2/export`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Rule Set
     * @name ImportReceiveDataRules
     * @summary Submit an import request
     * @request POST:/ruleset/v2/import
     * @secure
     */
    importReceiveDataRules: (data: Base64EncodedDocumentData, params: RequestParams = {}) =>
      this.request<any, DocRef>({
        path: `/ruleset/v2/import`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Rule Set
     * @name ListReceiveDataRules
     * @summary Submit a request for a list of doc refs held by this service
     * @request GET:/ruleset/v2/list
     * @secure
     */
    listReceiveDataRules: (params: RequestParams = {}) =>
      this.request<any, DocRef[]>({
        path: `/ruleset/v2/list`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Rule Set
     * @name FetchReceiveDataRules
     * @summary Fetch a rules doc by its UUID
     * @request GET:/ruleset/v2/{uuid}
     * @secure
     */
    fetchReceiveDataRules: (uuid: string, params: RequestParams = {}) =>
      this.request<any, ReceiveDataRules>({
        path: `/ruleset/v2/${uuid}`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Rule Set
     * @name UpdateReceiveDataRules
     * @summary Update a rules doc
     * @request PUT:/ruleset/v2/{uuid}
     * @secure
     */
    updateReceiveDataRules: (uuid: string, data: ReceiveDataRules, params: RequestParams = {}) =>
      this.request<any, ReceiveDataRules>({
        path: `/ruleset/v2/${uuid}`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  scheduledTime = {
    /**
     * No description
     *
     * @tags Scheduled Time
     * @name GetScheduledTimes
     * @summary Gets scheduled time info
     * @request POST:/scheduledTime/v1
     * @secure
     */
    getScheduledTimes: (data: GetScheduledTimesRequest, params: RequestParams = {}) =>
      this.request<any, ScheduledTimes>({
        path: `/scheduledTime/v1`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  script = {
    /**
     * No description
     *
     * @tags Scripts
     * @name FetchLinkedScripts
     * @summary Fetch related scripts
     * @request POST:/script/v1/fetchLinkedScripts
     * @secure
     */
    fetchLinkedScripts: (data: FetchLinkedScriptRequest, params: RequestParams = {}) =>
      this.request<any, ScriptDoc[]>({
        path: `/script/v1/fetchLinkedScripts`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Scripts
     * @name FetchScript
     * @summary Fetch a script doc by its UUID
     * @request GET:/script/v1/{uuid}
     * @secure
     */
    fetchScript: (uuid: string, params: RequestParams = {}) =>
      this.request<any, ScriptDoc>({
        path: `/script/v1/${uuid}`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Scripts
     * @name UpdateScript
     * @summary Update a script doc
     * @request PUT:/script/v1/{uuid}
     * @secure
     */
    updateScript: (uuid: string, data: ScriptDoc, params: RequestParams = {}) =>
      this.request<any, ScriptDoc>({
        path: `/script/v1/${uuid}`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  searchable = {
    /**
     * No description
     *
     * @tags Searchable
     * @name GetSearchableDataSource
     * @summary Submit a request for a data source definition, supplying the DocRef for the data source
     * @request POST:/searchable/v2/dataSource
     * @secure
     */
    getSearchableDataSource: (data: DocRef, params: RequestParams = {}) =>
      this.request<any, DataSource>({
        path: `/searchable/v2/dataSource`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Searchable
     * @name DestroySearchableQuery
     * @summary Destroy a running query
     * @request POST:/searchable/v2/destroy
     * @secure
     */
    destroySearchableQuery: (data: QueryKey, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/searchable/v2/destroy`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Searchable
     * @name KeepAliveSearchableQuery
     * @summary Keep a running query alive
     * @request POST:/searchable/v2/keepAlive
     * @secure
     */
    keepAliveSearchableQuery: (data: QueryKey, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/searchable/v2/keepAlive`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Searchable
     * @name StartSearchableQuery
     * @summary Submit a search request
     * @request POST:/searchable/v2/search
     * @secure
     */
    startSearchableQuery: (data: SearchRequest, params: RequestParams = {}) =>
      this.request<any, SearchResponse>({
        path: `/searchable/v2/search`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  session = {
    /**
     * No description
     *
     * @tags Sessions
     * @name ListSessions
     * @summary Lists user sessions for a node, or all nodes in the cluster if nodeName is null
     * @request GET:/session/v1/list
     * @secure
     */
    listSessions: (query?: { nodeName?: string }, params: RequestParams = {}) =>
      this.request<any, SessionListResponse>({
        path: `/session/v1/list`,
        method: "GET",
        query: query,
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Sessions
     * @name StroomLogout
     * @summary Logout of Stroom session
     * @request GET:/session/v1/logout
     * @secure
     */
    stroomLogout: (query: { redirect_uri: string }, params: RequestParams = {}) =>
      this.request<any, UrlResponse>({
        path: `/session/v1/logout`,
        method: "GET",
        query: query,
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Sessions
     * @name ValidateStroomSession
     * @summary Validate the current session, return a redirect Uri if invalid.
     * @request GET:/session/v1/noauth/validateSession
     * @secure
     */
    validateStroomSession: (query: { redirect_uri: string }, params: RequestParams = {}) =>
      this.request<any, ValidateSessionResponse>({
        path: `/session/v1/noauth/validateSession`,
        method: "GET",
        query: query,
        secure: true,
        ...params,
      }),
  };
  sessionInfo = {
    /**
     * No description
     *
     * @tags Session Info
     * @name GetSessionInfo
     * @summary Get information for the current session
     * @request GET:/sessionInfo/v1
     * @secure
     */
    getSessionInfo: (params: RequestParams = {}) =>
      this.request<any, SessionInfo>({
        path: `/sessionInfo/v1`,
        method: "GET",
        secure: true,
        ...params,
      }),
  };
  solrIndex = {
    /**
     * No description
     *
     * @tags Solr Indices
     * @name FetchSolrTypes
     * @summary Fetch Solr types
     * @request POST:/solrIndex/v1/fetchSolrTypes
     * @secure
     */
    fetchSolrTypes: (data: SolrIndexDoc, params: RequestParams = {}) =>
      this.request<any, string[]>({
        path: `/solrIndex/v1/fetchSolrTypes`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Solr Indices
     * @name SolrConnectionTest
     * @summary Test connection to Solr
     * @request POST:/solrIndex/v1/solrConnectionTest
     * @secure
     */
    solrConnectionTest: (data: SolrIndexDoc, params: RequestParams = {}) =>
      this.request<any, SolrConnectionTestResponse>({
        path: `/solrIndex/v1/solrConnectionTest`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Solr Indices
     * @name FetchSolrIndex
     * @summary Fetch a solr index doc by its UUID
     * @request GET:/solrIndex/v1/{uuid}
     * @secure
     */
    fetchSolrIndex: (uuid: string, params: RequestParams = {}) =>
      this.request<any, SolrIndexDoc>({
        path: `/solrIndex/v1/${uuid}`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Solr Indices
     * @name UpdateSolrIndex
     * @summary Update a solr index doc
     * @request PUT:/solrIndex/v1/{uuid}
     * @secure
     */
    updateSolrIndex: (uuid: string, data: SolrIndexDoc, params: RequestParams = {}) =>
      this.request<any, SolrIndexDoc>({
        path: `/solrIndex/v1/${uuid}`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  sqlstatistics = {
    /**
     * No description
     *
     * @tags Sql Statistics Query
     * @name GetSqlStatisticsDataSource
     * @summary Submit a request for a data source definition, supplying the DocRef for the data source
     * @request POST:/sqlstatistics/v2/dataSource
     * @secure
     */
    getSqlStatisticsDataSource: (data: DocRef, params: RequestParams = {}) =>
      this.request<any, DataSource>({
        path: `/sqlstatistics/v2/dataSource`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Sql Statistics Query
     * @name DestroySqlStatisticsQuery
     * @summary Destroy a running query
     * @request POST:/sqlstatistics/v2/destroy
     * @secure
     */
    destroySqlStatisticsQuery: (data: QueryKey, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/sqlstatistics/v2/destroy`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Sql Statistics Query
     * @name KeepAliveSqlStatisticsQuery
     * @summary Keep a running query alive
     * @request POST:/sqlstatistics/v2/keepAlive
     * @secure
     */
    keepAliveSqlStatisticsQuery: (data: QueryKey, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/sqlstatistics/v2/keepAlive`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Sql Statistics Query
     * @name StartSqlStatisticsQuery
     * @summary Submit a search request
     * @request POST:/sqlstatistics/v2/search
     * @secure
     */
    startSqlStatisticsQuery: (data: SearchRequest, params: RequestParams = {}) =>
      this.request<any, SearchResponse>({
        path: `/sqlstatistics/v2/search`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  statistic = {
    /**
     * No description
     *
     * @tags SQL Statistics RollUps
     * @name StatisticBitMaskConversion
     * @summary Get rollup bit mask
     * @request POST:/statistic/rollUp/v1/bitMaskConversion
     * @secure
     */
    statisticBitMaskConversion: (data: number[], params: RequestParams = {}) =>
      this.request<any, CustomRollUpMaskFields[]>({
        path: `/statistic/rollUp/v1/bitMaskConversion`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags SQL Statistics RollUps
     * @name StatisticBitMaskPermGeneration
     * @summary Create rollup bit mask
     * @request POST:/statistic/rollUp/v1/bitMaskPermGeneration
     * @secure
     */
    statisticBitMaskPermGeneration: (data: number, params: RequestParams = {}) =>
      this.request<any, CustomRollUpMask[]>({
        path: `/statistic/rollUp/v1/bitMaskPermGeneration`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags SQL Statistics RollUps
     * @name StatisticFieldChange
     * @summary Change fields
     * @request POST:/statistic/rollUp/v1/dataSourceFieldChange
     * @secure
     */
    statisticFieldChange: (data: StatisticsDataSourceFieldChangeRequest, params: RequestParams = {}) =>
      this.request<any, StatisticsDataSourceData>({
        path: `/statistic/rollUp/v1/dataSourceFieldChange`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags SQL Statistics Stores
     * @name FetchStatisticStore
     * @summary Fetch a statistic doc by its UUID
     * @request GET:/statistic/v1/{uuid}
     * @secure
     */
    fetchStatisticStore: (uuid: string, params: RequestParams = {}) =>
      this.request<any, StatisticStoreDoc>({
        path: `/statistic/v1/${uuid}`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags SQL Statistics Stores
     * @name UpdateStatisticStore
     * @summary Update a statistic doc
     * @request PUT:/statistic/v1/{uuid}
     * @secure
     */
    updateStatisticStore: (uuid: string, data: StatisticStoreDoc, params: RequestParams = {}) =>
      this.request<any, StatisticStoreDoc>({
        path: `/statistic/v1/${uuid}`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  statsStore = {
    /**
     * No description
     *
     * @tags Stroom Stats RollUps
     * @name StatsBitMaskConversion
     * @summary Get rollup bit mask
     * @request POST:/statsStore/rollUp/v1/bitMaskConversion
     * @secure
     */
    statsBitMaskConversion: (data: number[], params: RequestParams = {}) =>
      this.request<any, ResultPageCustomRollUpMaskFields>({
        path: `/statsStore/rollUp/v1/bitMaskConversion`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Stroom Stats RollUps
     * @name StatsBitMaskPermGeneration
     * @summary Create rollup bit mask
     * @request POST:/statsStore/rollUp/v1/bitMaskPermGeneration
     * @secure
     */
    statsBitMaskPermGeneration: (data: number, params: RequestParams = {}) =>
      this.request<any, ResultPageCustomRollUpMask>({
        path: `/statsStore/rollUp/v1/bitMaskPermGeneration`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Stroom Stats RollUps
     * @name StatsFieldChange
     * @summary Change fields
     * @request POST:/statsStore/rollUp/v1/dataSourceFieldChange
     * @secure
     */
    statsFieldChange: (data: StroomStatsStoreFieldChangeRequest, params: RequestParams = {}) =>
      this.request<any, StroomStatsStoreEntityData>({
        path: `/statsStore/rollUp/v1/dataSourceFieldChange`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Stroom Stats Stores
     * @name FetchStroomStatsStore
     * @summary Fetch a store doc doc by its UUID
     * @request GET:/statsStore/v1/{uuid}
     * @secure
     */
    fetchStroomStatsStore: (uuid: string, params: RequestParams = {}) =>
      this.request<any, StroomStatsStoreDoc>({
        path: `/statsStore/v1/${uuid}`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Stroom Stats Stores
     * @name UpdateStroomStatsStore
     * @summary Update a stats store doc
     * @request PUT:/statsStore/v1/{uuid}
     * @secure
     */
    updateStroomStatsStore: (uuid: string, data: StroomStatsStoreDoc, params: RequestParams = {}) =>
      this.request<any, StroomStatsStoreDoc>({
        path: `/statsStore/v1/${uuid}`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  stepping = {
    /**
     * No description
     *
     * @tags Stepping
     * @name FindElementDoc
     * @summary Load the document for an element
     * @request POST:/stepping/v1/findElementDoc
     * @secure
     */
    findElementDoc: (data: FindElementDocRequest, params: RequestParams = {}) =>
      this.request<any, DocRef>({
        path: `/stepping/v1/findElementDoc`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Stepping
     * @name GetPipelineForStepping
     * @summary Get a pipeline for stepping
     * @request POST:/stepping/v1/getPipelineForStepping
     * @secure
     */
    getPipelineForStepping: (data: GetPipelineForMetaRequest, params: RequestParams = {}) =>
      this.request<any, DocRef>({
        path: `/stepping/v1/getPipelineForStepping`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Stepping
     * @name Step
     * @summary Step a pipeline
     * @request POST:/stepping/v1/step
     * @secure
     */
    step: (data: PipelineStepRequest, params: RequestParams = {}) =>
      this.request<any, SteppingResult>({
        path: `/stepping/v1/step`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  storedQuery = {
    /**
     * No description
     *
     * @tags Stored Queries
     * @name CreateStoredQuery
     * @summary Create a stored query
     * @request POST:/storedQuery/v1/create
     * @secure
     */
    createStoredQuery: (data: StoredQuery, params: RequestParams = {}) =>
      this.request<any, StoredQuery>({
        path: `/storedQuery/v1/create`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Stored Queries
     * @name DeleteStoredQuery
     * @summary Delete a stored query
     * @request DELETE:/storedQuery/v1/delete
     * @secure
     */
    deleteStoredQuery: (data: StoredQuery, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/storedQuery/v1/delete`,
        method: "DELETE",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Stored Queries
     * @name FindStoredQueries
     * @summary Find stored queries
     * @request POST:/storedQuery/v1/find
     * @secure
     */
    findStoredQueries: (data: FindStoredQueryCriteria, params: RequestParams = {}) =>
      this.request<any, ResultPageStoredQuery>({
        path: `/storedQuery/v1/find`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Stored Queries
     * @name FetchStoredQuery
     * @summary Fetch a stored query
     * @request POST:/storedQuery/v1/read
     * @secure
     */
    fetchStoredQuery: (data: StoredQuery, params: RequestParams = {}) =>
      this.request<any, StoredQuery>({
        path: `/storedQuery/v1/read`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Stored Queries
     * @name UpdateStoredQuery
     * @summary Update a stored query
     * @request PUT:/storedQuery/v1/update
     * @secure
     */
    updateStoredQuery: (data: StoredQuery, params: RequestParams = {}) =>
      this.request<any, StoredQuery>({
        path: `/storedQuery/v1/update`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  stroomElasticIndex = {
    /**
     * No description
     *
     * @tags Elasticsearch Queries
     * @name GetElasticIndexDataSource
     * @summary Submit a request for a data source definition, supplying the DocRef for the data source
     * @request POST:/stroom-elastic-index/v2/dataSource
     * @secure
     */
    getElasticIndexDataSource: (data: DocRef, params: RequestParams = {}) =>
      this.request<any, DataSource>({
        path: `/stroom-elastic-index/v2/dataSource`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Elasticsearch Queries
     * @name DestroyElasticIndexQuery
     * @summary Destroy a running query
     * @request POST:/stroom-elastic-index/v2/destroy
     * @secure
     */
    destroyElasticIndexQuery: (data: QueryKey, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/stroom-elastic-index/v2/destroy`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Elasticsearch Queries
     * @name KeepAliveElasticIndexQuery
     * @summary Keep a running query alive
     * @request POST:/stroom-elastic-index/v2/keepAlive
     * @secure
     */
    keepAliveElasticIndexQuery: (data: QueryKey, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/stroom-elastic-index/v2/keepAlive`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Elasticsearch Queries
     * @name SearchElasticIndex
     * @summary Submit a search request
     * @request POST:/stroom-elastic-index/v2/search
     * @secure
     */
    searchElasticIndex: (data: SearchRequest, params: RequestParams = {}) =>
      this.request<any, SearchResponse>({
        path: `/stroom-elastic-index/v2/search`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  stroomIndex = {
    /**
     * No description
     *
     * @tags Stroom-Index Queries
     * @name GetStroomIndexDataSource
     * @summary Submit a request for a data source definition, supplying the DocRef for the data source
     * @request POST:/stroom-index/v2/dataSource
     * @secure
     */
    getStroomIndexDataSource: (data: DocRef, params: RequestParams = {}) =>
      this.request<any, DataSource>({
        path: `/stroom-index/v2/dataSource`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Stroom-Index Queries
     * @name DestroyStroomIndexQuery
     * @summary Destroy a running query
     * @request POST:/stroom-index/v2/destroy
     * @secure
     */
    destroyStroomIndexQuery: (data: QueryKey, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/stroom-index/v2/destroy`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Stroom-Index Queries
     * @name KeepAliveStroomIndexQuery
     * @summary Keep a running query alive
     * @request POST:/stroom-index/v2/keepAlive
     * @secure
     */
    keepAliveStroomIndexQuery: (data: QueryKey, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/stroom-index/v2/keepAlive`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Stroom-Index Queries
     * @name StartStroomIndexQuery
     * @summary Submit a search request
     * @request POST:/stroom-index/v2/search
     * @secure
     */
    startStroomIndexQuery: (data: SearchRequest, params: RequestParams = {}) =>
      this.request<any, SearchResponse>({
        path: `/stroom-index/v2/search`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  stroomSolrIndex = {
    /**
     * No description
     *
     * @tags Solr Queries
     * @name GetSolrIndexDataSource
     * @summary Submit a request for a data source definition, supplying the DocRef for the data source
     * @request POST:/stroom-solr-index/v2/dataSource
     * @secure
     */
    getSolrIndexDataSource: (data: DocRef, params: RequestParams = {}) =>
      this.request<any, DataSource>({
        path: `/stroom-solr-index/v2/dataSource`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Solr Queries
     * @name DestroySolrIndexQuery
     * @summary Destroy a running query
     * @request POST:/stroom-solr-index/v2/destroy
     * @secure
     */
    destroySolrIndexQuery: (data: QueryKey, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/stroom-solr-index/v2/destroy`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Solr Queries
     * @name KeepAliveSolrIndexQuery
     * @summary Keep a running query alive
     * @request POST:/stroom-solr-index/v2/keepAlive
     * @secure
     */
    keepAliveSolrIndexQuery: (data: QueryKey, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/stroom-solr-index/v2/keepAlive`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Solr Queries
     * @name StartSolrIndexQuery
     * @summary Submit a search request
     * @request POST:/stroom-solr-index/v2/search
     * @secure
     */
    startSolrIndexQuery: (data: SearchRequest, params: RequestParams = {}) =>
      this.request<any, SearchResponse>({
        path: `/stroom-solr-index/v2/search`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  suggest = {
    /**
     * No description
     *
     * @tags Suggestions
     * @name FetchSuggestions
     * @summary Fetch some suggestions
     * @request POST:/suggest/v1
     * @secure
     */
    fetchSuggestions: (data: FetchSuggestionsRequest, params: RequestParams = {}) =>
      this.request<any, string[]>({
        path: `/suggest/v1`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  systemInfo = {
    /**
     * No description
     *
     * @tags System Info
     * @name GetAllSystemInfo
     * @summary Get all system info results
     * @request GET:/systemInfo/v1
     * @secure
     */
    getAllSystemInfo: (params: RequestParams = {}) =>
      this.request<any, SystemInfoResultList>({
        path: `/systemInfo/v1`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags System Info
     * @name GetSystemInfoNames
     * @summary Get all system info result names
     * @request GET:/systemInfo/v1/names
     * @secure
     */
    getSystemInfoNames: (params: RequestParams = {}) =>
      this.request<any, string[]>({
        path: `/systemInfo/v1/names`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags System Info
     * @name GetSystemInfoParams
     * @summary Gets the parameters for this system info provider
     * @request GET:/systemInfo/v1/params/{name}
     * @secure
     */
    getSystemInfoParams: (name: string, params: RequestParams = {}) =>
      this.request<any, ParamInfo[]>({
        path: `/systemInfo/v1/params/${name}`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags System Info
     * @name GetSystemInfoByName
     * @summary Get a system info result by name
     * @request GET:/systemInfo/v1/{name}
     * @secure
     */
    getSystemInfoByName: (name: string, params: RequestParams = {}) =>
      this.request<any, SystemInfoResult>({
        path: `/systemInfo/v1/${name}`,
        method: "GET",
        secure: true,
        ...params,
      }),
  };
  task = {
    /**
     * No description
     *
     * @tags Tasks
     * @name FindTasks
     * @summary Finds tasks for a node
     * @request POST:/task/v1/find/{nodeName}
     * @secure
     */
    findTasks: (nodeName: string, data: FindTaskProgressRequest, params: RequestParams = {}) =>
      this.request<any, TaskProgressResponse>({
        path: `/task/v1/find/${nodeName}`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Tasks
     * @name ListTasks
     * @summary Lists tasks for a node
     * @request GET:/task/v1/list/{nodeName}
     * @secure
     */
    listTasks: (nodeName: string, params: RequestParams = {}) =>
      this.request<any, TaskProgressResponse>({
        path: `/task/v1/list/${nodeName}`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Tasks
     * @name TerminateTasks
     * @summary Terminates tasks for a node
     * @request POST:/task/v1/terminate/{nodeName}
     * @secure
     */
    terminateTasks: (nodeName: string, data: TerminateTaskProgressRequest, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/task/v1/terminate/${nodeName}`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Tasks
     * @name ListUserTasks
     * @summary Lists tasks for a node
     * @request GET:/task/v1/user/{nodeName}
     * @secure
     */
    listUserTasks: (nodeName: string, params: RequestParams = {}) =>
      this.request<any, TaskProgressResponse>({
        path: `/task/v1/user/${nodeName}`,
        method: "GET",
        secure: true,
        ...params,
      }),
  };
  textConverter = {
    /**
     * No description
     *
     * @tags Text Converters
     * @name FetchTextConverter
     * @summary Fetch a text converter doc by its UUID
     * @request GET:/textConverter/v1/{uuid}
     * @secure
     */
    fetchTextConverter: (uuid: string, params: RequestParams = {}) =>
      this.request<any, TextConverterDoc>({
        path: `/textConverter/v1/${uuid}`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Text Converters
     * @name UpdateTextConverter
     * @summary Update a text converter doc
     * @request PUT:/textConverter/v1/{uuid}
     * @secure
     */
    updateTextConverter: (uuid: string, data: TextConverterDoc, params: RequestParams = {}) =>
      this.request<any, TextConverterDoc>({
        path: `/textConverter/v1/${uuid}`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  userNames = {
    /**
     * No description
     *
     * @tags Authorisation
     * @name FindUserNames
     * @summary Find the user names matching the supplied criteria
     * @request POST:/userNames/v1/find
     * @secure
     */
    findUserNames: (data: FindUserNameCriteria, params: RequestParams = {}) =>
      this.request<any, ResultPageString>({
        path: `/userNames/v1/find`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  users = {
    /**
     * No description
     *
     * @tags Authorisation
     * @name FindUsers
     * @summary Find the users matching the supplied criteria
     * @request GET:/users/v1
     * @secure
     */
    findUsers: (query?: { name?: string; isGroup?: boolean; uuid?: string }, params: RequestParams = {}) =>
      this.request<any, User[]>({
        path: `/users/v1`,
        method: "GET",
        query: query,
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Authorisation
     * @name GetAssociatedUsers
     * @summary Gets a list of associated users
     * @request GET:/users/v1/associates
     * @secure
     */
    getAssociatedUsers: (query?: { filter?: string }, params: RequestParams = {}) =>
      this.request<any, string[]>({
        path: `/users/v1/associates`,
        method: "GET",
        query: query,
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Authorisation
     * @name CreateUser
     * @summary Creates a user or group with the supplied name
     * @request POST:/users/v1/create/{name}/{isGroup}
     * @secure
     */
    createUser: (name: string, isGroup: boolean, params: RequestParams = {}) =>
      this.request<any, User>({
        path: `/users/v1/create/${name}/${isGroup}`,
        method: "POST",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Authorisation
     * @name FindUsersByCriteria
     * @summary Find the users matching the supplied criteria
     * @request POST:/users/v1/find
     * @secure
     */
    findUsersByCriteria: (data: FindUserCriteria, params: RequestParams = {}) =>
      this.request<any, ResultPageUser>({
        path: `/users/v1/find`,
        method: "POST",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Authorisation
     * @name FetchUser
     * @summary Fetches the user with the supplied UUID
     * @request GET:/users/v1/{userUuid}
     * @secure
     */
    fetchUser: (userUuid: string, params: RequestParams = {}) =>
      this.request<any, User>({
        path: `/users/v1/${userUuid}`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Authorisation
     * @name RemoveUserFromGroup
     * @summary Removes user with UUID userUuid from the group with UUID groupUuid
     * @request DELETE:/users/v1/{userUuid}/{groupUuid}
     * @secure
     */
    removeUserFromGroup: (userUuid: string, groupUuid: string, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/users/v1/${userUuid}/${groupUuid}`,
        method: "DELETE",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Authorisation
     * @name AddUserToGroup
     * @summary Adds user with UUID userUuid to the group with UUID groupUuid
     * @request PUT:/users/v1/{userUuid}/{groupUuid}
     * @secure
     */
    addUserToGroup: (userUuid: string, groupUuid: string, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/users/v1/${userUuid}/${groupUuid}`,
        method: "PUT",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Authorisation
     * @name DeleteUser
     * @summary Deletes the user with the supplied UUID
     * @request DELETE:/users/v1/{uuid}
     * @secure
     */
    deleteUser: (uuid: string, params: RequestParams = {}) =>
      this.request<any, boolean>({
        path: `/users/v1/${uuid}`,
        method: "DELETE",
        secure: true,
        ...params,
      }),
  };
  visualisation = {
    /**
     * No description
     *
     * @tags Visualisations
     * @name FetchVisualisation
     * @summary Fetch a visualisation doc by its UUID
     * @request GET:/visualisation/v1/{uuid}
     * @secure
     */
    fetchVisualisation: (uuid: string, params: RequestParams = {}) =>
      this.request<any, VisualisationDoc>({
        path: `/visualisation/v1/${uuid}`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags Visualisations
     * @name UpdateVisualisation
     * @summary Update a visualisation doc
     * @request PUT:/visualisation/v1/{uuid}
     * @secure
     */
    updateVisualisation: (uuid: string, data: VisualisationDoc, params: RequestParams = {}) =>
      this.request<any, VisualisationDoc>({
        path: `/visualisation/v1/${uuid}`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  welcome = {
    /**
     * No description
     *
     * @tags Welcome
     * @name FetchWelcome
     * @summary Get the configured HTML welcome message
     * @request GET:/welcome/v1
     * @secure
     */
    fetchWelcome: (params: RequestParams = {}) =>
      this.request<any, Welcome>({
        path: `/welcome/v1`,
        method: "GET",
        secure: true,
        ...params,
      }),
  };
  xmlSchema = {
    /**
     * No description
     *
     * @tags XML Schemas
     * @name FetchXmlSchema
     * @summary Fetch a xml schema doc by its UUID
     * @request GET:/xmlSchema/v1/{uuid}
     * @secure
     */
    fetchXmlSchema: (uuid: string, params: RequestParams = {}) =>
      this.request<any, XmlSchemaDoc>({
        path: `/xmlSchema/v1/${uuid}`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags XML Schemas
     * @name UpdateXmlSchema
     * @summary Update a xml schema doc
     * @request PUT:/xmlSchema/v1/{uuid}
     * @secure
     */
    updateXmlSchema: (uuid: string, data: XmlSchemaDoc, params: RequestParams = {}) =>
      this.request<any, XmlSchemaDoc>({
        path: `/xmlSchema/v1/${uuid}`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
  xslt = {
    /**
     * No description
     *
     * @tags XSLTs
     * @name FetchXslt
     * @summary Fetch an xslt doc by its UUID
     * @request GET:/xslt/v1/{uuid}
     * @secure
     */
    fetchXslt: (uuid: string, params: RequestParams = {}) =>
      this.request<any, XsltDoc>({
        path: `/xslt/v1/${uuid}`,
        method: "GET",
        secure: true,
        ...params,
      }),

    /**
     * No description
     *
     * @tags XSLTs
     * @name UpdateXslt
     * @summary Update a an xslt doc
     * @request PUT:/xslt/v1/{uuid}
     * @secure
     */
    updateXslt: (uuid: string, data: XsltDoc, params: RequestParams = {}) =>
      this.request<any, XsltDoc>({
        path: `/xslt/v1/${uuid}`,
        method: "PUT",
        body: data,
        secure: true,
        type: ContentType.Json,
        ...params,
      }),
  };
}
