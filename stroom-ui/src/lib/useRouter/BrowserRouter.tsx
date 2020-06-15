import * as React from "react";
import { Router, Route, RouteProps } from "react-router-dom";

import { History } from "history";

export const RouterContext = React.createContext<RouteProps>({});
export const HistoryContext = React.createContext<History | undefined>(
  undefined,
);

export interface ChromeContext {
  includeSidebar: boolean;
  urlPrefix: string;
  setUrlPrefix: (i: string) => void;
}

export const DEFAULT_CHROME_MODE = "withChrome";

export const WithChromeContext = React.createContext<ChromeContext>({
  urlPrefix: DEFAULT_CHROME_MODE,
  includeSidebar: false,
  setUrlPrefix: () =>
    console.error("Setting Include Chrome on Default Implementation"),
});

export const useIncludeSidebar = (urlPrefix: string): boolean => {
  const { includeSidebar, setUrlPrefix } = React.useContext(WithChromeContext);
  React.useEffect(() => setUrlPrefix(urlPrefix), [urlPrefix, setUrlPrefix]);
  return includeSidebar;
};

interface Props {
  history: History;
  children?: React.ReactNode;
}

/**
 * Determine whether or not we want to show the chrome, based on either:
 *  - the requested path from history
 *  - a redirect stored earlier as part of the Oldauthentication flow
 *
 * Which we need depends on whether or not we're already authenticated.
 *
 * FIXME: I'm not certain that there isn't another/better way to do this.
 * FIXME: dynamic paths for not using chrome is not nice.
 * FIXME: 'withChrome' is a horrible path
 * @param pathname The current pathname, i.e. from history.location.pathname
 * @returns An object with 'prefix' and 'includeSidebar' properties.
 */
const showChrome = (pathname: string) => {
  let prefix: string;
  let includeSidebar = false;
  // If we're handling an Oldauthentication redirect then we need to get the path
  // from local storage.
  const referrer = localStorage.getItem("preAuthenticationRequestReferrer");
  const actualPath = referrer ? referrer : pathname;

  const parts = actualPath.split("/");
  prefix = parts[1];
  includeSidebar = prefix === DEFAULT_CHROME_MODE;
  return { includeSidebar, prefix };
};

const CustomRouter: React.FunctionComponent<Props> = ({
  history,
  children,
}) => {
  const [urlPrefix, setUrlPrefixRaw] = React.useState<string>(
    DEFAULT_CHROME_MODE,
  );

  const { includeSidebar, prefix } = showChrome(history.location.pathname);

  const setUrlPrefix = React.useCallback(
    (_urlPrefix = prefix) => setUrlPrefixRaw(_urlPrefix),
    [setUrlPrefixRaw, prefix],
  );

  return (
    <Router history={history}>
      <Route>
        {(routeProps) => (
          <WithChromeContext.Provider
            value={{
              urlPrefix,
              setUrlPrefix,
              includeSidebar: includeSidebar,
            }}
          >
            <HistoryContext.Provider value={history}>
              <RouterContext.Provider value={routeProps}>
                {children}
              </RouterContext.Provider>
            </HistoryContext.Provider>
          </WithChromeContext.Provider>
        )}
      </Route>
    </Router>
  );
};

export default CustomRouter;
