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
import { connect } from "react-redux";
import { compose, lifecycle } from "recompose";

import { actionCreators } from "./redux";
import { ExpressionOperatorType } from "../../types";
import { GlobalStoreState } from "../../startup/reducers";

const { expressionChanged } = actionCreators;

import { storiesOf } from "@storybook/react";

import StroomDecorator from "../../lib/storybook/StroomDecorator";
import ExpressionBuilder, {
  Props as ExpressionBuilderProps
} from "./ExpressionBuilder";

import "../../styles/main.css";

import { testExpression, simplestExpression, testDataSource } from "./test";

interface Props extends ExpressionBuilderProps {
  testExpression: ExpressionOperatorType;
}

interface ConnectState {}
interface ConnectDispatch {
  expressionChanged: typeof expressionChanged;
}

const enhance = compose<ExpressionBuilderProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    undefined,
    { expressionChanged }
  ),
  lifecycle<Props & ConnectState & ConnectDispatch, {}>({
    componentDidMount() {
      const { expressionChanged, expressionId, testExpression } = this.props;
      expressionChanged(expressionId, testExpression);
    }
  })
);

const TestExpressionBuilder = enhance(ExpressionBuilder);

storiesOf("Expression Builder", module)
  .addDecorator(StroomDecorator)
  .add("Populated Editable", () => (
    <TestExpressionBuilder
      testExpression={testExpression}
      showModeToggle
      expressionId="populatedExEdit"
      dataSource={testDataSource}
    />
  ))
  .add("Populated ReadOnly", () => (
    <TestExpressionBuilder
      testExpression={testExpression}
      expressionId="populatedExRO"
      dataSource={testDataSource}
    />
  ))
  .add("Simplest Editable", () => (
    <TestExpressionBuilder
      testExpression={simplestExpression}
      showModeToggle
      expressionId="simplestEx"
      dataSource={testDataSource}
    />
  ))
  .add("Missing Data Source (read only)", () => (
    <TestExpressionBuilder
      testExpression={testExpression}
      expressionId="populatedExNoDs"
      dataSource={testDataSource}
    />
  ))
  .add("Missing Expression", () => (
    <ExpressionBuilder expressionId="missingEx" dataSource={testDataSource} />
  ))
  .add("Hide mode toggle", () => (
    <TestExpressionBuilder
      testExpression={testExpression}
      showModeToggle={false}
      expressionId="simplestEx"
      dataSource={testDataSource}
    />
  ))
  .add("Hide mode toggle but be in edit mode", () => (
    <TestExpressionBuilder
      testExpression={testExpression}
      showModeToggle={false}
      editMode
      expressionId="simplestEx"
      dataSource={testDataSource}
    />
  ));
