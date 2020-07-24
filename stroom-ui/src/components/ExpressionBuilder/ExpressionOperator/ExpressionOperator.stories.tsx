import * as React from "react";

import { storiesOf } from "@storybook/react";
// import JsonDebug from "testing/JsonDebug";
// import ExpressionOperator from ".";
// import { getNewOperator } from "../expressionUtils";
// import { ExpressionOperatorType } from "../types";
// import { testDataSource as dataSource } from "../test";
// import Button from "components/Button";
// import useToggle from "lib/useToggle";
import { LineContainer } from "components/LineTo";

// const newOperator: ExpressionOperatorType = getNewOperator();

const TestHarness: React.FunctionComponent = () => {
  // const [index, onIndexChange] = React.useState<number>(67);
  // const [value, onValueChange] = React.useState<ExpressionOperatorType>(
  //   newOperator,
  // );
  // const [deletedId, onDelete] = React.useState<number | undefined>(undefined);

  // const resetDelete = React.useCallback(() => onDelete(undefined), [onDelete]);
  // const { value: isEnabled, toggle: toggleIsEnabled } = useToggle(true);

  // const onChange = React.useCallback(
  //   (_value: ExpressionOperatorType, _index: number) => {
  //     onIndexChange(_index);
  //     onValueChange(_value);
  //   },
  //   [onIndexChange, onValueChange],
  // );

  return (
    <LineContainer>
      {/* dnd_error: temporarily disable dnd-related code to get the build working */}
      {/* <ExpressionOperator
            {...{ isEnabled, onDelete, dataSource, value, onChange }}
            /> */}
      {/*<Button text="Toggle Parent Enable" onClick={toggleIsEnabled} />*/}
      {/*<Button text="Reset Delete" onClick={resetDelete} />*/}
      {/*<JsonDebug value={{ index, value, isEnabled, deletedId }} />*/}
    </LineContainer>
  );
};

storiesOf("Expression", module).add("Operator", () => <TestHarness />);
