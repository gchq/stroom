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

import { guid } from 'lib/treeUtils';

function createRandomItem(docRefType) {
  return {
    uuid: guid(),
    type: docRefType,
    name: loremIpsum(LOREM_CONFIG),
  };
}

const LOREM_CONFIG = { count: 3, units: 'words' };

const testTree = {
  uuid: guid(),
  name: 'Stroom',
  type: 'Folder',
  children: [
    {
      uuid: guid(),
      type: 'Folder',
      name: 'Some Examples',
      children: [
        {
          uuid: guid(),
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
          uuid: guid(),
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
      uuid: guid(),
      type: 'Folder',
      name: 'Yet More Examples',
      children: [
        {
          uuid: guid(),
          type: 'Folder',
          name: 'Stroom 102',
          children: [
            createRandomItem('Dictionary'),
            createRandomItem('Pipeline'),
            createRandomItem('XSLT'),
            createRandomItem('Index'),
            createRandomItem('Dashboard'),
            {
              uuid: guid(),
              type: 'Visualisation',
              name: 'abababababababa',
            },
          ],
        },
        {
          uuid: guid(),
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
      uuid: guid(),
      type: 'Folder',
      name: 'Stuff that wont match for tests',
      children: [
        {
          uuid: guid(),
          type: 'Visualisation',
          name: 'abcdefghijklmnopqrstuvwxyz',
        },
      ],
    },
    {
      uuid: guid(),
      type: 'Dashboard',
      name: 'ababababababababa',
    },
  ],
};

export default testTree;
