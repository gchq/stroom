import * as React from "react";

import useApi from "./useApi";
import { Activity } from "./types";

/**
 * Convenience function for using Index Volume.
 * This hook connects the REST API calls to the Redux Store.
 */
interface UseActivities {
  activities: Activity[];
  createActivity: (activity: Activity) => void;
  updateActivity: (activity: Activity) => void;
  deleteActivity: (id: string) => void;
}

interface ReceiveAction {
  type: "received";
  activities: Activity[];
}

interface CreateAction {
  type: "created";
  activity: Activity;
}
interface UpdateAction {
  type: "updated";
  activity: Activity;
}
interface DeleteAction {
  type: "deleted";
  id: string;
}

const reducer = (
  state: Activity[],
  action: ReceiveAction | CreateAction | UpdateAction | DeleteAction,
): Activity[] => {
  switch (action.type) {
    case "received":
      return action.activities;
    case "created":
      return state.concat([action.activity]);
    case "updated":
      return state.concat([action.activity]);
    case "deleted":
      return state.filter((v) => v.id !== action.id);
    default:
      return state;
  }
};

const useActivities = (): UseActivities => {
  const [activities, dispatch] = React.useReducer(reducer, []);

  const {
    getActivities,
    createActivity,
    updateActivity,
    deleteActivity,
  } = useApi();

  React.useEffect(() => {
    getActivities().then((v) =>
      dispatch({
        type: "received",
        activities: v,
      }),
    );
  }, [dispatch, getActivities]);

  return {
    activities,
    createActivity: React.useCallback(
      (activity: Activity) =>
        createActivity(activity).then((activity) =>
          dispatch({
            type: "created",
            activity,
          }),
        ),
      [createActivity],
    ),
    updateActivity: React.useCallback(
      (activity: Activity) =>
        updateActivity(activity).then((activity) =>
          dispatch({
            type: "updated",
            activity,
          }),
        ),
      [updateActivity],
    ),
    deleteActivity: React.useCallback(
      (id: string) =>
        deleteActivity(id).then(() =>
          dispatch({
            type: "deleted",
            id,
          }),
        ),
      [deleteActivity],
    ),
  };
};

export default useActivities;
