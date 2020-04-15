import { StreamTaskType } from "../types";

export enum TrackerSelection {
  first = "first",
  last = "last",
  none = "none",
}
export enum Directions {
  ascending = "ascending",
  descending = "descending",
}
export type SortByOptions = "Pipeline" | "Priority";

export interface FetchParameters {
  pageOffset: number;
  pageSize: number;
  sortBy: SortByOptions;
  sortDirection: Directions;
  searchCriteria: string;
}
export interface PagedTrackerInfo {
  trackers: StreamTaskType[];
  totalTrackers: number;
  numberOfPages: number;
}

export interface UseStreamTasks {
  fetchParameters: FetchParameters;
  pagedTrackerInfo: PagedTrackerInfo;
  updateFetchParameters: (params: Partial<FetchParameters>) => void;
  fetchTrackers: () => void;
  fetchMore: () => void;
  enableToggle: (filterId: number) => void;
  updateSort: (sortBy: SortByOptions, sortDirection: Directions) => void;
  updateEnabled: (filterId: number, enabled: boolean) => void;
  updateSearchCriteria: (searchCriteria: string) => void;
  changePage: (pageOffset: number) => void;
  updatePageSize: (pageSize: number) => void;
  resetPaging: () => void;
  pageRight: () => void;
  pageLeft: () => void;
}
