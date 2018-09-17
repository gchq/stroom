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
import { itemIsInSubtree, canMove, findItem } from '../treeUtils';

// Denormalised so I can refer to individual elements in the tree.
const oneOne = {
  uuid: '1-1',
  type: 'file',
  name: 'myFirstFirst',
};

const oneTwo = {
  uuid: '1-2',
  type: 'file',
  name: 'myFirstSecond',
};

const oneThreeOne = {
  uuid: '1-3-1',
  type: 'file',
  name: 'myFirstThirdFirst',
};

const oneThreeTwo = {
  uuid: '1-3-2',
  type: 'file',
  name: 'myFirstThirdSecond',
};

const oneThree = {
  uuid: '1-3',
  type: 'folder',
  name: 'myFirstThird',
  children: [oneThreeOne, oneThreeTwo],
};

const oneFourOne = {
  uuid: '1-4-1',
  type: 'file',
  name: 'myFirstFourthFirst',
};

const oneFourTwo = {
  uuid: '1-4-2',
  type: 'file',
  name: 'myFirstFourthSecond',
};

const oneFour = {
  uuid: '1-4',
  type: 'folder',
  name: 'myFirstFourth',
  children: [oneFourOne, oneFourTwo],
};

const oneFiveOneOne = {
  uuid: '1-5-1-1',
  type: 'file',
  name: 'myFirstFifthFirstFirst',
};

const oneFiveOne = {
  uuid: '1-5-1',
  type: 'folder',
  name: 'myFirstFifthFirst',
  children: [oneFiveOneOne],
};

const oneFive = {
  uuid: '1-5',
  type: 'folder',
  name: 'myFirstFifth',
  children: [oneFiveOne],
};

const testTree = {
  uuid: '1',
  type: 'folder',
  name: 'root',
  children: [oneOne, oneTwo, oneThree, oneFour, oneFive],
};

describe('Tree Utils', () => {
  describe('#itemIsInSubtree()', () => {
    test('should find a match when is root', () => {
      const found = itemIsInSubtree(testTree, testTree);
      expect(found).toBe(true);
    });
    test('should find a match when present within children', () => {
      const found = itemIsInSubtree(testTree, oneTwo);
      expect(found).toBe(true);
    });
    test('should find a match when present within grand-children', () => {
      const found = itemIsInSubtree(testTree, oneThreeOne);
      expect(found).toBe(true);
    });
    test('should not find a match when missing', () => {
      const found = itemIsInSubtree(testTree, { uuid: 'fifty' });
      expect(found).toBe(false);
    });
  });
  describe('#canMove()', () => {
    test('should allow moving files to other directories', () => {
      const allowed = canMove(oneFourTwo, oneThree);
      expect(allowed).toBe(true);
    });
    test('should prevent moving file into folder its already in', () => {
      const allowed = canMove(oneFourTwo, oneFour);
      expect(allowed).toBe(false);
    });
    test('should allow moving folder to other directories not inside self', () => {
      const allowed = canMove(oneFour, oneThree);
      expect(allowed).toBe(true);
    });
    test('should prevent moving folder into one of its own children', () => {
      const allowed = canMove(oneFive, oneFiveOne);
      expect(allowed).toBe(false);
    });
    test('should prevent moving folder into one of its own grand-children', () => {
      const allowed = canMove(testTree, oneFiveOne);
      expect(allowed).toBe(false);
    });
    test('should prevent moving folder into itself', () => {
      const allowed = canMove(oneFive, oneFive);
      expect(allowed).toBe(false);
    });
  });
  describe('#findItem()', () => {
    test('should find a match when is root', () => {
      const { node: found } = findItem(testTree, testTree.uuid);
      expect(found).toBe(testTree);
    });
    test('should find a match when present within children', () => {
      const { node: found } = findItem(testTree, oneTwo.uuid);
      expect(found).toBe(oneTwo);
    });
    test('should find a match when present within grand-children', () => {
      const { node: found } = findItem(testTree, oneThreeOne.uuid);
      expect(found).toBe(oneThreeOne);
    });
    test('should not find a match when missing', () => {
      const result = findItem(testTree, { uuid: 'fifty' });
      expect(result).toBe(undefined);
    });
  });
});
