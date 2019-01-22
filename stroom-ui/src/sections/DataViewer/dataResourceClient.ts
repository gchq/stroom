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
    state.dataViewers[dataViewerId] &&
    state.dataViewers[dataViewerId].streamAttributeMaps &&
    selectedRow
      ? state.dataViewers[dataViewerId]!.streamAttributeMaps![selectedRow].data
          .id
      : undefined;

  var url = new URL(`${state.config.values.stroomBaseServiceUrl}/data/v1/`);
  if (!!streamId) url.searchParams.append("streamId", streamId.toString());
  url.searchParams.append("streamsOffset", "0");
  url.searchParams.append("streamsLength", "1");
  url.searchParams.append("pageOffset", "0");
  url.searchParams.append("pageSize", "100");

  wrappedGet(
    dispatch,
    state,
    url.href,
    response => {
      response.json().then((data: AbstractFetchDataResult) => {
        dispatch(actionCreators.updateDataForSelectedRow(dataViewerId, data));
      });
    },
    {},
    true
  );
};
