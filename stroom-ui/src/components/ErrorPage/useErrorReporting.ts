import * as React from "react";
import ErrorReportingContext, {
  ErrorReportingContextState,
} from "./ErrorReportingContext";

const useErrorReporting = (): ErrorReportingContextState =>
  React.useContext(ErrorReportingContext);

export default useErrorReporting;
