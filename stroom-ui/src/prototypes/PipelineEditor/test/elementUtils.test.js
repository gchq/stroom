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
import expect from 'expect.js';

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
            let expectedCategories = ['DESTINATION', 'WRITER', 'PARSER', 'FILTER', 'INTERNAL']
            expectedCategories.forEach(category => expect(byCategory).to.have.property(category))

            let destinations = byCategory['DESTINATION'];
            expect(destinations).to.have.length(2);

            let filters = byCategory['FILTER'];
            expect(filters).to.have.length(5);
            let recordCountFilterFound = filters.filter(f => f.type === recordCountFilter.type)[0];
            expect(recordCountFilterFound).to.eql(recordCountFilter);
        });
    });

    describe('#groupByCategoryFiltered', () => {

    });

    describe('#isValidChildType', () => {
        describe('writer', () => {
            it('should allow a writer with no children to connect to a destination', () => {
                let allowed1 = isValidChildType(jsonWriter, streamAppender, 0);
                expect(allowed1).to.be(true);

                let allowed2 = isValidChildType(jsonWriter, fileAppender, 0);
                expect(allowed2).to.be(true);
            });
            it('should prevent a writer with a child from connecting to a destination', () => {
                let allowed1 = isValidChildType(jsonWriter, streamAppender, 1);
                expect(allowed1).to.be(false);

                let allowed2 = isValidChildType(jsonWriter, fileAppender, 1);
                expect(allowed2).to.be(false);
            });
            it ('should prevent a writer being connected to a filter', () => {
                let allowed = isValidChildType(jsonWriter, recordCountFilter, 0);
                expect(allowed).to.be(false);
            });
            it ('should prevent a writer being connected to a parser', () => {
                let allowed = isValidChildType(jsonWriter, dsParser, 0);
                expect(allowed).to.be(false);
            });
        });
        describe('destination', () => {
            it ('should prevent a destination being connected onto anything', () => {
                let allowed1 = isValidChildType(fileAppender, dsParser, 0);
                expect(allowed1).to.be(false);
                let allowed2 = isValidChildType(streamAppender, dsParser, 0);
                expect(allowed2).to.be(false);
            });
        });
        describe('source', () => {
            it ('should allow a source to connect to a destination', () => {
                let allowed = isValidChildType(source, fileAppender, 0);
                expect(allowed).to.be(true);
            });
            it ('should allow a source to connect to a parser', () => {
                let allowed = isValidChildType(source, dsParser, 0);
                expect(allowed).to.be(true);
            });
            it ('should prevent a source being connected to a filter', () => {
                let allowed = isValidChildType(source, schemaFilter, 0);
                expect(allowed).to.be(false);
            }); 
        });
        describe('parser', () => {
            it ('should prevent a parser from connecting to a destination', () => {
                let allowed = isValidChildType(dsParser, fileAppender, 0);
                expect(allowed).to.be(false);
            });
            it ('should prevent a parser from connecting to a parser', () => {
                let allowed = isValidChildType(dsParser, jsonParser, 0);
                expect(allowed).to.be(false);
            });
            it ('should allow a parser to connect to a filter', () => {
                let allowed = isValidChildType(jsonParser, schemaFilter, 0);
                expect(allowed).to.be(true);
            });
        });
        describe('target+writer', () => {
            it ('should prevent a target+writer from connecting to a parser', () => {
                let allowed = isValidChildType(jsonWriter, jsonParser, 0);
                expect(allowed).to.be(false);
            });
            it ('should prevent a target+writer to connect to a filter', () => {
                let allowed = isValidChildType(jsonWriter, schemaFilter, 0);
                expect(allowed).to.be(false);
            });
            it ('should allow a target+writer to connect to a destination', () => {
                let allowed = isValidChildType(jsonWriter, streamAppender, 0);
                expect(allowed).to.be(true);
            });
        });
        describe('target (not writer)', () => {
            it ('should prevent a non writing target from connecting to a parser', () => {
                let allowed = isValidChildType(recordCountFilter, jsonParser, 0);
                expect(allowed).to.be(false);
            });
            it ('should prevent a non writing target from connecting to a destination', () => {
                let allowed = isValidChildType(recordCountFilter, streamAppender, 0);
                expect(allowed).to.be(false);
            });
            it ('should allow a non writing target to connect to a filter', () => {
                let allowed = isValidChildType(recordCountFilter, schemaFilter, 0);
                expect(allowed).to.be(true);
            });
        });
    });
 });