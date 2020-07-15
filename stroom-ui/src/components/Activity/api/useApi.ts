import * as React from "react";
import { useCallback } from "react";
import useHttpClient from "lib/useHttpClient";
import { Activity } from "./types";
import useUrlFactory from "lib/useUrlFactory";

interface UseApi {
  // Get the current activity for the summary.
  getCurrentActivity: () => Promise<Activity>;
  setCurrentActivity: (activity: Activity) => Promise<Activity>;

  // Manage activities.
  getActivities: () => Promise<Activity[]>;

  // Activity CRUD
  createActivity: (activity: Activity) => Promise<Activity>;
  getActivity: (id: string) => Promise<Activity>;
  updateActivity: (activity: Activity) => Promise<Activity>;
  deleteActivity: (id: string) => Promise<void>;
}

export const useApi = (): UseApi => {
  const {
    httpGetJson,
    httpPostJsonResponse,
    httpDeleteEmptyResponse,
  } = useHttpClient();
  const { apiUrl } = useUrlFactory();
  const resource = apiUrl("/activity/v1");
  return {
    getCurrentActivity: useCallback(() => httpGetJson(`${resource}/current`), [
      resource,
      httpGetJson,
    ]),
    setCurrentActivity: useCallback(
      (activity: Activity) =>
        httpPostJsonResponse(`${resource}/current`, {
          body: JSON.stringify({ activity }),
        }),
      [resource, httpPostJsonResponse],
    ),

    getActivities: React.useCallback(() => httpGetJson(resource), [
      resource,
      httpGetJson,
    ]),

    createActivity: React.useCallback(
      (activity: Activity) =>
        httpPostJsonResponse(resource, {
          body: JSON.stringify({ activity }),
        }),
      [resource, httpPostJsonResponse],
    ),
    getActivity: React.useCallback(
      (id: string) => httpGetJson(`${resource}/${id}`),
      [resource, httpGetJson],
    ),
    updateActivity: React.useCallback(
      (activity: Activity) =>
        httpPostJsonResponse(resource, {
          body: JSON.stringify({ activity }),
        }),
      [resource, httpPostJsonResponse],
    ),
    deleteActivity: React.useCallback(
      (id: string) => httpDeleteEmptyResponse(`${resource}/${id}`),
      [resource, httpDeleteEmptyResponse],
    ),
  };
};
