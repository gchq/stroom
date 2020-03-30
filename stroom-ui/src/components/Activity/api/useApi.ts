import * as React from "react";
import { useCallback } from "react";
import useHttpClient from "lib/useHttpClient";
import useConfig from "startup/config/useConfig";
import { Activity } from "./types";

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

const useApi = (): UseApi => {
  const {
    httpGetJson,
    httpPostJsonResponse,
    httpDeleteEmptyResponse,
  } = useHttpClient();
  const { stroomBaseServiceUrl } = useConfig();
  return {
    getCurrentActivity: useCallback(
      () => httpGetJson(`${stroomBaseServiceUrl}/activity/v1/current`),
      [stroomBaseServiceUrl, httpGetJson],
    ),
    setCurrentActivity: useCallback(
      (activity: Activity) =>
        httpPostJsonResponse(`${stroomBaseServiceUrl}/activity/v1/current`, {
          body: JSON.stringify({ activity }),
        }),
      [stroomBaseServiceUrl, httpPostJsonResponse],
    ),

    getActivities: React.useCallback(
      () => httpGetJson(`${stroomBaseServiceUrl}/activity/v1`),
      [stroomBaseServiceUrl, httpGetJson],
    ),

    createActivity: React.useCallback(
      (activity: Activity) =>
        httpPostJsonResponse(`${stroomBaseServiceUrl}/activity/v1`, {
          body: JSON.stringify({ activity }),
        }),
      [stroomBaseServiceUrl, httpPostJsonResponse],
    ),
    getActivity: React.useCallback(
      (id: string) => httpGetJson(`${stroomBaseServiceUrl}/activity/v1/${id}`),
      [stroomBaseServiceUrl, httpGetJson],
    ),
    updateActivity: React.useCallback(
      (activity: Activity) =>
        httpPostJsonResponse(`${stroomBaseServiceUrl}/activity/v1`, {
          body: JSON.stringify({ activity }),
        }),
      [stroomBaseServiceUrl, httpPostJsonResponse],
    ),
    deleteActivity: React.useCallback(
      (id: string) =>
        httpDeleteEmptyResponse(`${stroomBaseServiceUrl}/activity/v1/${id}`),
      [stroomBaseServiceUrl, httpDeleteEmptyResponse],
    ),
  };
};

export default useApi;
