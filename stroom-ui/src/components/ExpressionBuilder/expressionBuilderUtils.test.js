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

import {
  toString
} from './expressionBuilderUtils';

import {
  emptyExpression,
  singleTermExpression,
  simpleAndExpression,
  simpleOrExpression,
} from './expressionBuilderUtils.testData';

describe('ExpressionBuilter Utils', () => {
  describe('#toString', () => {
    test('can convert an empty expression', () => {
      // When
      const asString = toString(emptyExpression);

      // Then
      expect(asString).toBe('');
    });
    test('can convert a single term expression', () => {
      // When
      const asString = toString(singleTermExpression);

      // Then
      expect(asString).toBe('colour CONTAINS red');
    });
    test('can convert a simple AND expression', () => {
      // When
      const asString = toString(simpleAndExpression);

      // Then
      expect(asString).toBe('colour CONTAINS red AND colour IN blue');
    });
    test('can convert a simple OR expression', () => {
      // When
      const asString = toString(simpleOrExpression);

      // Then
      expect(asString).toBe('colour LIKE red OR colour = blue');
    });

  });
});
