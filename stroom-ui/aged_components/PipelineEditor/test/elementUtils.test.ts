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
import { groupByCategory, isValidChildType, keyByType } from '../elementUtils';
import { ElementCategories } from '../ElementCategories';
import { elements, elementProperties } from './index';

const jsonWriter = elements.find(f => f.type === 'JSONWriter');
const streamAppender = elements.find(f => f.type === 'StreamAppender');
const fileAppender = elements.find(f => f.type === 'FileAppender');
const recordCountFilter = elements.find(f => f.type === 'RecordCountFilter');
const dsParser = elements.find(f => f.type === 'DSParser');
const source = elements.find(f => f.type === 'Source');
const jsonParser = elements.find(f => f.type === 'JSONParser');
const schemaFilter = elements.find(f => f.type === 'SchemaFilter');

describe('Element Utils', () => {
  describe('#groupByCategory', () => {
    test('should map by category', () => {
      // When
      const byCategory = groupByCategory(elements);

      // Then
      const expectedCategories = ['DESTINATION', 'WRITER', 'PARSER', 'FILTER'];
      expectedCategories.forEach(c => expect(byCategory).toHaveProperty(c));

      const destinations = byCategory.DESTINATION;
      expect(destinations.length).toBe(9);

      const filters = byCategory.FILTER;
      expect(filters.length).toBe(13);
      expect(filters.map(f => f.type)).toContain('RecordCountFilter');
    });
  });

  describe('#keyByType', () => {
    test('should map by category', () => {
      // When
      const byType = keyByType(elements);

      // Then
      const expectedTypes = ['JSONWriter', 'XMLFragmentParser', 'TextWriter', 'XMLWriter'];
      expectedTypes.forEach(c => expect(byType).toHaveProperty(c));
    });
  });

  describe('#isValidChildType', () => {
    describe('writer', () => {
      test('should allow a writer with no children to connect to a destination', () => {
        const allowed1 = isValidChildType(jsonWriter, streamAppender, 0);
        expect(allowed1).toBe(true);

        const allowed2 = isValidChildType(jsonWriter, fileAppender, 0);
        expect(allowed2).toBe(true);
      });
      test('should prevent a writer with a child from connecting to a destination', () => {
        const allowed1 = isValidChildType(jsonWriter, streamAppender, 1);
        expect(allowed1).toBe(false);

        const allowed2 = isValidChildType(jsonWriter, fileAppender, 1);
        expect(allowed2).toBe(false);
      });
      test('should prevent a writer being connected to a filter', () => {
        const allowed = isValidChildType(jsonWriter, recordCountFilter, 0);
        expect(allowed).toBe(false);
      });
      test('should prevent a writer being connected to a parser', () => {
        const allowed = isValidChildType(jsonWriter, dsParser, 0);
        expect(allowed).toBe(false);
      });
    });
    describe('destination', () => {
      test('should prevent a destination being connected onto anything', () => {
        const allowed1 = isValidChildType(fileAppender, dsParser, 0);
        expect(allowed1).toBe(false);
        const allowed2 = isValidChildType(streamAppender, dsParser, 0);
        expect(allowed2).toBe(false);
      });
    });
    describe('source', () => {
      test('should allow a source to connect to a destination', () => {
        const allowed = isValidChildType(source, fileAppender, 0);
        expect(allowed).toBe(true);
      });
      test('should allow a source to connect to a parser', () => {
        const allowed = isValidChildType(source, dsParser, 0);
        expect(allowed).toBe(true);
      });
      test('should prevent a source being connected to a filter', () => {
        const allowed = isValidChildType(source, schemaFilter, 0);
        expect(allowed).toBe(false);
      });
    });
    describe('parser', () => {
      test('should prevent a parser from connecting to a destination', () => {
        const allowed = isValidChildType(dsParser, fileAppender, 0);
        expect(allowed).toBe(false);
      });
      test('should prevent a parser from connecting to a parser', () => {
        const allowed = isValidChildType(dsParser, jsonParser, 0);
        expect(allowed).toBe(false);
      });
      test('should allow a parser to connect to a filter', () => {
        const allowed = isValidChildType(jsonParser, schemaFilter, 0);
        expect(allowed).toBe(true);
      });
    });
    describe('target+writer', () => {
      test('should prevent a target+writer from connecting to a parser', () => {
        const allowed = isValidChildType(jsonWriter, jsonParser, 0);
        expect(allowed).toBe(false);
      });
      test('should prevent a target+writer to connect to a filter', () => {
        const allowed = isValidChildType(jsonWriter, schemaFilter, 0);
        expect(allowed).toBe(false);
      });
      test('should allow a target+writer to connect to a destination', () => {
        const allowed = isValidChildType(jsonWriter, streamAppender, 0);
        expect(allowed).toBe(true);
      });
    });
    describe('target (not writer)', () => {
      test('should prevent a non writing target from connecting to a parser', () => {
        const allowed = isValidChildType(recordCountFilter, jsonParser, 0);
        expect(allowed).toBe(false);
      });
      test('should prevent a non writing target from connecting to a destination', () => {
        const allowed = isValidChildType(recordCountFilter, streamAppender, 0);
        expect(allowed).toBe(false);
      });
      test('should allow a non writing target to connect to a filter', () => {
        const allowed = isValidChildType(recordCountFilter, schemaFilter, 0);
        expect(allowed).toBe(true);
      });
    });
  });
});
