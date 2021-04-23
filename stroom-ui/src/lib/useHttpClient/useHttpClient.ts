import * as React from "react";

import { HttpError } from "lib/ErrorTypes";
import { usePrompt } from "components/Prompt/PromptDisplayBoundary";

const useCheckStatus = (status: number) =>
  React.useCallback(
    (response: Response): Promise<any> => {
      //       console.log(response.headers.get("Content-Type"));
      //       console.log(response.headers.get("Date"));
      //       console.log(response.status);
      //       console.log(response.statusText);

      if (response.status === status) {
        return Promise.resolve(response);
      }

      return response.text().then((text) => {
        console.log(
          "Expected HTTP status " +
            status +
            " but received " +
            response.status +
            " - " +
            response.statusText,
          " (" + response.url + ") " + text,
        );

        let message = text;
        if (!message) {
          message = response.statusText;
        }
        if (!message) {
          message = "Incorrect HTTP Response Code";
        }

        return Promise.reject(new HttpError(response.status, message));
      });
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
  const { showError } = usePrompt();

  const handle200 = useCheckStatus(200);
  const handle204 = useCheckStatus(204);

  const catchImpl = React.useCallback(
    (error: any) => {
      const msg = `Error, Status ${error.status}, Msg: ${error.message}`;
      console.log(msg);
      showError({ message: error.message });
    },
    [showError],
  );

  const httpGetJson = React.useCallback(
    <T>(
      url: string,
      options: {
        [s: string]: any;
      } = {},
      forceGet = true, // default to true, take care with settings this to false, old promises can override the updated picture with old information if this is mis-used
    ): Promise<T | void> => {
      const headers = {
        Accept: "application/json",
        "Content-Type": "application/json",
        ...(options ? options.headers : {}),
      };

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
          .then((r) => r.json())
          //           .then(r => {
          //             try {
          //               return r.json();
          // //               console.log(r.headers.get("Content-Type"));
          // //               console.log(r.headers.get("Date"));
          // //               console.log(r.status);
          // //               console.log(r.statusText);
          // //
          // //               return r.text();
          //             } catch (e) {
          //               console.error(e);
          //               throw e;
          //             }
          //           })
          //           .then(text => {
          //             try {
          //               return JSON.parse(text);
          //             } catch (e) {
          //               console.error(e);
          //             }
          //
          //             return text;
          //
          //           })
          .catch(catchImpl);
      }

      return cache[url];
    },
    [catchImpl, handle200],
  );

  const useFetchWithBodyAndJsonResponse = (method: string) =>
    React.useCallback(
      <T>(
        url: string,
        options?: {
          [s: string]: any;
        },
      ): Promise<T | void> => {
        const headers = {
          Accept: "application/json",
          "Content-Type": "application/json",
          ...(options ? options.headers : {}),
        };

        return (
          fetch(url, {
            mode: "cors",
            credentials: "include",
            ...options,
            method,
            headers,
          })
            .then(handle200)
            .then((r) => r.json())
            //           .then(r => {
            //             try {
            //               console.log(r.headers.get("Content-Type"));
            //               console.log(r.headers.get("Date"));
            //               console.log(r.status);
            //               console.log(r.statusText);
            //
            //               return r.text();
            //             } catch (e) {
            //               console.error(e);
            //               throw e;
            //             }
            //           })
            //           .then(text => {
            //             try {
            //               return JSON.parse(text);
            //             } catch (e) {
            //               console.error(e);
            //             }
            //
            //             return text;
            //
            //           })
            .catch(catchImpl)
        );
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
      ): Promise<Response | void> => {
        const headers = {
          "Content-Type": "application/json",
          ...(options ? options.headers : {}),
        };

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
