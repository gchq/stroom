/*
 * Copyright 2018 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import * as React from "react";

/* import ExpressionOperator from "./ExpressionOperator"; */
import { DataSourceType, ExpressionOperatorType } from "./types";
import { LineContainer } from "../LineTo";
import useToggle from "lib/useToggle";
/* import ReadOnlyExpressionOperator from "./ReadOnlyExpressionOperator"; */
import ElbowDown from "../LineTo/lineCreators/ElbowDown";

interface Props {
  className?: string;
  dataSource?: DataSourceType;
  showModeToggle?: boolean;
  editMode?: boolean;
  value: ExpressionOperatorType;
  onChange?: (e: ExpressionOperatorType) => void;
}

const defaultOnChange = (e: ExpressionOperatorType) =>
  console.error("Cannot edit expression without valid onChange", e);

const ExpressionBuilder: React.FunctionComponent<Props> = ({
  dataSource,
  showModeToggle,
  editMode,
  value,
  onChange = defaultOnChange,
}) => {
  const { value: inEditMode, toggle: toggleEditMode } = useToggle(editMode);

  return (
    <LineContainer LineElementCreator={ElbowDown}>
      {showModeToggle && !!dataSource && (
        <React.Fragment>
          <label>Edit Mode</label>
          <input
            type="checkbox"
            checked={inEditMode}
            onChange={toggleEditMode}
          />
        </React.Fragment>
      )}

      {/* dnd_error: temporarily disable dnd-related code to get the build working */}
      {/* {inEditMode && !!dataSource ? (
            <ExpressionOperator {...{ dataSource, value, onChange }} />
            ) : (
            <ReadOnlyExpressionOperator {...{ value }} />
            )} */}
    </LineContainer>
  );
};

export default ExpressionBuilder;
