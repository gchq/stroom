import { Action, ActionCreator } from "redux";

import { prepareReducer } from "../../../lib/redux-actions-ts";
import { groupByCategory, keyByType } from "../elementUtils";
import {
  ElementDefinitions,
  ElementPropertiesByElementIdType,
  ElementDefinitionsByCategory,
  ElementDefinitionsByType
} from "../../../types";

export const ELEMENTS_RECEIVED = "ELEMENTS_RECEIVED";
export const ELEMENT_PROPERTIES_RECEIVED = "ELEMENT_PROPERTIES_RECEIVED";

export interface ElementsReceivedAction extends Action<"ELEMENTS_RECEIVED"> {
  elements: ElementDefinitions;
}

export interface ElementPropertiesReceivedAction
  extends Action<"ELEMENT_PROPERTIES_RECEIVED"> {
  elementProperties: ElementPropertiesByElementIdType;
}

export interface ActionCreators {
  elementsReceived: ActionCreator<ElementsReceivedAction>;
  elementPropertiesReceived: ActionCreator<ElementPropertiesReceivedAction>;
}

export interface StoreState {
  elements: ElementDefinitions;
  elementProperties: ElementPropertiesByElementIdType;
  byCategory: ElementDefinitionsByCategory;
  byType: ElementDefinitionsByType;
}

export const defaultState: StoreState = {
  elements: [],
  elementProperties: {},
  byCategory: {},
  byType: {}
};

export const actionCreators: ActionCreators = {
  elementsReceived: elements => ({
    type: ELEMENTS_RECEIVED,
    elements
  }),
  elementPropertiesReceived: elementProperties => ({
    type: ELEMENT_PROPERTIES_RECEIVED,
    elementProperties
  })
};

export const reducer = prepareReducer(defaultState)
  .handleAction<ElementsReceivedAction>(
    ELEMENTS_RECEIVED,
    (state = defaultState, { elements }) => ({
      ...state,
      elements,
      byCategory: groupByCategory(elements),
      byType: keyByType(elements)
    })
  )
  .handleAction<ElementPropertiesReceivedAction>(
    ELEMENT_PROPERTIES_RECEIVED,
    (state = defaultState, { elementProperties }) => ({
      ...state,
      elementProperties
    })
  )
  .getReducer();
