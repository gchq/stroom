import {
  testExpression,
  simplestExpression,
  testAndOperator,
  testOrOperator,
  testNotOperator
} from "./queryExpression.testData";

import {
  emptyExpression,
  singleTermExpression,
  simpleAndExpression,
  simpleOrExpression,
  nestedExpression,
  deeplyNestedExpression,
  partlyDisabledExpression01,
  partlyDisabledExpression02,
  partlyDisabledExpression03,
  hugeExpression
} from "./expressionBuilderUtils.testData";

import { testDataSource, emptyDataSource } from "./dataSource.testData";

import enhanceWithTestExpression from "./enhanceWithTestExpression";

export {
  testExpression,
  simplestExpression,
  testAndOperator,
  testOrOperator,
  testNotOperator,
  emptyExpression,
  singleTermExpression,
  simpleAndExpression,
  simpleOrExpression,
  nestedExpression,
  deeplyNestedExpression,
  partlyDisabledExpression01,
  partlyDisabledExpression02,
  partlyDisabledExpression03,
  hugeExpression,
  testDataSource,
  emptyDataSource,
  enhanceWithTestExpression
};
