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

import { storiesOf } from "@storybook/react";

import ExpressionBuilder from "./ExpressionBuilder";

import { testExpression, testDataSource, simplestExpression } from "./test";

import { ExpressionOperatorType } from "./types";
import { useCallback } from "react";

const PopulatedEditable = () => {
  const [value, onChange] = React.useState<ExpressionOperatorType>(
    testExpression,
  );
  const handleChange = useCallback(value => onChange(value), [onChange]);

  return (
    <ExpressionBuilder
      value={value}
      onChange={handleChange}
      showModeToggle
      editMode
      dataSource={testDataSource}
    />
  );
};

const PopulatedReadOnly = () => {
  const [value, onChange] = React.useState<ExpressionOperatorType>(
    testExpression,
  );

  return (
    <ExpressionBuilder
      onChange={onChange}
      value={value}
      dataSource={testDataSource}
    />
  );
};

const SimplestEditable = () => {
  const [value, onChange] = React.useState<ExpressionOperatorType>(
    simplestExpression,
  );

  return (
    <ExpressionBuilder
      onChange={onChange}
      value={value}
      showModeToggle
      dataSource={testDataSource}
    />
  );
};

const MissingDataSource = () => {
  const [value, onChange] = React.useState<ExpressionOperatorType>(
    testExpression,
  );

  return (
    <ExpressionBuilder
      onChange={onChange}
      value={value}
      dataSource={testDataSource}
    />
  );
};

const HideModeToggle = () => {
  const [value, onChange] = React.useState<ExpressionOperatorType>(
    testExpression,
  );

  return (
    <ExpressionBuilder
      onChange={onChange}
      value={value}
      showModeToggle={false}
      dataSource={testDataSource}
    />
  );
};

const HideModeButEdit = () => {
  const [value, onChange] = React.useState<ExpressionOperatorType>(
    testExpression,
  );

  return (
    <ExpressionBuilder
      onChange={onChange}
      value={value}
      showModeToggle={false}
      editMode
      dataSource={testDataSource}
    />
  );
};

storiesOf("Expression/Builder", module)
  .add("Populated Editable", PopulatedEditable)
  .add("Populated ReadOnly", PopulatedReadOnly)
  .add("Simplest Editable", SimplestEditable)
  .add("Missing Data Source (read only)", MissingDataSource)
  .add("Hide mode toggle", HideModeToggle)
  .add("Hide mode toggle but be in edit mode", HideModeButEdit);
