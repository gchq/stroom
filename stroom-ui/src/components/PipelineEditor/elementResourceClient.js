import { actionCreators } from './redux';
import { wrappedGet } from 'lib/fetchTracker.redux';

const { elementsReceived, elementPropertiesReceived } = actionCreators;

export const fetchElements = () => (dispatch, getState) => {
  const state = getState();
  const url = `${state.config.elementServiceUrl}/elements`;
  wrappedGet(dispatch, state, url, response =>
    response.json().then(elements => dispatch(elementsReceived(elements))));
};

export const fetchElementProperties = () => (dispatch, getState) => {
  const state = getState();
  const url = `${state.config.elementServiceUrl}/elementProperties`;
  wrappedGet(dispatch, state, url, response =>
    response
      .json()
      .then(elementProperties => dispatch(elementPropertiesReceived(elementProperties))));
};
