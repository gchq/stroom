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

const emptyExpression = {
  uuid: 'root',
  type: 'operator',
  op: 'AND',
  children: [],
  enabled: true,
};

const singleTermExpression = {
  type: 'operator',
  op: 'AND',
  children: [
    {
      type: 'term',
      field: 'colour',
      condition: 'CONTAINS',
      value: 'red',
      dictionary: null,
      enabled: true,
    }
  ],
  enabled: true,
}

const simpleAndExpression = {
  type: 'operator',
  op: 'AND',
  children: [
    {
      type: 'term',
      field: 'colour',
      condition: 'CONTAINS',
      value: 'red',
      dictionary: null,
      enabled: true,
    },
    {
      type: 'term',
      field: 'colour',
      condition: 'IN',
      value: 'blue',
      dictionary: null,
      enabled: true,
    },
  ],
  enabled: true,
};

const simpleOrExpression = {
  type: 'operator',
  op: 'OR',
  children: [
    {
      type: 'term',
      field: 'colour',
      condition: 'LIKE',
      value: 'red',
      dictionary: null,
      enabled: true,
    },
    {
      type: 'term',
      field: 'colour',
      condition: '=',
      value: 'blue',
      dictionary: null,
      enabled: true,
    },
  ],
  enabled: true,
};

export { emptyExpression, singleTermExpression, simpleAndExpression, simpleOrExpression };
