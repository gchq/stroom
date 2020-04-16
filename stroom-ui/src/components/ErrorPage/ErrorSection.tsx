import * as React from "react";

interface Props {
  title: string;
  errorData: string | number;
}

const ErrorSection: React.FunctionComponent<Props> = ({ title, errorData }) => (
  <div className="ErrorPage__section">
    <strong>{title}: </strong>
    <code>{errorData}</code>
  </div>
);

export default ErrorSection;
