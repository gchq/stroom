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
  availableChildStreamTypes: string[];
}

export interface FetchDataResult extends AbstractFetchDataResult {
  data: string;
  html: boolean;
}

export const isFetchDataResult = (
  result: AnyFetchDataResult,
): result is FetchDataResult => !!(result as any).data;

export interface FetchMarkerResult extends AbstractFetchDataResult {
  markers: AnyMarker[];
}

export const isFetchMarkerResult = (
  result: AnyFetchDataResult,
): result is FetchMarkerResult => !!(result as any).markers;

export type AnyFetchDataResult = FetchDataResult | FetchMarkerResult;

export type Severity = "INFO" | "WARN" | "ERROR" | "FATAL";

export interface AbstractMarker {
  severity: Severity;
}

export interface StoredError extends AbstractMarker {
  elementId: string;
  location: Location;
  message: string;
}

export interface Expander {
  depth: number;
  expanded: boolean;
  leaf: boolean;
}

export interface Summary extends AbstractMarker {
  count: number;
  total: number;
  expander: Expander;
}

export type AnyMarker = Summary | StoredError;

export const isStoredErrorMarker = (marker: AnyMarker): marker is StoredError =>
  !!(marker as any).elementId;
export const isSummaryMarker = (marker: AnyMarker): marker is Summary =>
  !!(marker as any).expander;

export interface Location {
  streamNo: number;
  lineNo: number;
  colNo: number;
}

export interface FetchDataParams {
  metaId?: number;
  pageSize?: number;
  pageOffset?: number;
}

export interface UseData {
  data: AnyFetchDataResult;
  getDataForSelectedRow: () => void;
}
