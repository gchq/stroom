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

const testPipeline = {
  elements: {
    add: {
      element: [
        {
          id: 'CSV splitter filter',
          type: 'DSParser',
        },
        {
          id: 'XSLT filter',
          type: 'XSLTFilter',
        },
        {
          id: 'XML writer 1',
          type: 'XMLWriter',
        },
        {
          id: 'stream appender 1',
          type: 'StreamAppender',
        },
        {
          id: 'XML writer 2',
          type: 'XMLWriter',
        },
        {
          id: 'stream appender 2',
          type: 'StreamAppender',
        },
      ],
    },
  },
  properties: {
    add: {
      property: [
        {
          element: 'CSV splitter filter',
          name: 'textConverter',
          value: {
            entity: {
              type: 'TextConverter',
              uuid: '4fe46544-fbf6-4a0d-ab44-16cd0e00a0a5',
              name: 'CSV splitter',
            },
          },
        },
        {
          element: 'XSLT filter',
          name: 'xslt',
          value: {
            entity: {
              type: 'XSLT',
              uuid: 'efb3738b-f7f3-44b9-839e-b74b341c78ee',
              name: 'XSLT',
            },
          },
        },
        {
          element: 'stream appender 1',
          name: 'feed',
          value: {
            entity: {
              type: 'Feed',
              uuid: '306959c0-7125-492d-8f0d-81af248a85f2',
              name: 'CSV_FEED',
            },
          },
        },
        {
          element: 'stream appender 1',
          name: 'streamType',
          value: { string: 'Events' },
        },
        {
          element: 'stream appender 2',
          name: 'feed',
          value: {
            entity: {
              type: 'Feed',
              uuid: '306959c0-7125-492d-8f0d-81af248a85f2',
              name: 'CSV_FEED',
            },
          },
        },
        {
          element: 'stream appender 2',
          name: 'streamType',
          value: { string: 'Events' },
        },
      ],
    },
  },
  links: {
    add: {
      link: [
        {
          from: 'CSV splitter filter',
          to: 'XSLT filter',
        },
        {
          from: 'XSLT filter',
          to: 'XML writer 1',
        },
        {
          from: 'XML writer 1',
          to: 'stream appender 1',
        },
        {
          from: 'XSLT filter',
          to: 'XML writer 2',
        },
        {
          from: 'XML writer 2',
          to: 'stream appender 2',
        },
      ],
    },
  },
};

export { testPipeline };
