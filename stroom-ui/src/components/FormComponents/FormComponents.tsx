import * as React from "react";
import styled from "styled-components";
import { Tooltip } from "antd";

const requiredFieldText = "This field is required";

export const ValidationMessage = styled.span`
  color: red;
`;
//TODO are these used?
export const RequiredFieldMessage = () => (
  <ValidationMessage>{requiredFieldText}</ValidationMessage>
);

export const OptionalRequiredFieldMessage: React.FunctionComponent<{
  visible: boolean;
}> = ({ visible }) => {
  if (visible) {
    return (
      <ValidationMessage>{requiredFieldText}</ValidationMessage>
    );
  }

  return (
    <ValidationMessage>&nbsp;</ValidationMessage>
  );
};

export const ErrorMessage = (message: string) => (
  <ValidationMessage>{message}</ValidationMessage>
);

export const MandatoryIndicator = () => (
  <Tooltip title={requiredFieldText}>
    <ValidationMessage>* </ValidationMessage>
  </Tooltip>
);
