import { combineReducers } from "redux";

import {
  reducer as menuItemsOpenReducer,
  actionCreators as menuItemsOpenActionCreators,
  StoreState as MenuItemsStoreState
} from "./menuItemsOpenReducer";

export const actionCreators = {
  ...menuItemsOpenActionCreators
};

export const reducer = combineReducers({
  areMenuItemsOpen: menuItemsOpenReducer
});

export interface StoreState {
  areMenuItemsOpen: MenuItemsStoreState;
}
