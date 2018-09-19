import * as React from "react";

export interface SectionProps {
  title: string;
  errorData: string | number;
}

const ErrorSection = ({ title, errorData }: SectionProps) => (
  <div className="ErrorPage__section">
    <strong>{title}: </strong>
    <code>{errorData}</code>
  </div>
);

export default ErrorSection;
