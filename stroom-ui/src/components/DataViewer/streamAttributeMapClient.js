import { actionCreators } from './redux';
import { wrappedGet } from 'lib/fetchTracker.redux';

export const search = (dataViewerId, pageOffset, pageSize) => (dispatch, getState) => {
  const state = getState();

  let url = `${state.config.streamAttributeMapServiceUrl}/?`;
  url += `pageSize=${pageSize}`;
  url += `&pageOffset=${pageOffset}`;

  wrappedGet(
    dispatch,
    state,
    url,
    (response) => {
      response.json().then((data) => {
        dispatch(actionCreators.updateStreamAttributeMaps(
          dataViewerId,
          data.streamAttributeMaps,
          data.pageResponse.total,
          pageSize,
          pageOffset,
        ));
      });
    },
    null,
    true,
  );
};

export const getDetailsForSelectedRow = dataViewerId => (dispatch, getState) => {
  const state = getState();
  const dataView = state.dataViewers[dataViewerId];
  const streamId = dataView.streamAttributeMaps[dataView.selectedRow].stream.id;
  const url = `${state.config.streamAttributeMapServiceUrl}/${streamId}`;

  wrappedGet(
    dispatch,
    state,
    url,
    (response) => {
      response.json().then((data) => {
        dispatch(actionCreators.updateDetailsForSelectedRow(dataViewerId, data));
      });
    },
    null,
    true,
  );
};
