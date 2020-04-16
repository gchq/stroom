import * as React from "react";
import ErrorReportingContext from "./ErrorReportingContext";

const useErrorReporting = () => React.useContext(ErrorReportingContext);

export default useErrorReporting;
