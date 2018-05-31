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

import { 
    groupByCategory,
    groupByCategoryFiltered,
    isValidChildType
} from '../elementUtils';

import {
    ElementCategories
} from '../ElementCategories';

import {
    testElementTypes,
    testElementProperties,
    fileAppender,
	jsonWriter,
	xmlFragmentParser,
	recordCountFilter,
	textWriter,
	dsParser,
	combinedParser,
	xmlWriter,
	source,
	schemaFilter,
	splitFilter,
	xsltFilter,
	jsonParser,
	streamAppender,
	recordOutputFilter,
	xmlParser
} from 'testData';

 describe('Element Utils', () => {
    describe('#groupByCategory', () => {
        it('should map by category', () => {
            // When
            let byCategory = groupByCategory(testElementTypes);

            // Then
            let expectedCategories = ['DESTINATION', 'WRITER', 'PARSER', 'FILTER']
            expectedCategories.forEach(c => expect(byCategory).toHaveProperty(c));

            let destinations = byCategory['DESTINATION'];
            expect(destinations.length).toBe(2);

            let filters = byCategory['FILTER'];
            expect(filters.length).toBe(5);
            let recordCountFilterFound = filters.filter(f => f.type === recordCountFilter.type)[0];
            expect(recordCountFilterFound).toBe(recordCountFilter);
        });
    });

    describe('#groupByCategoryFiltered', () => {
        it('should present the parsers and destinations for a parent of source', () => {
            let byCategory = groupByCategoryFiltered(testElementTypes, source, 0);

            expect(byCategory).toHaveProperty('DESTINATION');
            expect(byCategory).toHaveProperty('PARSER');
            expect(Object.keys(byCategory).length).toBe(2);
        });

        it('should present filters and writers for a parent of parser', () => {
            let byCategory = groupByCategoryFiltered(testElementTypes, dsParser, 0);

            expect(byCategory).toHaveProperty('FILTER');
            expect(byCategory).toHaveProperty('WRITER');

            expect(Object.keys(byCategory).length).toBe(2);
        });

        it('should present filters and writers for a parent of filter', () => {
            let byCategory = groupByCategoryFiltered(testElementTypes, splitFilter, 0);

            expect(byCategory).toHaveProperty('FILTER');
            expect(byCategory).toHaveProperty('WRITER');

            expect(Object.keys(byCategory).length).toBe(2);
        });

        it('should present no options if the parent is a writer, with an existing child', () => {
            let byCategory = groupByCategoryFiltered(testElementTypes, xmlWriter, 1);
            expect(byCategory).toEqual({});
        });

        it('should present destinations for a parent of writer, with no existing child', () => {
            let byCategory = groupByCategoryFiltered(testElementTypes, xmlWriter, );

            expect(byCategory).toHaveProperty('DESTINATION');
            expect(byCategory.DESTINATION).toContain(fileAppender);
            expect(byCategory.DESTINATION).toContain(streamAppender);
        });
    });

    describe('#isValidChildType', () => {
        describe('writer', () => {
            it('should allow a writer with no children to connect to a destination', () => {
                let allowed1 = isValidChildType(jsonWriter, streamAppender, 0);
                expect(allowed1).toBe(true);

                let allowed2 = isValidChildType(jsonWriter, fileAppender, 0);
                expect(allowed2).toBe(true);
            });
            it('should prevent a writer with a child from connecting to a destination', () => {
                let allowed1 = isValidChildType(jsonWriter, streamAppender, 1);
                expect(allowed1).toBe(false);

                let allowed2 = isValidChildType(jsonWriter, fileAppender, 1);
                expect(allowed2).toBe(false);
            });
            it ('should prevent a writer being connected to a filter', () => {
                let allowed = isValidChildType(jsonWriter, recordCountFilter, 0);
                expect(allowed).toBe(false);
            });
            it ('should prevent a writer being connected to a parser', () => {
                let allowed = isValidChildType(jsonWriter, dsParser, 0);
                expect(allowed).toBe(false);
            });
        });
        describe('destination', () => {
            it ('should prevent a destination being connected onto anything', () => {
                let allowed1 = isValidChildType(fileAppender, dsParser, 0);
                expect(allowed1).toBe(false);
                let allowed2 = isValidChildType(streamAppender, dsParser, 0);
                expect(allowed2).toBe(false);
            });
        });
        describe('source', () => {
            it ('should allow a source to connect to a destination', () => {
                let allowed = isValidChildType(source, fileAppender, 0);
                expect(allowed).toBe(true);
            });
            it ('should allow a source to connect to a parser', () => {
                let allowed = isValidChildType(source, dsParser, 0);
                expect(allowed).toBe(true);
            });
            it ('should prevent a source being connected to a filter', () => {
                let allowed = isValidChildType(source, schemaFilter, 0);
                expect(allowed).toBe(false);
            }); 
        });
        describe('parser', () => {
            it ('should prevent a parser from connecting to a destination', () => {
                let allowed = isValidChildType(dsParser, fileAppender, 0);
                expect(allowed).toBe(false);
            });
            it ('should prevent a parser from connecting to a parser', () => {
                let allowed = isValidChildType(dsParser, jsonParser, 0);
                expect(allowed).toBe(false);
            });
            it ('should allow a parser to connect to a filter', () => {
                let allowed = isValidChildType(jsonParser, schemaFilter, 0);
                expect(allowed).toBe(true);
            });
        });
        describe('target+writer', () => {
            it ('should prevent a target+writer from connecting to a parser', () => {
                let allowed = isValidChildType(jsonWriter, jsonParser, 0);
                expect(allowed).toBe(false);
            });
            it ('should prevent a target+writer to connect to a filter', () => {
                let allowed = isValidChildType(jsonWriter, schemaFilter, 0);
                expect(allowed).toBe(false);
            });
            it ('should allow a target+writer to connect to a destination', () => {
                let allowed = isValidChildType(jsonWriter, streamAppender, 0);
                expect(allowed).toBe(true);
            });
        });
        describe('target (not writer)', () => {
            it ('should prevent a non writing target from connecting to a parser', () => {
                let allowed = isValidChildType(recordCountFilter, jsonParser, 0);
                expect(allowed).toBe(false);
            });
            it ('should prevent a non writing target from connecting to a destination', () => {
                let allowed = isValidChildType(recordCountFilter, streamAppender, 0);
                expect(allowed).toBe(false);
            });
            it ('should allow a non writing target to connect to a filter', () => {
                let allowed = isValidChildType(recordCountFilter, schemaFilter, 0);
                expect(allowed).toBe(true);
            });
        });
    });
 });