import * as React from "react";

import { HttpError } from "lib/ErrorTypes";
import { useAlert } from "components/AlertDialog/AlertDisplayBoundary";
import { AlertType } from "components/AlertDialog/AlertDialog";
import { useCallback } from "react";

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

      return response.text().then(text => {
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

type HttpPost = (url: string, object: any) => Promise<any>;

interface HttpClient2 {
  post: HttpPost;
}

export const useHttpClient2 = (): HttpClient2 => {
  // const { idToken } = useAuthenticationContext();
  const { alert } = useAlert();

  // const { reportError } = useErrorReporting();
  // const {
  //   nav: { goToError },
  // } = useAppNavigation();

  const handle200 = useCheckStatus(200);
  // const handle204 = useCheckStatus(204);

  const catchImpl = React.useCallback(
    (error: any) => {
      const msg = `Error, Status ${error.status}, Msg: ${error.message}`;
      alert({ type: AlertType.ERROR, title: "Error", message: msg });
    },
    [alert],
  );

  const post = useCallback<HttpPost>(
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
        .then(r => r.json())
        .catch(catchImpl);
    },
    [fetch],
  );

  return {
    post,
  };
};

export default useHttpClient2;
