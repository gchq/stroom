import { actionCreators } from './redux';
import { wrappedGet, wrappedPatch } from 'lib/fetchTracker.redux';

export const search = (dataViewerId, pageOffset, pageSize) => (dispatch, getState) => {
  const state = getState();

  let url = `${state.config.streamAttributeMapServiceUrl}/?`;
  url += `pageSize=${pageSize}`;
  url += `&pageOffset=${pageOffset}`;

  wrappedGet(dispatch, state, url, (response) => {
    response.json().then((data) => {
      dispatch(actionCreators.updateStreamAttributeMaps(
        dataViewerId,
        data.streamAttributeMaps,
        data.pageResponse.total,
      ));
    });
  });
};
