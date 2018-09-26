import { actionCreators } from "./redux";
import { wrappedGet } from "../../lib/fetchTracker.redux";
import { Dispatch } from "redux";
import { GlobalStoreState } from "../../startup/reducers";
import { ElementPropertyTypes, ElementDefinition } from "../../types";

const { elementsReceived, elementPropertiesReceived } = actionCreators;

export const fetchElements = () => (
  dispatch: Dispatch,
  getState: () => GlobalStoreState
) => {
  const state = getState();
  const url = `${
    state.config.values.stroomBaseServiceUrl
  }/elements/v1/elements`;
  wrappedGet(dispatch, state, url, response =>
    response
      .json()
      .then((elements: Array<ElementDefinition>) =>
        dispatch(elementsReceived(elements))
      )
  );
};

export const fetchElementProperties = () => (
  dispatch: Dispatch,
  getState: () => GlobalStoreState
) => {
  const state = getState();
  const url = `${
    state.config.values.stroomBaseServiceUrl
  }/elements/v1/elementProperties`;
  wrappedGet(dispatch, state, url, response =>
    response
      .json()
      .then((elementProperties: ElementPropertyTypes) =>
        dispatch(elementPropertiesReceived(elementProperties))
      )
  );
};
