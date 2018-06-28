import { actionCreators } from './redux';
import { wrappedGet } from 'lib/fetchTracker.redux';

const { docTreeReceived, docRefTypesReceived } = actionCreators;

export const fetchDocTree = () => (dispatch, getState) => {
  const state = getState();
  const url = `${state.config.explorerServiceUrl}/all`;
  wrappedGet(dispatch, state, url, docTree => dispatch(docTreeReceived(docTree)));
};

export const fetchDocRefTypes = () => (dispatch, getState) => {
  const state = getState();
  const url = `${state.config.explorerServiceUrl}/docRefTypes`;
  wrappedGet(dispatch, state, url, docRefTypes => dispatch(docRefTypesReceived(docRefTypes)));
};
