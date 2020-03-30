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

import { toString } from "../expressionUtils";

import {
  emptyExpression,
  singleTermExpression,
  simpleAndExpression,
  simpleOrExpression,
  nestedExpression,
  deeplyNestedExpression,
  partlyDisabledExpression01,
  partlyDisabledExpression02,
  partlyDisabledExpression03,
  hugeExpression,
} from "./expressionBuilderUtils.testData";

describe("ExpressionBuilter Utils", () => {
  describe("#toString", () => {
    test("can convert an empty expression", () => {
      // When
      const asString = toString(emptyExpression);

      // Then
      expect(asString).toBe("");
    });
    test("can convert a single term expression", () => {
      // When
      const asString = toString(singleTermExpression);

      // Then
      expect(asString).toBe("colour contains red");
    });
    test("can convert a simple AND expression", () => {
      // When
      const asString = toString(simpleAndExpression);

      // Then
      expect(asString).toBe("colour contains red AND colour in blue");
    });
    test("can convert a simple OR expression", () => {
      // When
      const asString = toString(simpleOrExpression);

      // Then
      expect(asString).toBe("colour contains red OR colour = blue");
    });
    test("can convert a nested expression", () => {
      // When
      const asString = toString(nestedExpression);

      // Then
      expect(asString).toBe(
        "colour contains red OR colour in blue OR (numberOfDoors between 1,5 AND createUser = me)",
      );
    });
    test("can convert a deeply nested expression", () => {
      // When
      const asString = toString(deeplyNestedExpression);

      // Then
      expect(asString).toBe(
        "colour contains red OR colour in blue OR (numberOfDoors between 1,5 AND createUser = me AND (id contains bob OR updateTime between me))",
      );
    });
    test("can convert a partly disabled expression 01", () => {
      // When
      const asString = toString(partlyDisabledExpression01);

      // Then
      expect(asString).toBe("colour contains red");
    });
    test("can convert a partly disabled expression 02", () => {
      // When
      const asString = toString(partlyDisabledExpression02);

      // Then
      expect(asString).toBe("colour = blue");
    });
    test("can convert a partly disabled expression 03", () => {
      // When
      const asString = toString(partlyDisabledExpression03);

      // Then
      expect(asString).toBe(
        "colour contains red OR colour in blue OR (numberOfDoors between 1,5)",
      );
    });
    test("can convert a huge expression", () => {
      // When
      const asString = toString(hugeExpression);

      // Then
      expect(asString).toBe(
        "colour contains red OR colour in blue OR (numberOfDoors between 1,5 AND createUser = me)",
      );
    });
  });
});
