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

export const SEARCH_RESULTS_RETURNED = "SEARCH_RESULTS_RETURNED";

export interface SearchResultsReturnedAction
  extends Action<"SEARCH_RESULTS_RETURNED">,
    ActionId {
  searchResults: Array<DocRefType>;
}

export const actionCreators = {
  searchResultsReturned: (
    id: string,
    searchResults: Array<DocRefType>
  ): SearchResultsReturnedAction => ({
    type: SEARCH_RESULTS_RETURNED,
    id,
    searchResults
  })
};

export type StoreStatePerId = Array<DocRefType>;

export type StoreState = StateById<StoreStatePerId>;

export const defaultStatePerId: StoreStatePerId = [];

export const reducer = prepareReducerById(defaultStatePerId)
  .handleAction<SearchResultsReturnedAction>(
    SEARCH_RESULTS_RETURNED,
    (state: StoreStatePerId, { searchResults }) => searchResults
  )
  .getReducer();
