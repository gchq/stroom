import { Dispatch, Action } from "redux";

import { prepareReducer } from "../../lib/redux-actions-ts";

export const SET_APP_PERMISSION = "authorisation/SET_APP_PERMISSION";

export interface StoreState {
  appPermissions: Array<string>;
}

export interface SetAppPermissionAction
  extends Action<"authorisation/SET_APP_PERMISSION"> {
  appPermission: string;
  hasAppPermission: boolean;
}

const defaultState = {
  appPermissions: []
};

export const actionCreators = {
  setAppPermission: (
    appPermission: string,
    hasAppPermission: boolean
  ): SetAppPermissionAction => ({
    type: SET_APP_PERMISSION,
    appPermission,
    hasAppPermission
  })
};

export const reducer = prepareReducer(defaultState)
  .handleAction<SetAppPermissionAction>(
    SET_APP_PERMISSION,
    (state = defaultState, { appPermission, hasAppPermission }) =>
      Object.assign(state, {
        appPermissions: Object.assign(state.appPermissions, {
          [appPermission]: hasAppPermission
        })
      })
  )
  .getReducer();

const setHasAppPermission = (
  appPermission: string,
  hasAppPermission: boolean
) => ({
  type: SET_APP_PERMISSION,
  appPermission,
  hasAppPermission
});

export const hasAppPermission = (
  idToken: string,
  authorisationServiceUrl: string,
  appPermission: string
) => (dispatch: Dispatch) => {
  const hasAppPermissionUrl = `${authorisationServiceUrl}/hasAppPermission`;
  return fetch(hasAppPermissionUrl, {
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
      Authorization: `Bearer ${idToken}`
    },
    method: "post",
    mode: "cors",
    body: JSON.stringify({
      permission: appPermission
    })
  }).then(response => {
    if (response.status === 401) {
      dispatch(setHasAppPermission(appPermission, false));
    } else if (response.status === 200) {
      dispatch(setHasAppPermission(appPermission, true));
    } else {
      console.log(
        `Unknown response from the authorisation service! ${response}`
      );
    }
  });
};
