import * as React from "react";

interface Props {
  debuggerId: string;
}

const DebuggerStep: React.FunctionComponent<Props> = ({ debuggerId }) => (
  <div>TODO: debugger step information {debuggerId}</div>
);

export default DebuggerStep;
