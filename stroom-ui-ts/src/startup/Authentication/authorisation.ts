import { createActions, handleActions } from "redux-actions";
import { Dispatch } from "redux";

export const SET_APP_PERMISSION = "authorisation/SET_APP_PERMISSION";

export interface StoreState {
  appPermissions: Array<string>;
}

export interface StoreAction {
  appPermission: string;
  hasAppPermission: boolean;
}

const defaultState = {
  appPermissions: []
};

export const actionCreators = createActions<StoreAction>({
  SET_APP_PERMISSION: (appPermission, hasAppPermission) => ({
    appPermission,
    hasAppPermission
  })
});

export const reducer = handleActions<StoreState, StoreAction>(
  {
    SET_APP_PERMISSION: (state, action) =>
      Object.assign(state, {
        appPermissions: Object.assign(state.appPermissions, {
          [action.payload!.appPermission]: action.payload!.hasAppPermission
        })
      })
  },
  defaultState
);

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
