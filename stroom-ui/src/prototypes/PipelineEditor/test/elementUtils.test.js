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
import expect from 'expect';

import { groupByCategory, groupByCategoryFiltered, isValidChildType } from '../elementUtils';

import { ElementCategories } from '../ElementCategories';

import { testElementTypes, testElementProperties } from './pipelineData.test';
import { elements } from './elementsData.test';

describe('Element Utils', () => {
  describe('#groupByCategory', () => {
    it('should map by category', () => {
      // When
      const byCategory = groupByCategory(testElementTypes);

      // Then
      const expectedCategories = ['DESTINATION', 'WRITER', 'PARSER', 'FILTER'];
      expectedCategories.forEach(c => expect(byCategory).toHaveProperty(c));

      const destinations = byCategory.DESTINATION;
      expect(destinations.length).toBe(2);

      const filters = byCategory.FILTER;
      expect(filters.length).toBe(5);
      expect(filters).toContain(elements.recordCountFilter);
    });
  });

  describe('#groupByCategoryFiltered', () => {
    it('should present the parsers and destinations for a parent of source', () => {
      const byCategory = groupByCategoryFiltered(testElementTypes, elements.source, 0);

      expect(byCategory).toHaveProperty('DESTINATION');
      expect(byCategory).toHaveProperty('PARSER');
      expect(Object.keys(byCategory).length).toBe(2);
    });

    it('should present filters and writers for a parent of parser', () => {
      const byCategory = groupByCategoryFiltered(testElementTypes, elements.dsParser, 0);

      expect(byCategory).toHaveProperty('FILTER');
      expect(byCategory).toHaveProperty('WRITER');

      expect(Object.keys(byCategory).length).toBe(2);
    });

    it('should present filters and writers for a parent of filter', () => {
      const byCategory = groupByCategoryFiltered(testElementTypes, elements.splitFilter, 0);

      expect(byCategory).toHaveProperty('FILTER');
      expect(byCategory).toHaveProperty('WRITER');

      expect(Object.keys(byCategory).length).toBe(2);
    });

    it('should present no options if the parent is a writer, with an existing child', () => {
      const byCategory = groupByCategoryFiltered(testElementTypes, elements.xmlWriter, 1);
      expect(byCategory).toEqual({});
    });

    it('should present destinations for a parent of writer, with no existing child', () => {
      const byCategory = groupByCategoryFiltered(testElementTypes, elements.xmlWriter);

      expect(byCategory).toHaveProperty('DESTINATION');
      expect(byCategory.DESTINATION).toContain(elements.fileAppender);
      expect(byCategory.DESTINATION).toContain(elements.streamAppender);
    });
  });

  describe('#isValidChildType', () => {
    describe('writer', () => {
      it('should allow a writer with no children to connect to a destination', () => {
        const allowed1 = isValidChildType(elements.jsonWriter, elements.streamAppender, 0);
        expect(allowed1).toBe(true);

        const allowed2 = isValidChildType(elements.jsonWriter, elements.fileAppender, 0);
        expect(allowed2).toBe(true);
      });
      it('should prevent a writer with a child from connecting to a destination', () => {
        const allowed1 = isValidChildType(elements.jsonWriter, elements.streamAppender, 1);
        expect(allowed1).toBe(false);

        const allowed2 = isValidChildType(elements.jsonWriter, elements.fileAppender, 1);
        expect(allowed2).toBe(false);
      });
      it('should prevent a writer being connected to a filter', () => {
        const allowed = isValidChildType(elements.jsonWriter, elements.recordCountFilter, 0);
        expect(allowed).toBe(false);
      });
      it('should prevent a writer being connected to a parser', () => {
        const allowed = isValidChildType(elements.jsonWriter, elements.dsParser, 0);
        expect(allowed).toBe(false);
      });
    });
    describe('destination', () => {
      it('should prevent a destination being connected onto anything', () => {
        const allowed1 = isValidChildType(elements.fileAppender, elements.dsParser, 0);
        expect(allowed1).toBe(false);
        const allowed2 = isValidChildType(elements.streamAppender, elements.dsParser, 0);
        expect(allowed2).toBe(false);
      });
    });
    describe('source', () => {
      it('should allow a source to connect to a destination', () => {
        const allowed = isValidChildType(elements.source, elements.fileAppender, 0);
        expect(allowed).toBe(true);
      });
      it('should allow a source to connect to a parser', () => {
        const allowed = isValidChildType(elements.source, elements.dsParser, 0);
        expect(allowed).toBe(true);
      });
      it('should prevent a source being connected to a filter', () => {
        const allowed = isValidChildType(elements.source, elements.schemaFilter, 0);
        expect(allowed).toBe(false);
      });
    });
    describe('parser', () => {
      it('should prevent a parser from connecting to a destination', () => {
        const allowed = isValidChildType(elements.dsParser, elements.fileAppender, 0);
        expect(allowed).toBe(false);
      });
      it('should prevent a parser from connecting to a parser', () => {
        const allowed = isValidChildType(elements.dsParser, elements.jsonParser, 0);
        expect(allowed).toBe(false);
      });
      it('should allow a parser to connect to a filter', () => {
        const allowed = isValidChildType(elements.jsonParser, elements.schemaFilter, 0);
        expect(allowed).toBe(true);
      });
    });
    describe('target+writer', () => {
      it('should prevent a target+writer from connecting to a parser', () => {
        const allowed = isValidChildType(elements.jsonWriter, elements.jsonParser, 0);
        expect(allowed).toBe(false);
      });
      it('should prevent a target+writer to connect to a filter', () => {
        const allowed = isValidChildType(elements.jsonWriter, elements.schemaFilter, 0);
        expect(allowed).toBe(false);
      });
      it('should allow a target+writer to connect to a destination', () => {
        const allowed = isValidChildType(elements.jsonWriter, elements.streamAppender, 0);
        expect(allowed).toBe(true);
      });
    });
    describe('target (not writer)', () => {
      it('should prevent a non writing target from connecting to a parser', () => {
        const allowed = isValidChildType(elements.recordCountFilter, elements.jsonParser, 0);
        expect(allowed).toBe(false);
      });
      it('should prevent a non writing target from connecting to a destination', () => {
        const allowed = isValidChildType(elements.recordCountFilter, elements.streamAppender, 0);
        expect(allowed).toBe(false);
      });
      it('should allow a non writing target to connect to a filter', () => {
        const allowed = isValidChildType(elements.recordCountFilter, elements.schemaFilter, 0);
        expect(allowed).toBe(true);
      });
    });
  });
});
