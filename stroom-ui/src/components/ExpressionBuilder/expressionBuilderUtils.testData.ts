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

import { ExpressionOperatorType } from "../../types";
import * as uuidv4 from "uuid/v4";

const emptyExpression: ExpressionOperatorType = {
  uuid: "root",
  type: "operator",
  op: "AND",
  children: [],
  enabled: true
};

const singleTermExpression: ExpressionOperatorType = {
  uuid: uuidv4(),
  type: "operator",
  op: "AND",
  children: [
    {
      uuid: uuidv4(),
      type: "term",
      field: "colour",
      condition: "CONTAINS",
      value: "red",
      dictionary: null,
      enabled: true
    }
  ],
  enabled: true
};

const simpleAndExpression: ExpressionOperatorType = {
  uuid: uuidv4(),
  type: "operator",
  op: "AND",
  children: [
    {
      uuid: uuidv4(),
      type: "term",
      field: "colour",
      condition: "CONTAINS",
      value: "red",
      dictionary: null,
      enabled: true
    },
    {
      uuid: uuidv4(),
      type: "term",
      field: "colour",
      condition: "IN",
      value: "blue",
      dictionary: null,
      enabled: true
    }
  ],
  enabled: true
};

const simpleOrExpression: ExpressionOperatorType = {
  uuid: uuidv4(),
  type: "operator",
  op: "OR",
  children: [
    {
      uuid: uuidv4(),
      type: "term",
      field: "colour",
      condition: "CONTAINS",
      value: "red",
      dictionary: null,
      enabled: true
    },
    {
      uuid: uuidv4(),
      type: "term",
      field: "colour",
      condition: "EQUALS",
      value: "blue",
      dictionary: null,
      enabled: true
    }
  ],
  enabled: true
};

const nestedExpression: ExpressionOperatorType = {
  uuid: uuidv4(),
  type: "operator",
  op: "OR",
  children: [
    {
      uuid: uuidv4(),
      type: "term",
      field: "colour",
      condition: "CONTAINS",
      value: "red",
      dictionary: null,
      enabled: true
    },
    {
      uuid: uuidv4(),
      type: "term",
      field: "colour",
      condition: "IN",
      value: "blue",
      dictionary: null,
      enabled: true
    },
    {
      uuid: uuidv4(),
      type: "operator",
      op: "AND",
      enabled: true,
      children: [
        {
          uuid: uuidv4(),
          type: "term",
          field: "numberOfDoors",
          condition: "BETWEEN",
          value: "1,5",
          dictionary: null,
          enabled: true
        },
        {
          uuid: uuidv4(),
          type: "term",
          field: "createUser",
          condition: "EQUALS",
          value: "me",
          dictionary: null,
          enabled: true
        }
      ]
    }
  ],
  enabled: true
};

const deeplyNestedExpression: ExpressionOperatorType = {
  uuid: uuidv4(),
  type: "operator",
  op: "OR",
  children: [
    {
      uuid: uuidv4(),
      type: "term",
      field: "colour",
      condition: "CONTAINS",
      value: "red",
      dictionary: null,
      enabled: true
    },
    {
      uuid: uuidv4(),
      type: "term",
      field: "colour",
      condition: "IN",
      value: "blue",
      dictionary: null,
      enabled: true
    },
    {
      uuid: uuidv4(),
      type: "operator",
      op: "AND",
      enabled: true,
      children: [
        {
          uuid: uuidv4(),
          type: "term",
          field: "numberOfDoors",
          condition: "BETWEEN",
          value: "1,5",
          dictionary: null,
          enabled: true
        },
        {
          uuid: uuidv4(),
          type: "term",
          field: "createUser",
          condition: "EQUALS",
          value: "me",
          dictionary: null,
          enabled: true
        },
        {
          uuid: uuidv4(),
          type: "operator",
          op: "OR",
          enabled: true,
          children: [
            {
              uuid: uuidv4(),
              type: "term",
              field: "id",
              condition: "CONTAINS",
              value: "bob",
              dictionary: null,
              enabled: true
            },
            {
              uuid: uuidv4(),
              type: "term",
              field: "updateTime",
              condition: "BETWEEN",
              value: "me",
              dictionary: null,
              enabled: true
            }
          ]
        }
      ]
    }
  ],
  enabled: true
};

const partlyDisabledExpression01: ExpressionOperatorType = {
  uuid: uuidv4(),
  type: "operator",
  op: "OR",
  children: [
    {
      uuid: uuidv4(),
      type: "term",
      field: "colour",
      condition: "CONTAINS",
      value: "red",
      dictionary: null,
      enabled: true
    },
    {
      uuid: uuidv4(),
      type: "term",
      field: "colour",
      condition: "EQUALS",
      value: "blue",
      dictionary: null,
      enabled: false
    }
  ],
  enabled: true
};

const partlyDisabledExpression02: ExpressionOperatorType = {
  uuid: uuidv4(),
  type: "operator",
  op: "OR",
  children: [
    {
      uuid: uuidv4(),
      type: "term",
      field: "colour",
      condition: "CONTAINS",
      value: "red",
      dictionary: null,
      enabled: false
    },
    {
      uuid: uuidv4(),
      type: "term",
      field: "colour",
      condition: "EQUALS",
      value: "blue",
      dictionary: null,
      enabled: true
    }
  ],
  enabled: true
};

const partlyDisabledExpression03: ExpressionOperatorType = {
  uuid: uuidv4(),
  type: "operator",
  op: "OR",
  children: [
    {
      uuid: uuidv4(),
      type: "term",
      field: "colour",
      condition: "CONTAINS",
      value: "red",
      dictionary: null,
      enabled: true
    },
    {
      uuid: uuidv4(),
      type: "term",
      field: "colour",
      condition: "IN",
      value: "blue",
      dictionary: null,
      enabled: true
    },
    {
      uuid: uuidv4(),
      type: "operator",
      op: "AND",
      enabled: true,
      children: [
        {
          uuid: uuidv4(),
          type: "term",
          field: "numberOfDoors",
          condition: "BETWEEN",
          value: "1,5",
          dictionary: null,
          enabled: true
        },
        {
          uuid: uuidv4(),
          type: "term",
          field: "createUser",
          condition: "EQUALS",
          value: "me",
          dictionary: null,
          enabled: false
        }
      ]
    }
  ],
  enabled: true
};

const hugeExpression: ExpressionOperatorType = {
  uuid: uuidv4(),
  type: "operator",
  op: "OR",
  children: [
    {
      uuid: uuidv4(),
      type: "term",
      field: "colour",
      condition: "CONTAINS",
      value: "red",
      dictionary: null,
      enabled: true
    },
    {
      uuid: uuidv4(),
      type: "term",
      field: "colour",
      condition: "IN",
      value: "blue",
      dictionary: null,
      enabled: true
    },
    {
      uuid: uuidv4(),
      type: "operator",
      op: "AND",
      enabled: true,
      children: [
        {
          uuid: uuidv4(),
          type: "term",
          field: "numberOfDoors",
          condition: "BETWEEN",
          value: "1,5",
          dictionary: null,
          enabled: true
        },
        {
          uuid: uuidv4(),
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
      uuid: uuidv4(),
      type: "operator",
      op: "OR",
      enabled: false,
      children: [
        {
          uuid: uuidv4(),
          type: "term",
          field: "id",
          condition: "CONTAINS",
          value: "bob",
          dictionary: null,
          enabled: false
        },
        {
          uuid: uuidv4(),
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

export {
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
};
