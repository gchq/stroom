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
import loremIpsum from 'lorem-ipsum';
import uuidv4 from 'uuid/v4';

function createRandomItem(docRefType) {
  return {
    uuid: uuidv4(),
    type: docRefType,
    name: loremIpsum(LOREM_CONFIG),
  };
}

const LOREM_CONFIG = { count: 3, units: 'words' };

const testTree = {
  uuid: uuidv4(),
  name: 'Stroom',
  type: 'Folder',
  children: [
    {
      uuid: uuidv4(),
      type: 'Folder',
      name: 'Some Examples',
      children: [
        {
          uuid: uuidv4(),
          type: 'Folder',
          name: 'Stroom 101',
          children: [
            createRandomItem('Dictionary'),
            createRandomItem('Pipeline'),
            createRandomItem('XSLT'),
            createRandomItem('Index'),
            createRandomItem('Dashboard'),
          ],
        },
        {
          uuid: uuidv4(),
          type: 'Folder',
          name: 'Stroom Elastic Example',
          children: [
            createRandomItem('Dictionary'),
            createRandomItem('Pipeline'),
            createRandomItem('TextConverter'),
            createRandomItem('ElasticIndex'),
            createRandomItem('Dashboard'),
          ],
        },
      ],
    },
    {
      uuid: uuidv4(),
      type: 'Folder',
      name: 'Yet More Examples',
      children: [
        {
          uuid: uuidv4(),
          type: 'Folder',
          name: 'Stroom 102',
          children: [
            createRandomItem('Dictionary'),
            createRandomItem('Pipeline'),
            createRandomItem('XSLT'),
            createRandomItem('Index'),
            createRandomItem('Dashboard'),
            {
              uuid: uuidv4(),
              type: 'Visualisation',
              name: 'abababababababa',
            },
          ],
        },
        {
          uuid: uuidv4(),
          type: 'Folder',
          name: 'Stroom Annotations Example',
          children: [
            createRandomItem('Dictionary'),
            createRandomItem('Pipeline'),
            createRandomItem('TextConverter'),
            createRandomItem('AnnotationsIndex'),
            createRandomItem('Dashboard'),
          ],
        },
      ],
    },
    {
      uuid: uuidv4(),
      type: 'Folder',
      name: 'Stuff that wont match for tests',
      children: [
        {
          uuid: uuidv4(),
          type: 'Visualisation',
          name: 'abcdefghijklmnopqrstuvwxyz',
        },
      ],
    },
    {
      uuid: uuidv4(),
      type: 'Dashboard',
      name: 'ababababababababa',
    },
  ],
};

export default testTree;
