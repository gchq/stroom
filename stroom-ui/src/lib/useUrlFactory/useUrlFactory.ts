import * as React from "react";

const apiPath = process.env.REACT_APP_API_PATH;

interface UrlFactory {
  apiUrl: (path: string) => string;
}

export const useUrlFactory = (): UrlFactory => {
  const apiUrl = React.useCallback(<T>(path: string): string => {
    if (path.startsWith("/")) {
      return apiPath + path;
    }
    return apiPath + "/" + path;
  }, []);

  return {
    apiUrl,
  };
};

export default useUrlFactory;
