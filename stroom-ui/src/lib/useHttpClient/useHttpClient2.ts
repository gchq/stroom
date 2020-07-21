import * as React from "react";
import { useCallback } from "react";

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

type HttpGet = (url: string) => Promise<any>;
type HttpPost = (url: string, object: any) => Promise<any>;
type HttpPut = (url: string, object: any) => Promise<any>;
type HttpDelete = (url: string) => Promise<any>;

interface HttpClient2 {
  httpGet: HttpGet;
  httpPost: HttpPost;
  httpPut: HttpPut;
  httpDelete: HttpDelete;
}

export const useHttpClient2 = (): HttpClient2 => {
  // const { idToken } = useAuthenticationContext();
  const { showError } = usePrompt();

  // const { reportError } = useErrorReporting();
  // const {
  //   nav: { goToError },
  // } = useAppNavigation();

  const handle200 = useCheckStatus(200);
  // const handle204 = useCheckStatus(204);

  const catchImpl = React.useCallback(
    (error: any) => {
      const msg = `Error, Status ${error.status}, Msg: ${error.message}`;
      console.log(msg);
      if (error.message !== undefined) {
        try {
          const json = JSON.parse(error.message);
          showError({ message: json.message });
        } catch (e) {
          showError({ message: error.message });
        }
      } else {
        showError({ message: msg });
      }
    },
    [showError],
  );

  const httpGet = useCallback<HttpGet>(
    <T>(url: string): Promise<T | void> => {
      const headers = {
        Accept: "application/json",
        "Content-Type": "application/json",
      };

      const options = {};

      return fetch(url, {
        mode: "cors",
        credentials: "include",
        ...options,
        method: "get",
        headers,
      })
        .then(handle200)
        .then((r) => r.json())
        .catch(catchImpl);
    },
    [handle200, catchImpl],
  );

  const httpPost = useCallback<HttpPost>(
    <T>(url: string, object: any): Promise<T | void> => {
      const headers = {
        Accept: "application/json",
        "Content-Type": "application/json",
      };

      const options = {
        body: JSON.stringify(object),
      };

      return fetch(url, {
        mode: "cors",
        credentials: "include",
        ...options,
        method: "post",
        headers,
      })
        .then(handle200)
        .then((r) => r.json())
        .catch(catchImpl);
    },
    [handle200, catchImpl],
  );

  const httpPut = useCallback<HttpPut>(
    <T>(url: string, object: any): Promise<T | void> => {
      const headers = {
        Accept: "application/json",
        "Content-Type": "application/json",
      };

      const options = {
        body: JSON.stringify(object),
      };

      return fetch(url, {
        mode: "cors",
        credentials: "include",
        ...options,
        method: "put",
        headers,
      })
        .then(handle200)
        .then((r) => r.json())
        .catch(catchImpl);
    },
    [handle200, catchImpl],
  );

  const httpDelete = useCallback<HttpDelete>(
    <T>(url: string): Promise<T | void> => {
      const headers = {
        Accept: "application/json",
        "Content-Type": "application/json",
      };

      const options = {};

      return fetch(url, {
        mode: "cors",
        credentials: "include",
        ...options,
        method: "delete",
        headers,
      })
        .then(handle200)
        .then((r) => r.json())
        .catch(catchImpl);
    },
    [handle200, catchImpl],
  );

  return {
    httpGet,
    httpPost,
    httpPut,
    httpDelete,
  };
};

export default useHttpClient2;
