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

const DOC_REF_TYPES = {
  FOLDER: 'Folder',
  DICTIONARY: 'Dictionary',
  XSLT: 'XSLT',
  TextConverter: 'TextConverter',
  ElasticIndex: 'ElasticIndex',
  AnnotationsIndex: 'AnnotationsIndex',
  Pipeline: 'Pipeline',
  Index: 'Index',
  Dashboard: 'Dashboard',
  Visualisation: 'Visualisation',
};

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
  type: DOC_REF_TYPES.FOLDER,
  children: [
    {
      uuid: guid(),
      type: DOC_REF_TYPES.FOLDER,
      name: 'Some Examples',
      children: [
        {
          uuid: guid(),
          type: DOC_REF_TYPES.FOLDER,
          name: 'Stroom 101',
          children: [
            createRandomItem(DOC_REF_TYPES.DICTIONARY),
            createRandomItem(DOC_REF_TYPES.Pipeline),
            createRandomItem(DOC_REF_TYPES.XSLT),
            createRandomItem(DOC_REF_TYPES.Index),
            createRandomItem(DOC_REF_TYPES.Dashboard),
          ],
        },
        {
          uuid: guid(),
          type: DOC_REF_TYPES.FOLDER,
          name: 'Stroom Elastic Example',
          children: [
            createRandomItem(DOC_REF_TYPES.DICTIONARY),
            createRandomItem(DOC_REF_TYPES.Pipeline),
            createRandomItem(DOC_REF_TYPES.TextConverter),
            createRandomItem(DOC_REF_TYPES.ElasticIndex),
            createRandomItem(DOC_REF_TYPES.Dashboard),
          ],
        },
      ],
    },
    {
      uuid: guid(),
      type: DOC_REF_TYPES.FOLDER,
      name: 'Yet More Examples',
      children: [
        {
          uuid: guid(),
          type: DOC_REF_TYPES.FOLDER,
          name: 'Stroom 102',
          children: [
            createRandomItem(DOC_REF_TYPES.DICTIONARY),
            createRandomItem(DOC_REF_TYPES.Pipeline),
            createRandomItem(DOC_REF_TYPES.XSLT),
            createRandomItem(DOC_REF_TYPES.Index),
            createRandomItem(DOC_REF_TYPES.Dashboard),
            {
              uuid: guid(),
              type: DOC_REF_TYPES.Visualisation,
              name: 'abababababababa',
            },
          ],
        },
        {
          uuid: guid(),
          type: DOC_REF_TYPES.FOLDER,
          name: 'Stroom Annotations Example',
          children: [
            createRandomItem(DOC_REF_TYPES.DICTIONARY),
            createRandomItem(DOC_REF_TYPES.Pipeline),
            createRandomItem(DOC_REF_TYPES.TextConverter),
            createRandomItem(DOC_REF_TYPES.AnnotationsIndex),
            createRandomItem(DOC_REF_TYPES.Dashboard),
          ],
        },
      ],
    },
    {
      uuid: guid(),
      type: DOC_REF_TYPES.FOLDER,
      name: 'Stuff that wont match for tests',
      children: [
        {
          uuid: guid(),
          type: DOC_REF_TYPES.Visualisation,
          name: 'abcdefghijklmnopqrstuvwxyz',
        },
      ],
    },
    {
      uuid: guid(),
      type: DOC_REF_TYPES.Dashboard,
      name: 'ababababababababa',
    },
  ],
};

export { DOC_REF_TYPES, testTree };
