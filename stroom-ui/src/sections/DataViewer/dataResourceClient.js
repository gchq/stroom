import { actionCreators } from './redux';
import { wrappedGet } from 'lib/fetchTracker.redux';

export const getDataForSelectedRow = dataViewerId => (dispatch, getState) => {
  const state = getState();

  // TODO get other parms, e.g. for paging
  const selectedRow = state.dataViewers[dataViewerId].selectedRow;
  const streamId = state.dataViewers[dataViewerId].streamAttributeMaps[selectedRow].data.id;
  const params = `streamId=${streamId}&streamsOffset=0&streamsLength=1&pageOffset=0&pageSize=100`;

  const url = `${state.config.stroomBaseServiceUrl}/data/v1/?${params}`;

  wrappedGet(
    dispatch,
    state,
    url,
    (response) => {
      response.json().then((data) => {
        dispatch(actionCreators.updateDataForSelectedRow(dataViewerId, data));
      });
    },
    null,
    true,
  );
};
