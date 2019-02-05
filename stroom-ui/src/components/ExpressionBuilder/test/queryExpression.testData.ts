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
import { ExpressionOperatorType, Dictionary } from "../../../types";

const colourDictionary: Dictionary = {
  type: "Dictionary",
  uuid: "colourDict123",
  name: "Colours",
  description: "All the colours of the rainbow",
  data: `red
  orange
  yellow
  green
  blue
  indigo
  violet
  `,
  imports: []
};

const testExpression: ExpressionOperatorType = {
  type: "operator",
  op: "OR",
  children: [
    {
      type: "term",
      field: "colour",
      condition: "CONTAINS",
      value: "red",
      dictionary: null,
      enabled: true
    },
    {
      type: "term",
      field: "colour",
      condition: "IN",
      value: "blue",
      dictionary: null,
      enabled: true
    },
    {
      type: "term",
      field: "colour",
      condition: "IN_DICTIONARY",
      value: null,
      dictionary: colourDictionary,
      enabled: true
    },
    {
      type: "operator",
      op: "AND",
      enabled: true,
      children: [
        {
          type: "term",
          field: "numberOfDoors",
          condition: "BETWEEN",
          value: "1,5",
          dictionary: null,
          enabled: true
        },
        {
          type: "term",
          field: "createUser",
          condition: "EQUALS",
          value: "me",
          dictionary: null,
          enabled: true
        }
      ]
    },
    {
      type: "operator",
      op: "OR",
      enabled: false,
      children: [
        {
          type: "term",
          field: "id",
          condition: "CONTAINS",
          value: "bob",
          dictionary: null,
          enabled: false
        },
        {
          type: "term",
          field: "updateTime",
          condition: "BETWEEN",
          value: "me",
          dictionary: null,
          enabled: true
        }
      ]
    }
  ],
  enabled: true
};

const simplestExpression: ExpressionOperatorType = {
  type: "operator",
  op: "AND",
  children: [],
  enabled: true
};

const testAndOperator: ExpressionOperatorType = {
  type: "operator",
  op: "AND",
  enabled: true,
  children: []
};
const testOrOperator: ExpressionOperatorType = {
  type: "operator",
  op: "AND",
  enabled: true,
  children: []
};
const testNotOperator: ExpressionOperatorType = {
  type: "operator",
  op: "AND",
  enabled: true,
  children: []
};
export {
  testExpression,
  simplestExpression,
  testAndOperator,
  testOrOperator,
  testNotOperator
};
