import { actionCreators } from "./redux";
import { wrappedGet } from "../../lib/fetchTracker.redux";
import { Dispatch } from "redux";
import { GlobalStoreState } from "../../startup/reducers";
import { AbstractFetchDataResult } from "../../types";

export const getDataForSelectedRow = (dataViewerId: string) => (
  dispatch: Dispatch,
  getState: () => GlobalStoreState
) => {
  const state = getState();

  // TODO get other parms, e.g. for paging
  const selectedRow = state.dataViewers[dataViewerId].selectedRow;
  const streamId =
    state.dataViewers[dataViewerId].streamAttributeMaps[selectedRow].data.id;
  const params = `streamId=${streamId}&streamsOffset=0&streamsLength=1&pageOffset=0&pageSize=100`;

  const url = `${state.config.values.stroomBaseServiceUrl}/data/v1/?${params}`;

  wrappedGet(
    dispatch,
    state,
    url,
    response => {
      response.json().then((data: AbstractFetchDataResult) => {
        dispatch(actionCreators.updateDataForSelectedRow(dataViewerId, data));
      });
    },
    {},
    true
  );
};
