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

import { elements } from './testElements';

let myCsvSplitterFilter = {
  id: 'CSV splitter filter',
  type: elements.dsParser.type,
};

let myXsltFilter = {
  id: 'XSLT filter',
  type: elements.xsltFilter.type,
};

let myXmlWriter1 = {
  id: 'XML writer 1',
  type: elements.xmlWriter.type,
}

let myStreamAppender1 = {
  id: 'stream appender 1',
  type: elements.streamAppender.type,
};

let myXmlWriter2 = {
  id: 'XML writer 2',
  type: elements.xmlWriter.type,
};

let myStreamAppender2 = {
  id: 'stream appender 2',
  type: elements.streamAppender.type,
};

const testPipeline = {
  elements: {
    add: {
      element: [
        myCsvSplitterFilter,
        myXsltFilter,
        myXmlWriter1,
        myStreamAppender1,
        myXmlWriter2,
        myStreamAppender2
      ]
    }
  },
  properties: {
    add: {
      property: [
        {
          element: myCsvSplitterFilter.id,
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
          element: myXsltFilter.id,
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
          element: myStreamAppender1.id,
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
          element: myStreamAppender1.id,
          name: 'streamType',
          value: { string: 'Events' },
        },
        {
          element: myStreamAppender2.id,
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
          element: myStreamAppender2.id,
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
          from: myCsvSplitterFilter.id,
          to: myXsltFilter.id,
        },
        {
          from: myXsltFilter.id,
          to: myXmlWriter1.id,
        },
        {
          from: myXmlWriter1.id,
          to: myStreamAppender1.id,
        },
        {
          from: myXsltFilter.id,
          to: myXmlWriter2.id,
        },
        {
          from: myXmlWriter2.id,
          to: myStreamAppender2.id,
        },
      ],
    },
  },
};

let testPipelineElements = {
  myCsvSplitterFilter,
  myXsltFilter,
  myStreamAppender1,
  myStreamAppender2,
  myXmlWriter1,
  myXmlWriter2
}

export {
  testPipeline,
  testPipelineElements
};
