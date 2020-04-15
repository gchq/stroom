import * as React from "react";

import { HttpError } from "lib/ErrorTypes";
import cogoToast from "cogo-toast";

import { useAuthenticationContext } from "startup/Authentication";
import { useErrorReporting } from "components/ErrorPage";
import useAppNavigation from "lib/useAppNavigation";

const useCheckStatus = (status: number) =>
  React.useCallback(
    (response: Response): Promise<any> => {
      if (response.status === status) {
        return Promise.resolve(response);
      }
      return Promise.reject(
        new HttpError(
          response.status,
          response.statusText || "Incorrect HTTP Response Code",
        ),
      );
    },
    [status],
  );

/**
 * A wrapper around HTTP fetch that allows us to plop in idTokens, CORS specifications,
 * and general common stuff like that.
 */

type HttpCall = (
  url: string,
  options?: {
    [s: string]: any;
  },
  forceGet?: boolean,
  addAuthentication?: boolean,
) => Promise<any>;

interface HttpClient {
  httpGetJson: (
    url: string,
    options?: {
      [s: string]: any;
    },
    forceGet?: boolean,
    addAuthentication?: boolean,
  ) => Promise<any>;
  httpGetEmptyResponse: HttpCall;
  httpPostJsonResponse: HttpCall;
  httpPutJsonResponse: HttpCall;
  httpDeleteJsonResponse: HttpCall;
  httpPatchJsonResponse: HttpCall;
  httpPostEmptyResponse: HttpCall;
  httpPutEmptyResponse: HttpCall;
  httpDeleteEmptyResponse: HttpCall;
  httpPatchEmptyResponse: HttpCall;
  clearCache: () => void;
}

// Cache GET Promises by URL -- Effectively static/global to the application
let cache = {};

export const useHttpClient = (): HttpClient => {
  const { idToken } = useAuthenticationContext();
  const { reportError } = useErrorReporting();
  const {
    nav: { goToError },
  } = useAppNavigation();

  const handle200 = useCheckStatus(200);
  const handle204 = useCheckStatus(204);

  const catchImpl = React.useCallback(
    (error: any) => {
      const msg = `Error, Status ${error.status}, Msg: ${error.message}`;
      cogoToast.error(msg, {
        hideAfter: 5,
        onClick: () => {
          reportError({
            errorMessage: error.message,
            stackTrace: error.stack,
            httpErrorCode: error.status,
          });
          goToError();
        },
      });
    },
    [reportError, goToError],
  );

  const httpGetJson = React.useCallback(
    <T>(
      url: string,
      options: {
        [s: string]: any;
      } = {},
      forceGet: boolean = true, // default to true, take care with settings this to false, old promises can override the updated picture with old information if this is mis-used
      addAuthentication: boolean = false, // most of the time we want authenticated requests, so we'll make that the default.
    ): Promise<T | void> => {
      if (!idToken && addAuthentication) {
        let p = Promise.reject();
        p.catch(() => console.log("Missing ID Token, not making request"));
        return p;
      }

      let headers = {
        Accept: "application/json",
        "Content-Type": "application/json",
        ...(options ? options.headers : {}),
      };

      if (addAuthentication) {
        headers.Authorization = `Bearer ${idToken}`;
      }

      // If we do not have an entry in the cache or we are forcing GET, create a new call
      if (!cache[url] || forceGet) {
        cache[url] = fetch(url, {
          method: "get",
          mode: "cors",
          credentials: "include",
          ...options,
          headers,
        })
          .then(handle200)
          .then(r => {
            try {
              return r.json();
            } catch (e) {
              console.error(e);
              throw e;
            }
          })
          .catch(catchImpl);
      }

      return cache[url];
    },
    [catchImpl, idToken, handle200],
  );

  const useFetchWithBodyAndJsonResponse = (method: string) =>
    React.useCallback(
      <T>(
        url: string,
        options?: {
          [s: string]: any;
        },
        forceGet: boolean = true, // default to true, take care with settings this to false, old promises can override the updated picture with old information if this is mis-used
        addAuthentication: boolean = true, // most of the time we want authenticated requests, so we'll make that the default.
      ): Promise<T | void> => {
        if (!idToken && addAuthentication) {
          const p = Promise.reject();
          p.catch(() => console.log("Missing ID Token, not making request"));
          return p;
        }

        const headers = {
          Accept: "application/json",
          "Content-Type": "application/json",
          ...(options ? options.headers : {}),
        };

        if (addAuthentication) {
          headers.Authorization = `Bearer ${idToken}`;
        }

        return fetch(url, {
          mode: "cors",
          credentials: "include",
          ...options,
          method,
          headers,
        })
          .then(handle200)
          .then(r => r.json())
          .catch(catchImpl);
      },
      [method],
    );

  const useFetchWithBodyAndEmptyResponse = (method: string) =>
    React.useCallback(
      (
        url: string,
        options?: {
          [s: string]: any;
        },
        addAuthentication = true, // most of the time we want authenticated requests, so we'll make that the default.
      ): Promise<Response | void> => {
        if (!idToken && addAuthentication) {
          const p = Promise.reject();
          p.catch(() => console.log("Missing ID Token, not making request"));
          return p;
        }

        const headers = {
          "Content-Type": "application/json",
          ...(options ? options.headers : {}),
        };

        if (addAuthentication) {
          headers.Authorization = `Bearer ${idToken}`;
        }

        return fetch(url, {
          mode: "cors",
          credentials: "include",
          ...options,
          method,
          headers,
        })
          .then(handle204)
          .catch(catchImpl);
      },
      [method],
    );

  return {
    httpGetJson,
    httpGetEmptyResponse: useFetchWithBodyAndEmptyResponse("get"),
    httpPostJsonResponse: useFetchWithBodyAndJsonResponse("post"),
    httpPutJsonResponse: useFetchWithBodyAndJsonResponse("put"),
    httpDeleteJsonResponse: useFetchWithBodyAndJsonResponse("delete"),
    httpPatchJsonResponse: useFetchWithBodyAndJsonResponse("patch"),
    httpPostEmptyResponse: useFetchWithBodyAndEmptyResponse("post"),
    httpPutEmptyResponse: useFetchWithBodyAndEmptyResponse("put"),
    httpDeleteEmptyResponse: useFetchWithBodyAndEmptyResponse("delete"),
    httpPatchEmptyResponse: useFetchWithBodyAndEmptyResponse("patch"),
    clearCache: () => {
      cache = {};
    },
  };
};

export default useHttpClient;
