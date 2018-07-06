import { actionCreators } from './redux/explorerTreeReducer';
import { wrappedGet } from 'lib/fetchTracker.redux';

const { docTreeReceived, docRefTypesReceived } = actionCreators;

export const fetchDocTree = () => (dispatch, getState) => {
  const state = getState();
  const url = `${state.config.explorerServiceUrl}/all`;
  wrappedGet(dispatch, state, url, response =>
    response.json().then(documentTree => dispatch(docTreeReceived(documentTree))));
};

export const fetchDocRefTypes = () => (dispatch, getState) => {
  const state = getState();
  const url = `${state.config.explorerServiceUrl}/docRefTypes`;
  wrappedGet(dispatch, state, url, response =>
    response.json().then(docRefTypes => dispatch(docRefTypesReceived(docRefTypes))));
};
