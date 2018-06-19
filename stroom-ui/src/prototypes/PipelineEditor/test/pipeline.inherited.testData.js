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

export default {
  configStack: [
    {
      elements: {
        add: [
          {
            id: 'dsParser',
            type: 'DSParser',
          },
          {
            id: 'xsltFilter',
            type: 'XSLTFilter',
          },
          {
            id: 'xmlWriter',
            type: 'XMLWriter',
          },
          {
            id: 'streamAppender',
            type: 'StreamAppender',
          },
          {
            id: 'Source',
            type: 'Source',
          },
        ],
        remove: [],
      },
      properties: {
        add: [],
        remove: [],
      },
      pipelineReferences: {
        add: [],
        remove: [],
      },
      links: {
        add: [
          {
            from: 'Source',
            to: 'dsParser',
          },
          {
            from: 'dsParser',
            to: 'xsltFilter',
          },
          {
            from: 'xsltFilter',
            to: 'xmlWriter',
          },
          {
            from: 'xmlWriter',
            to: 'streamAppender',
          },
        ],
        remove: [],
      },
    },
    {
      elements: {
        add: [],
        remove: [],
      },
      properties: {
        add: [
          {
            element: 'dsParser',
            name: 'textConverter',
            value: {
              string: null,
              integer: null,
              entity: {
                type: 'TextConverter',
                uuid: '4fde9c79-796c-4069-bbdb-e707ff558376',
                name: 'My CSV Splitter',
              },
              long: null,
              boolean: null,
            },
          },
          {
            element: 'xsltFilter',
            name: 'xslt',
            value: {
              string: null,
              integer: null,
              entity: {
                type: 'XSLT',
                uuid: '5871080f-b5bb-49d2-9483-5a54f7fb4e7c',
                name: 'MyXSLT',
              },
              long: null,
              boolean: null,
            },
          },
          {
            element: 'streamAppender',
            name: 'feed',
            value: {
              string: null,
              integer: null,
              entity: {
                type: 'Feed',
                uuid: 'c65aa827-d10b-4aea-8bf0-3e361e0ee4dd',
                name: 'MY_FEED',
              },
              long: null,
              boolean: null,
            },
          },
          {
            element: 'streamAppender',
            name: 'streamType',
            value: {
              string: 'Events',
              integer: null,
              entity: null,
              long: null,
              boolean: null,
            },
          },
        ],
        remove: [],
      },
      pipelineReferences: {
        add: [],
        remove: [],
      },
      links: {
        add: [],
        remove: [],
      },
    },
  ],
  merged: {
    elements: {
      add: [
        {
          id: 'xsltFilter',
          type: 'XSLTFilter',
        },
        {
          id: 'streamAppender',
          type: 'StreamAppender',
        },
        {
          id: 'xmlWriter',
          type: 'XMLWriter',
        },
        {
          id: 'dsParser',
          type: 'DSParser',
        },
        {
          id: 'Source',
          type: 'Source',
        },
      ],
      remove: [],
    },
    properties: {
      add: [
        {
          element: 'xsltFilter',
          name: 'xslt',
          value: {
            string: null,
            integer: null,
            entity: {
              type: 'XSLT',
              uuid: '5871080f-b5bb-49d2-9483-5a54f7fb4e7c',
              name: 'MyXSLT',
            },
            long: null,
            boolean: null,
          },
        },
        {
          element: 'streamAppender',
          name: 'feed',
          value: {
            string: null,
            integer: null,
            entity: {
              type: 'Feed',
              uuid: 'c65aa827-d10b-4aea-8bf0-3e361e0ee4dd',
              name: 'MY_FEED',
            },
            long: null,
            boolean: null,
          },
        },
        {
          element: 'streamAppender',
          name: 'streamType',
          value: {
            string: 'Events',
            integer: null,
            entity: null,
            long: null,
            boolean: null,
          },
        },
        {
          element: 'dsParser',
          name: 'textConverter',
          value: {
            string: null,
            integer: null,
            entity: {
              type: 'TextConverter',
              uuid: '4fde9c79-796c-4069-bbdb-e707ff558376',
              name: 'My CSV Splitter',
            },
            long: null,
            boolean: null,
          },
        },
      ],
      remove: [],
    },
    pipelineReferences: {
      add: [],
      remove: [],
    },
    links: {
      add: [
        {
          from: 'xsltFilter',
          to: 'xmlWriter',
        },
        {
          from: 'xmlWriter',
          to: 'streamAppender',
        },
        {
          from: 'dsParser',
          to: 'xsltFilter',
        },
        {
          from: 'Source',
          to: 'dsParser',
        },
      ],
      remove: [],
    },
  },
};
