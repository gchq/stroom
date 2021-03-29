import * as React from "react";

export interface Props {
  value: any;
}

const JsonDebug: React.FunctionComponent<Props> = ({ value }) => (
  <div style={{ margin: "3rem 0" }}>
    <hr />
    <h1>Debug information</h1>
    <pre
      style={{
        fontSize: "1rem",
        padding: ".25rem .5rem",
        overflowX: "auto",
      }}
    >
      {JSON.stringify(value, null, 2)}
    </pre>
  </div>
);
export default JsonDebug;
