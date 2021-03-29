import * as React from "react";
import { SingleError } from "./types";

interface ErrorReportingContextState {
  error?: SingleError;
  reportError: (e: SingleError) => void;
}

export default React.createContext<ErrorReportingContextState>({
  error: {
    errorMessage: "",
    stackTrace: "",
    httpErrorCode: 0,
  },
  reportError: (e) =>
    console.error("Reporting error to disconnected default context", e),
});
