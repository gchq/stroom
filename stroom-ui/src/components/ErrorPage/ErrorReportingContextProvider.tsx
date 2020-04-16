import * as React from "react";

import { SingleError } from "./types";
import ErrorReportingContext from "./ErrorReportingContext";

const ErrorReportingContextProvider: React.FunctionComponent = ({
  children,
}) => {
  const [error, reportError] = React.useState<SingleError | undefined>(
    undefined,
  );

  return (
    <ErrorReportingContext.Provider value={{ error, reportError }}>
      {children}
    </ErrorReportingContext.Provider>
  );
};

export default ErrorReportingContextProvider;
