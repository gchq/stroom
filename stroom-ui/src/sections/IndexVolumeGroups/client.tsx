import { Dispatch } from "redux";
import { GlobalStoreState } from "src/startup/reducers";

import { actionCreators } from "./redux";
import {
  wrappedGet,
  wrappedPost,
  wrappedDelete
} from "../../lib/fetchTracker.redux";
import { IndexVolumeGroup } from "src/types";

const {
  indexVolumeGroupNamesReceived,
  indexVolumeGroupsReceived,
  indexVolumeGroupReceived,
  indexVolumeGroupCreated,
  indexVolumeGroupDeleted
} = actionCreators;

export const getIndexVolumeGroupNames = () => (
  dispatch: Dispatch,
  getState: () => GlobalStoreState
) => {
  const state = getState();

  var url = new URL(
    `${
      state.config.values.stroomBaseServiceUrl
    }/stroom-index/volumeGroup/v1/names`
  );

  wrappedGet(
    dispatch,
    state,
    url.href,
    r =>
      r
        .json()
        .then((groupNames: Array<string>) =>
          dispatch(indexVolumeGroupNamesReceived(groupNames))
        ),
    {},
    true
  );
};

export const getIndexVolumeGroups = () => (
  dispatch: Dispatch,
  getState: () => GlobalStoreState
) => {
  const state = getState();

  var url = new URL(
    `${state.config.values.stroomBaseServiceUrl}/stroom-index/volumeGroup/v1`
  );

  wrappedGet(
    dispatch,
    state,
    url.href,
    r =>
      r
        .json()
        .then((indexVolumeGroups: Array<IndexVolumeGroup>) =>
          dispatch(indexVolumeGroupsReceived(indexVolumeGroups))
        ),
    {},
    true
  );
};

export const getIndexVolumeGroup = (name: string) => (
  dispatch: Dispatch,
  getState: () => GlobalStoreState
) => {
  const state = getState();

  var url = new URL(
    `${
      state.config.values.stroomBaseServiceUrl
    }/stroom-index/volumeGroup/v1/${name}`
  );

  wrappedGet(
    dispatch,
    state,
    url.href,
    r =>
      r
        .json()
        .then((indexVolumeGroup: IndexVolumeGroup) =>
          dispatch(indexVolumeGroupReceived(indexVolumeGroup))
        ),
    {},
    true
  );
};

export const createIndexVolumeGroup = (name: string) => (
  dispatch: Dispatch,
  getState: () => GlobalStoreState
) => {
  const state = getState();

  var url = new URL(
    `${
      state.config.values.stroomBaseServiceUrl
    }/stroom-index/volumeGroup/v1/${name}`
  );

  wrappedPost(dispatch, state, url.href, response =>
    response
      .json()
      .then((indexVolumeGroup: IndexVolumeGroup) =>
        dispatch(indexVolumeGroupCreated(indexVolumeGroup))
      )
  );
};

export const deleteIndexVolumeGroup = (name: string) => (
  dispatch: Dispatch,
  getState: () => GlobalStoreState
) => {
  const state = getState();

  var url = new URL(
    `${
      state.config.values.stroomBaseServiceUrl
    }/stroom-index/volumeGroup/v1/${name}`
  );

  wrappedDelete(dispatch, state, url.href, response =>
    response.text().then(() => dispatch(indexVolumeGroupDeleted(name)))
  );
};
