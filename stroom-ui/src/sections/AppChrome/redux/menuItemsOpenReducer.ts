import { Action, ActionCreator } from "redux";

import { prepareReducer } from "../../../lib/redux-actions-ts";

export const MENU_ITEM_OPENED = "MENU_ITEM_OPENED";

export interface MenuItemOpenedAction extends Action<"MENU_ITEM_OPENED"> {
  key: string;
  isOpen: boolean;
}

export interface ActionCreators {
  menuItemOpened: ActionCreator<MenuItemOpenedAction>;
}

export const actionCreators: ActionCreators = {
  menuItemOpened: (key, isOpen) => ({
    type: MENU_ITEM_OPENED,
    key,
    isOpen
  })
};

export interface StoreState {
  [s: string]: boolean;
}

const defaultState: StoreState = {};

export const reducer = prepareReducer(defaultState)
  .handleAction<MenuItemOpenedAction>(
    MENU_ITEM_OPENED,
    (state, { key, isOpen }) => ({
      ...state,
      [key]: isOpen
    })
  )
  .getReducer();
