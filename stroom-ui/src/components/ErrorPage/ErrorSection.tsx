import * as React from "react";

export interface Props {
  title: string;
  errorData: string | number;
}

const ErrorSection = ({ title, errorData }: Props) => (
  <div className="ErrorPage__section">
    <strong>{title}: </strong>
    <code>{errorData}</code>
  </div>
);

export default ErrorSection;
