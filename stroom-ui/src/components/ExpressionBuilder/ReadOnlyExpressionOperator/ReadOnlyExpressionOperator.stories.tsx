import * as React from "react";
import { storiesOf } from "@storybook/react";
import ReadOnlyExpressionOperator from ".";
import { testExpression } from "../test";
import { LineContainer } from "components/LineTo";
import ElbowDown from "components/LineTo/lineCreators/ElbowDown";

const TestHarness: React.FunctionComponent = () => {
  return (
    <LineContainer LineElementCreator={ElbowDown}>
      <ReadOnlyExpressionOperator value={testExpression} />
    </LineContainer>
  );
};

storiesOf("Expression/Read Only Operator", module).add("react", () => (
  <TestHarness />
));
