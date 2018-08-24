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

import { processSearchString } from './searchBarUtils';

import { testDataSource } from 'components/ExpressionBuilder/dataSource.testData';

describe('SearchBarUtils', () => {
  describe('#processSearchString', () => {
    // ///////////////
    // Field tests //
    // ///////////////

    test('Can find single invalid field', () => {
      // Given
      const basic = 'foo1=bar1';

      // When
      const results = processSearchString(testDataSource, basic);

      // Then
      expect(results.fields.length).toBe(1);
      expect(results.fields[0].fieldIsValid).toBeFalsy();
      expect(results.fields[0].conditionIsValid).toBeFalsy();
    });

    test("Doesn't flag valid fields as being invalid", () => {
      // Given
      const basic = 'foo1=bar1 createTime=2018-01-01T00:00Z000';

      // When
      const results = processSearchString(testDataSource, basic);

      // Then
      expect(results.fields.length).toBe(2);
      expect(results.fields[0].fieldIsValid).toBeFalsy();
      expect(results.fields[0].conditionIsValid).toBeFalsy();
      expect(results.fields[1].fieldIsValid).toBeTruthy();
      expect(results.fields[1].conditionIsValid).toBeTruthy();
    });

    test('Finds two invalid fields', () => {
      // Given
      const basic = 'foo1=bar1 createTimez=2018-01-01T00:00Z000';

      // When
      const results = processSearchString(testDataSource, basic);

      // Then
      expect(results.fields.length).toBe(2);
      expect(results.fields[0].fieldIsValid).toBeFalsy();
      expect(results.fields[0].conditionIsValid).toBeFalsy();
      expect(results.fields[1].fieldIsValid).toBeFalsy();
      expect(results.fields[1].conditionIsValid).toBeFalsy();
    });

    test('Tests all fields in the datasource, along with some duffers', () => {
      // Given
      const basic =
        'foo1=bar1  id=782897 colour=red     numberOfDoors=10000000 createUser=DarthVader   createTime=2018-01-01T00:00Z000 updateUser=LukeSkywalker updateTime=2018-01-01T00:00Z000    beKind=toYourself';

      // When
      const results = processSearchString(testDataSource, basic);
      const get = fieldName => results.fields.find(field => field.parsed[0] === fieldName);

      // Then
      expect(results.fields.length).toBe(9);
      expect(get('foo1').fieldIsValid).toBeFalsy();
      expect(get('foo1').conditionIsValid).toBeFalsy();
      expect(get('id').fieldIsValid).toBeTruthy();
      expect(get('id').conditionIsValid).toBeTruthy();
      expect(get('colour').fieldIsValid).toBeTruthy();
      expect(get('colour').conditionIsValid).toBeTruthy();
      expect(get('numberOfDoors').fieldIsValid).toBeTruthy();
      expect(get('numberOfDoors').conditionIsValid).toBeTruthy();
      expect(get('createUser').fieldIsValid).toBeTruthy();
      expect(get('createUser').conditionIsValid).toBeTruthy();
      expect(get('createTime').fieldIsValid).toBeTruthy();
      expect(get('createTime').conditionIsValid).toBeTruthy();
      expect(get('updateUser').fieldIsValid).toBeTruthy();
      expect(get('updateUser').conditionIsValid).toBeTruthy();
      expect(get('updateTime').fieldIsValid).toBeTruthy();
      expect(get('updateTime').conditionIsValid).toBeTruthy();
      expect(get('beKind').fieldIsValid).toBeFalsy();
      expect(get('beKind').conditionIsValid).toBeFalsy();
    });

    test('Field and condition but no value is invalid', () => {
      // Given
      const basic = 'createTime=';

      // When
      const results = processSearchString(testDataSource, basic);

      // Then
      expect(results.fields.length).toBe(1);
      expect(results.fields[0].fieldIsValid).toBeTruthy();
      expect(results.fields[0].conditionIsValid).toBeTruthy();
      expect(results.fields[0].valueIsValid).toBeFalsy();
    });

    // ////////////////////
    // Expression tests //
    // ////////////////////

    test('Can map simplest query', () => {
      // Given
      const basic = 'foo1=bar1';

      // When
      const results = processSearchString(testDataSource, basic);

      // Then
      expect(results.expression.children.length).toBe(1);
      expect(results.expression.children[0].field).toBe('foo1');
      expect(results.expression.children[0].value).toBe('bar1');
      expect(results.expression.children[0].condition).toBe('EQUALS');
    });

    test('Can map a query with all operators', () => {
      // Given
      const basic = 'foo1=bar1 foo2>bar2 foo3<bar3 foo4>=bar4 foo5<=bar5';

      // When
      const results = processSearchString(testDataSource, basic);

      // Then
      expect(results.expression.children.length).toBe(5);
      expectForTerm(results.expression.children[0], 'foo1', 'EQUALS', 'bar1');
      expectForTerm(results.expression.children[1], 'foo2', 'GREATER_THAN', 'bar2');
      expectForTerm(results.expression.children[2], 'foo3', 'LESS_THAN', 'bar3');
      expectForTerm(results.expression.children[3], 'foo4', 'GREATER_THAN_OR_EQUAL_TO', 'bar4');
      expectForTerm(results.expression.children[4], 'foo5', 'LESS_THAN_OR_EQUAL_TO', 'bar5');
    });

    test('Returns an error for an unknown operator', () => {
      // Given
      const basic = 'foo1=bar1 foo2~bar2 foo3<bar3 foo4>=bar4 foo5<=bar5';

      // When
      const results = processSearchString(testDataSource, basic);

      // Then
      expect(results.fields.filter(field => field.parsed === undefined).length).toBe(1);
      expect(results.fields.filter(field => field.parsed === undefined)[0].original).toBe('foo2~bar2');
      expect(results.expression.children.length).toBe(4);
      expectForTerm(results.expression.children[0], 'foo1', 'EQUALS', 'bar1');
      expectForTerm(results.expression.children[1], 'foo3', 'LESS_THAN', 'bar3');
      expectForTerm(results.expression.children[2], 'foo4', 'GREATER_THAN_OR_EQUAL_TO', 'bar4');
      expectForTerm(results.expression.children[3], 'foo5', 'LESS_THAN_OR_EQUAL_TO', 'bar5');
    });

    test('Can map a query with all operators but a different order', () => {
      // Given
      const basic = 'foo4>=bar4 foo5<=bar5 foo1=bar1 foo2>bar2 foo3<bar3';

      // When
      const results = processSearchString(testDataSource, basic);

      // Then
      expect(results.fields.filter(field => field.parsed === undefined).length).toBe(0);
      expectForHealthy(results.expression.children);
    });

    test('Returns an error for a bad condition', () => {
      // Given
      const basic = 'foo4>=bar4 foo5<=bar5 foo1=bar1 BAD_CONDITION foo2>bar2 foo3<bar3';

      // When
      const results = processSearchString(testDataSource, basic);

      // Then
      expect(results.fields.filter(field => field.parsed === undefined).length).toBe(1);
      expect(results.fields.filter(field => field.parsed === undefined)[0].original).toBe('BAD_CONDITION');
      expectForHealthy(results.expression.children);
    });

    test('Handles whitespace at the start and end of the query', () => {
      // Given
      const basic = '   foo4>=bar4 foo5<=bar5 foo1=bar1 BAD_CONDITION foo2>bar2 foo3<bar3   ';

      // When
      const results = processSearchString(testDataSource, basic);

      // Then
      expect(results.fields.filter(field => field.parsed === undefined).length).toBe(1);
      expect(results.fields.filter(field => field.parsed === undefined)[0].original).toBe('BAD_CONDITION');
      expectForHealthy(results.expression.children);
    });

    test('Handles whitespace at the start and end of the query and in th middle', () => {
      // Given
      const basic =
        '   foo4>=bar4            foo5<=bar5 foo1=bar1 BAD_CONDITION foo2>bar2 foo3<bar3   ';

      // When
      const results = processSearchString(testDataSource, basic);

      // Then
      expect(results.fields.filter(field => field.parsed === undefined).length).toBe(1);
      expect(results.fields.filter(field => field.parsed === undefined)[0].original).toBe('BAD_CONDITION');
      expectForHealthy(results.expression.children);
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
