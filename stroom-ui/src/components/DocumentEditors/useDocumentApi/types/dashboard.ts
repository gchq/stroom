import { DocumentBase, DocRefType } from "./base";
import { ExpressionOperatorType } from "components/ExpressionBuilder/types";

// 'query'
export interface Automate {
  open: boolean;
  refresh: boolean;
  refreshInterval: string;
}

export interface QueryComponentSettings {
  dataSource: DocRefType;
  expression: ExpressionOperatorType;
  automate: Automate;
}

// 'table'
export enum SortDirection {
  ASCENDING = "Ascending",
  DESCENDING = "Descending",
}
export interface Sort {
  order: number;
  direction: SortDirection;
}

export interface Filter {
  includes: string;
  excludes: string;
}

export enum FormatType {
  GENERAL = "General",
  NUMBER = "Number",
  DATE_TIME = "Date Time",
  TEXT = "Text",
}

export interface NumberFormatSettings {
  decimalPlaces: number;
  useSeparator: boolean;
}

export enum TimeZoneUse {
  LOCAL = "Local",
  UTC = "UTC",
  ID = "Id",
  OFFSET = "Offset",
}

export interface TimeZone {
  id: number;
  use: TimeZoneUse;
  offsetHours: number;
  offsetMinutes: number;
}

export interface DateTimeFormatSettings {
  pattern: string;
  timeZone: TimeZone;
}

export interface Format {
  type: FormatType;
  settings: NumberFormatSettings | DateTimeFormatSettings;
  wrap: boolean;
}

export interface TableComponentField {
  name: string;
  expression: string;
  sort: Sort;
  filter: Filter;
  format: Format;
  group: number;
  width: number;
  visible: boolean;
}
export interface TableComponentSettings {
  queryId: string;
  fields: TableComponentField[];
  extractValues: boolean;
  extractionPipeline: DocRefType;
  maxResults: number[];
  showDetail: boolean;
}

// 'vis'
export interface VisComponentSettings {
  tableId: string;
  visualisation: DocRefType;
  json: string;
  tableSettings: TableComponentSettings;
}

// 'text'
export interface TextComponentSettings {
  tableId: string;
  pipeline: DocRefType;
  showAsHtml: boolean;
}

export type ComponentSettings =
  | QueryComponentSettings
  | TableComponentSettings
  | VisComponentSettings
  | TextComponentSettings;

export interface ComponentConfig {
  type: string;
  id: string;
  name: string;
  settings: ComponentSettings;
}

export interface Size {
  size: number[];
}

export interface TabConfig {
  id: number;
  componentSettings: ComponentSettings;
}

// 'splitLayout'
export interface SplitLayoutConfig {
  preferredSize: Size;
  dimension: number;
  children: (SplitLayoutConfig | TabLayoutConfig)[];
}

// 'tabLayout'
export interface TabLayoutConfig {
  preferredSize: Size;
  tabs: TabConfig[];
  selected: number;
}

export type LayoutConfig = SplitLayoutConfig | TabLayoutConfig;

export enum TabVisibility {
  SHOW_ALL = "Show All",
  HIDE_SINGLE = "Hide Single Tabs",
  HIDE_ALL = "Hide All",
}

export interface DashboardConfig {
  parameters: string;
  components: ComponentConfig[];
  layout: LayoutConfig;
  tabVisibility: TabVisibility;
}

export interface DashboardDoc extends DocumentBase<"Dashboard"> {
  description?: string;
  dashboardConfig: DashboardConfig;
}
