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

import { stringToExpression } from './searchBarUtils';

describe('SearchBarUtils', () => {
  describe('#mappings', () => {
    test('from string to expression (simplest)', () => {
      // Given
      const basic = 'foo1=bar1';

      // When
      const parsed = stringToExpression(basic);

      // Then
      expect(parsed.expression.children.length).toBe(1);
      expect(parsed.expression.children[0].field).toBe('foo1');
      expect(parsed.expression.children[0].value).toBe('bar1');
      expect(parsed.expression.children[0].condition).toBe('EQUALS');
    });

    test('from string to expression (everything)', () => {
      // Given
      const basic = 'foo1=bar1 foo2>bar2 foo3<bar3 foo4>=bar4 foo5<=bar5';

      // When
      const parsed = stringToExpression(basic);

      // Then
      expect(parsed.errors.length).toBe(0);
      expect(parsed.expression.children.length).toBe(5);
      expectForTerm(parsed.expression.children[0], 'foo1', 'EQUALS', 'bar1');
      expectForTerm(parsed.expression.children[1], 'foo2', 'GREATER_THAN', 'bar2');
      expectForTerm(parsed.expression.children[2], 'foo3', 'LESS_THAN', 'bar3');
      expectForTerm(parsed.expression.children[3], 'foo4', 'GREATER_THAN_OR_EQUAL_TO', 'bar4');
      expectForTerm(parsed.expression.children[4], 'foo5', 'LESS_THAN_OR_EQUAL_TO', 'bar5');
    });

    test('from string to expression (bad condition)', () => {
      // Given
      const basic = 'foo1=bar1 foo2~bar2 foo3<bar3 foo4>=bar4 foo5<=bar5';

      // When
      const parsed = stringToExpression(basic);

      // Then
      expect(parsed.errors.length).toBe(1);
      expect(parsed.errors[0]).toBe('foo2~bar2');
      expect(parsed.expression.children.length).toBe(4);
      expectForTerm(parsed.expression.children[0], 'foo1', 'EQUALS', 'bar1');
      expectForTerm(parsed.expression.children[1], 'foo3', 'LESS_THAN', 'bar3');
      expectForTerm(parsed.expression.children[2], 'foo4', 'GREATER_THAN_OR_EQUAL_TO', 'bar4');
      expectForTerm(parsed.expression.children[3], 'foo5', 'LESS_THAN_OR_EQUAL_TO', 'bar5');
    });

    test('from string to expression (different order)', () => {
      // Given
      const basic = 'foo4>=bar4 foo5<=bar5 foo1=bar1 foo2>bar2 foo3<bar3';

      // When
      const parsed = stringToExpression(basic);

      // Then
      expect(parsed.errors.length).toBe(0);
      expectForHealthy(parsed.expression.children);
    });

    test('from string to expression (bad condition 2)', () => {
      // Given
      const basic = 'foo4>=bar4 foo5<=bar5 foo1=bar1 BAD_CONDITION foo2>bar2 foo3<bar3';

      // When
      const parsed = stringToExpression(basic);

      // Then
      expect(parsed.errors.length).toBe(1);
      expect(parsed.errors[0]).toBe('BAD_CONDITION');
      expectForHealthy(parsed.expression.children);
    });

    test('from string to expression (white space in places 1)', () => {
      // Given
      const basic = '   foo4>=bar4 foo5<=bar5 foo1=bar1 BAD_CONDITION foo2>bar2 foo3<bar3   ';

      // When
      const parsed = stringToExpression(basic);

      // Then
      expect(parsed.errors.length).toBe(1);
      expect(parsed.errors[0]).toBe('BAD_CONDITION');
      expectForHealthy(parsed.expression.children);
    });

    test('from string to expression (white space in places 2)', () => {
      // Given
      const basic =
        '   foo4>=bar4            foo5<=bar5 foo1=bar1 BAD_CONDITION foo2>bar2 foo3<bar3   ';

      // When
      const parsed = stringToExpression(basic);

      // Then
      expect(parsed.errors.length).toBe(1);
      expect(parsed.errors[0]).toBe('BAD_CONDITION');
      expectForHealthy(parsed.expression.children);
    });
  });
});

const expectForHealthy = (children) => {
  expect(children.length).toBe(5);
  expectForTerm(children[0], 'foo4', 'GREATER_THAN_OR_EQUAL_TO', 'bar4');
  expectForTerm(children[1], 'foo5', 'LESS_THAN_OR_EQUAL_TO', 'bar5');
  expectForTerm(children[2], 'foo1', 'EQUALS', 'bar1');
  expectForTerm(children[3], 'foo2', 'GREATER_THAN', 'bar2');
  expectForTerm(children[4], 'foo3', 'LESS_THAN', 'bar3');
};

const expectForTerm = (child, field, condition, value) => {
  expect(child.field).toBe(field);
  expect(child.value).toBe(value);
  expect(child.condition).toBe(condition);
};
