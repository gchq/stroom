import { Action } from "redux";

import {
  prepareReducerById,
  ActionId,
  StateById
} from "../../lib/redux-actions-ts";
import { DocRefType } from "../../types";

export enum SearchMode {
  GLOBAL_SEARCH,
  NAVIGATION,
  RECENT_ITEMS
}

export const SWITCH_MODE = "SWITCH_MODE";
export const NAVIGATE_TO_FOLDER = "NAVIGATE_TO_FOLDER";
export const SEARCH_TERM_UPDATED = "SEARCH_TERM_UPDATED";
export const SEARCH_RESULTS_RETURNED = "SEARCH_RESULTS_RETURNED";

export interface SwitchModeAction extends Action<"SWITCH_MODE">, ActionId {
  searchMode: SearchMode;
}

export interface NavigateToFolderAction
  extends Action<"NAVIGATE_TO_FOLDER">,
    ActionId {
  navFolder: DocRefType;
}
export interface SearchTermUpdatedAction
  extends Action<"SEARCH_TERM_UPDATED">,
    ActionId {
  searchTerm: string;
}
export interface SearchResultsReturnedAction
  extends Action<"SEARCH_RESULTS_RETURNED">,
    ActionId {
  searchResults: Array<DocRefType>;
}

export const actionCreators = {
  switchMode: (id: string, searchMode: SearchMode): SwitchModeAction => ({
    type: SWITCH_MODE,
    id,
    searchMode
  }),
  navigateToFolder: (
    id: string,
    navFolder: DocRefType
  ): NavigateToFolderAction => ({
    type: NAVIGATE_TO_FOLDER,
    id,
    navFolder
  }),
  searchTermUpdated: (
    id: string,
    searchTerm: string
  ): SearchTermUpdatedAction => ({
    type: SEARCH_TERM_UPDATED,
    id,
    searchTerm
  }),
  searchResultsReturned: (
    id: string,
    searchResults: Array<DocRefType>
  ): SearchResultsReturnedAction => ({
    type: SEARCH_RESULTS_RETURNED,
    id,
    searchResults
  })
};

export interface StoreStatePerId {
  searchTerm: string;
  searchResults: Array<any>;
  navFolder?: DocRefType;
  searchMode: SearchMode;
}

export type StoreState = StateById<StoreStatePerId>;

export const defaultStatePerId: StoreStatePerId = {
  searchTerm: "",
  searchResults: [],
  navFolder: undefined,
  searchMode: SearchMode.NAVIGATION
};

export const reducer = prepareReducerById(defaultStatePerId)
  .handleAction<SwitchModeAction>(
    SWITCH_MODE,
    (state: StoreStatePerId, { searchMode }) => ({
      ...state,
      searchMode
    })
  )
  .handleAction<NavigateToFolderAction>(
    NAVIGATE_TO_FOLDER,
    (state: StoreStatePerId, { navFolder }) => ({
      ...state,
      navFolder
    })
  )
  .handleAction<SearchTermUpdatedAction>(
    SEARCH_TERM_UPDATED,
    (state: StoreStatePerId, { searchTerm }) => ({
      ...state,
      searchTerm,
      searchMode:
        searchTerm.length > 0 ? SearchMode.GLOBAL_SEARCH : SearchMode.NAVIGATION
    })
  )
  .handleAction<SearchResultsReturnedAction>(
    SEARCH_RESULTS_RETURNED,
    (state: StoreStatePerId, { searchResults }) => ({
      ...state,
      searchResults
    })
  )
  .getReducer();
