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

import { storiesOf } from "@storybook/react";

import StroomDecorator from "../../lib/storybook/StroomDecorator";
import { actionCreators as expressionBuilderActionCreators } from "../ExpressionBuilder";

import { simplestExpression, testDataSource } from "../ExpressionBuilder/test";

import ExpressionSearchBar, {
  Props as ExpressionSearchBarProps
} from "./ExpressionSearchBar";

import { ExpressionOperatorType } from "../../types";
import { GlobalStoreState } from "../../startup/reducers";

import "../../styles/main.css";

const { expressionChanged } = expressionBuilderActionCreators;

interface Props extends ExpressionSearchBarProps {
  testExpression: ExpressionOperatorType;
}

interface ConnectState {}
interface ConnectDispatch {
  expressionChanged: typeof expressionChanged;
}

const enhance = compose<ExpressionSearchBarProps, Props>(
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

const TestExpressionSearchBar = enhance(ExpressionSearchBar);

storiesOf("ExpressionSearchBar", module)
  .addDecorator(StroomDecorator)
  .add("Basic", () => (
    <TestExpressionSearchBar
      testExpression={simplestExpression}
      onSearch={() => console.log("Search called")}
      expressionId="simplestEx"
      initialSearchString="foo1=bar1 foo2=bar2 foo3=bar3 someOtherKey=sometOtherValue"
      dataSource={testDataSource}
    />
  ));
