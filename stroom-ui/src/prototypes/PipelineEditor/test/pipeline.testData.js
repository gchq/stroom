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

import { elements } from './elements.testData';

const myCsvSplitterFilter = {
  id: 'CSV splitter filter',
  type: elements.dsParser.type,
};

const myXsltFilter = {
  id: 'XSLT filter',
  type: elements.xsltFilter.type,
};

const myXmlWriter1 = {
  id: 'XML writer 1',
  type: elements.xmlWriter.type,
};

const myStreamAppender1 = {
  id: 'stream appender 1',
  type: elements.streamAppender.type,
};

const myXmlWriter2 = {
  id: 'XML writer 2',
  type: elements.xmlWriter.type,
};

const myStreamAppender2 = {
  id: 'stream appender 2',
  type: elements.streamAppender.type,
};

const testPipeline = {
  elements: {
    add: [
      myCsvSplitterFilter,
      myXsltFilter,
      myXmlWriter1,
      myStreamAppender1,
      myXmlWriter2,
      myStreamAppender2,
    ],
  },
  properties: {
    add: [
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
  links: {
    add: [
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
};

const singleElementTestPipeline = {
  elements: {
    add: [myCsvSplitterFilter],
  },
  properties: {
    add: [
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
    ],
  },
  links: {
    add: [],
  },
};

const bigTestPipeline = [
  {
    elements: {
      add: [
        {
          elementType: {
            type: 'CombinedParser',
            category: 'PARSER',
            roles: ['parser', 'hasCode', 'simple', 'hasTargets', 'stepping', 'mutator'],
            icon: 'text.svg',
          },
          source: {
            pipeline: {
              type: 'Pipeline',
              uuid: '7630b1ff-e65d-4744-b05f-0ea178a97a41',
              name: 'Common Test Reference Pipeline',
            },
          },
          id: 'combinedParser',
          type: 'CombinedParser',
        },
        {
          elementType: {
            type: 'RecordCountFilter',
            category: 'FILTER',
            roles: ['hasTargets', 'target'],
            icon: 'recordCount.svg',
          },
          source: {
            pipeline: {
              type: 'Pipeline',
              uuid: '7630b1ff-e65d-4744-b05f-0ea178a97a41',
              name: 'Common Test Reference Pipeline',
            },
          },
          id: 'readRecordCountFilter',
          type: 'RecordCountFilter',
        },
        {
          elementType: {
            type: 'SplitFilter',
            category: 'FILTER',
            roles: ['hasTargets', 'target'],
            icon: 'split.svg',
          },
          source: {
            pipeline: {
              type: 'Pipeline',
              uuid: '7630b1ff-e65d-4744-b05f-0ea178a97a41',
              name: 'Common Test Reference Pipeline',
            },
          },
          id: 'splitFilter',
          type: 'SplitFilter',
        },
        {
          elementType: {
            type: 'XSLTFilter',
            category: 'FILTER',
            roles: ['hasCode', 'simple', 'hasTargets', 'stepping', 'mutator', 'target'],
            icon: 'xslt.svg',
          },
          source: {
            pipeline: {
              type: 'Pipeline',
              uuid: '7630b1ff-e65d-4744-b05f-0ea178a97a41',
              name: 'Common Test Reference Pipeline',
            },
          },
          id: 'translationFilter',
          type: 'XSLTFilter',
        },
        {
          elementType: {
            type: 'SchemaFilter',
            category: 'FILTER',
            roles: ['validator', 'hasTargets', 'stepping', 'target'],
            icon: 'xsd.svg',
          },
          source: {
            pipeline: {
              type: 'Pipeline',
              uuid: '7630b1ff-e65d-4744-b05f-0ea178a97a41',
              name: 'Common Test Reference Pipeline',
            },
          },
          id: 'schemaFilter',
          type: 'SchemaFilter',
        },
        {
          elementType: {
            type: 'RecordOutputFilter',
            category: 'FILTER',
            roles: ['hasTargets', 'target'],
            icon: 'recordOutput.svg',
          },
          source: {
            pipeline: {
              type: 'Pipeline',
              uuid: '7630b1ff-e65d-4744-b05f-0ea178a97a41',
              name: 'Common Test Reference Pipeline',
            },
          },
          id: 'recordOutputFilter',
          type: 'RecordOutputFilter',
        },
        {
          elementType: {
            type: 'RecordCountFilter',
            category: 'FILTER',
            roles: ['hasTargets', 'target'],
            icon: 'recordCount.svg',
          },
          source: {
            pipeline: {
              type: 'Pipeline',
              uuid: '7630b1ff-e65d-4744-b05f-0ea178a97a41',
              name: 'Common Test Reference Pipeline',
            },
          },
          id: 'writeRecordCountFilter',
          type: 'RecordCountFilter',
        },
        {
          elementType: {
            type: 'XMLWriter',
            category: 'WRITER',
            roles: ['hasTargets', 'writer', 'mutator', 'stepping', 'target'],
            icon: 'xml.svg',
          },
          source: {
            pipeline: {
              type: 'Pipeline',
              uuid: '7630b1ff-e65d-4744-b05f-0ea178a97a41',
              name: 'Common Test Reference Pipeline',
            },
          },
          id: 'streamStoreXMLWriter',
          type: 'XMLWriter',
        },
        {
          elementType: {
            type: 'StreamAppender',
            category: 'DESTINATION',
            roles: ['destination', 'stepping', 'target'],
            icon: 'stream.svg',
          },
          source: {
            pipeline: {
              type: 'Pipeline',
              uuid: '7630b1ff-e65d-4744-b05f-0ea178a97a41',
              name: 'Common Test Reference Pipeline',
            },
          },
          id: 'streamAppender',
          type: 'StreamAppender',
        },
      ],
      remove: [],
    },
    properties: {
      add: [
        {
          propertyType: {
            elementType: {
              type: 'RecordCountFilter',
              category: 'FILTER',
              roles: ['hasTargets', 'target'],
              icon: 'recordCount.svg',
            },
            name: 'countRead',
            type: 'boolean',
            description: 'Is this filter counting records read or records written?',
            defaultValue: 'true',
            pipelineReference: false,
            docRefTypes: null,
          },
          source: {
            pipeline: {
              type: 'Pipeline',
              uuid: '7630b1ff-e65d-4744-b05f-0ea178a97a41',
              name: 'Common Test Reference Pipeline',
            },
          },
          element: 'readRecordCountFilter',
          name: 'countRead',
          value: {
            string: null,
            integer: null,
            entity: null,
            long: null,
            boolean: true,
          },
        },
        {
          propertyType: {
            elementType: {
              type: 'SplitFilter',
              category: 'FILTER',
              roles: ['hasTargets', 'target'],
              icon: 'split.svg',
            },
            name: 'splitDepth',
            type: 'int',
            description: 'The depth of XML elements to split at.',
            defaultValue: '1',
            pipelineReference: false,
            docRefTypes: null,
          },
          source: {
            pipeline: {
              type: 'Pipeline',
              uuid: '7630b1ff-e65d-4744-b05f-0ea178a97a41',
              name: 'Common Test Reference Pipeline',
            },
          },
          element: 'splitFilter',
          name: 'splitDepth',
          value: {
            string: null,
            integer: 1,
            entity: null,
            long: null,
            boolean: null,
          },
        },
        {
          propertyType: {
            elementType: {
              type: 'SplitFilter',
              category: 'FILTER',
              roles: ['hasTargets', 'target'],
              icon: 'split.svg',
            },
            name: 'splitCount',
            type: 'int',
            description:
              'The number of elements at the split depth to count before the XML is split.',
            defaultValue: '10000',
            pipelineReference: false,
            docRefTypes: null,
          },
          source: {
            pipeline: {
              type: 'Pipeline',
              uuid: '7630b1ff-e65d-4744-b05f-0ea178a97a41',
              name: 'Common Test Reference Pipeline',
            },
          },
          element: 'splitFilter',
          name: 'splitCount',
          value: {
            string: null,
            integer: 100,
            entity: null,
            long: null,
            boolean: null,
          },
        },
        {
          propertyType: {
            elementType: {
              type: 'SchemaFilter',
              category: 'FILTER',
              roles: ['validator', 'hasTargets', 'stepping', 'target'],
              icon: 'xsd.svg',
            },
            name: 'schemaGroup',
            type: 'String',
            description:
              'Limits the schemas that can be used to validate data to those with a matching schema group name.',
            defaultValue: '',
            pipelineReference: false,
            docRefTypes: null,
          },
          source: {
            pipeline: {
              type: 'Pipeline',
              uuid: '7630b1ff-e65d-4744-b05f-0ea178a97a41',
              name: 'Common Test Reference Pipeline',
            },
          },
          element: 'schemaFilter',
          name: 'schemaGroup',
          value: {
            string: 'REFERENCE_DATA',
            integer: null,
            entity: null,
            long: null,
            boolean: null,
          },
        },
        {
          propertyType: {
            elementType: {
              type: 'RecordCountFilter',
              category: 'FILTER',
              roles: ['hasTargets', 'target'],
              icon: 'recordCount.svg',
            },
            name: 'countRead',
            type: 'boolean',
            description: 'Is this filter counting records read or records written?',
            defaultValue: 'true',
            pipelineReference: false,
            docRefTypes: null,
          },
          source: {
            pipeline: {
              type: 'Pipeline',
              uuid: '7630b1ff-e65d-4744-b05f-0ea178a97a41',
              name: 'Common Test Reference Pipeline',
            },
          },
          element: 'writeRecordCountFilter',
          name: 'countRead',
          value: {
            string: null,
            integer: null,
            entity: null,
            long: null,
            boolean: false,
          },
        },
        {
          propertyType: {
            elementType: {
              type: 'XMLWriter',
              category: 'WRITER',
              roles: ['hasTargets', 'writer', 'mutator', 'stepping', 'target'],
              icon: 'xml.svg',
            },
            name: 'indentOutput',
            type: 'boolean',
            description: 'Should output XML be indented and include new lines (pretty printed)?',
            defaultValue: 'false',
            pipelineReference: false,
            docRefTypes: null,
          },
          source: {
            pipeline: {
              type: 'Pipeline',
              uuid: '7630b1ff-e65d-4744-b05f-0ea178a97a41',
              name: 'Common Test Reference Pipeline',
            },
          },
          element: 'streamStoreXMLWriter',
          name: 'indentOutput',
          value: {
            string: null,
            integer: null,
            entity: null,
            long: null,
            boolean: false,
          },
        },
        {
          propertyType: {
            elementType: {
              type: 'StreamAppender',
              category: 'DESTINATION',
              roles: ['destination', 'stepping', 'target'],
              icon: 'stream.svg',
            },
            name: 'streamType',
            type: 'String',
            description:
              'The stream type that the output stream should be written as. This must be specified.',
            defaultValue: '',
            pipelineReference: false,
            docRefTypes: null,
          },
          source: {
            pipeline: {
              type: 'Pipeline',
              uuid: '7630b1ff-e65d-4744-b05f-0ea178a97a41',
              name: 'Common Test Reference Pipeline',
            },
          },
          element: 'streamAppender',
          name: 'streamType',
          value: {
            string: 'Reference',
            integer: null,
            entity: null,
            long: null,
            boolean: null,
          },
        },
        {
          propertyType: {
            elementType: {
              type: 'StreamAppender',
              category: 'DESTINATION',
              roles: ['destination', 'stepping', 'target'],
              icon: 'stream.svg',
            },
            name: 'segmentOutput',
            type: 'boolean',
            description:
              'Should the output stream be marked with indexed segments to allow fast access to individual records?',
            defaultValue: 'true',
            pipelineReference: false,
            docRefTypes: null,
          },
          source: {
            pipeline: {
              type: 'Pipeline',
              uuid: '7630b1ff-e65d-4744-b05f-0ea178a97a41',
              name: 'Common Test Reference Pipeline',
            },
          },
          element: 'streamAppender',
          name: 'segmentOutput',
          value: {
            string: null,
            integer: null,
            entity: null,
            long: null,
            boolean: true,
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
          source: {
            pipeline: {
              type: 'Pipeline',
              uuid: '7630b1ff-e65d-4744-b05f-0ea178a97a41',
              name: 'Common Test Reference Pipeline',
            },
          },
          from: 'combinedParser',
          to: 'readRecordCountFilter',
        },
        {
          source: {
            pipeline: {
              type: 'Pipeline',
              uuid: '7630b1ff-e65d-4744-b05f-0ea178a97a41',
              name: 'Common Test Reference Pipeline',
            },
          },
          from: 'readRecordCountFilter',
          to: 'splitFilter',
        },
        {
          source: {
            pipeline: {
              type: 'Pipeline',
              uuid: '7630b1ff-e65d-4744-b05f-0ea178a97a41',
              name: 'Common Test Reference Pipeline',
            },
          },
          from: 'splitFilter',
          to: 'translationFilter',
        },
        {
          source: {
            pipeline: {
              type: 'Pipeline',
              uuid: '7630b1ff-e65d-4744-b05f-0ea178a97a41',
              name: 'Common Test Reference Pipeline',
            },
          },
          from: 'translationFilter',
          to: 'schemaFilter',
        },
        {
          source: {
            pipeline: {
              type: 'Pipeline',
              uuid: '7630b1ff-e65d-4744-b05f-0ea178a97a41',
              name: 'Common Test Reference Pipeline',
            },
          },
          from: 'schemaFilter',
          to: 'recordOutputFilter',
        },
        {
          source: {
            pipeline: {
              type: 'Pipeline',
              uuid: '7630b1ff-e65d-4744-b05f-0ea178a97a41',
              name: 'Common Test Reference Pipeline',
            },
          },
          from: 'recordOutputFilter',
          to: 'writeRecordCountFilter',
        },
        {
          source: {
            pipeline: {
              type: 'Pipeline',
              uuid: '7630b1ff-e65d-4744-b05f-0ea178a97a41',
              name: 'Common Test Reference Pipeline',
            },
          },
          from: 'writeRecordCountFilter',
          to: 'streamStoreXMLWriter',
        },
        {
          source: {
            pipeline: {
              type: 'Pipeline',
              uuid: '7630b1ff-e65d-4744-b05f-0ea178a97a41',
              name: 'Common Test Reference Pipeline',
            },
          },
          from: 'streamStoreXMLWriter',
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
          propertyType: {
            elementType: {
              type: 'CombinedParser',
              category: 'PARSER',
              roles: ['parser', 'hasCode', 'simple', 'hasTargets', 'stepping', 'mutator'],
              icon: 'text.svg',
            },
            name: 'textConverter',
            type: 'DocRef',
            description:
              'The text converter configuration that should be used to parse the input data.',
            defaultValue: '',
            pipelineReference: false,
            docRefTypes: ['TextConverter'],
          },
          source: {
            pipeline: {
              type: 'Pipeline',
              uuid: '5ab7ec67-1444-46ea-89c6-d46d84b9d012',
              name: 'BITMAP-REFERENCE',
            },
          },
          element: 'combinedParser',
          name: 'textConverter',
          value: {
            string: null,
            integer: null,
            entity: {
              type: 'TextConverter',
              uuid: '8e43a4dd-6780-4055-b8dc-03c6318e31e0',
              name: 'BITMAP-REFERENCE',
            },
            long: null,
            boolean: null,
          },
        },
        {
          propertyType: {
            elementType: {
              type: 'XSLTFilter',
              category: 'FILTER',
              roles: ['hasCode', 'simple', 'hasTargets', 'stepping', 'mutator', 'target'],
              icon: 'xslt.svg',
            },
            name: 'xslt',
            type: 'DocRef',
            description: 'The XSLT to use.',
            defaultValue: '',
            pipelineReference: false,
            docRefTypes: ['XSLT'],
          },
          source: {
            pipeline: {
              type: 'Pipeline',
              uuid: '5ab7ec67-1444-46ea-89c6-d46d84b9d012',
              name: 'BITMAP-REFERENCE',
            },
          },
          element: 'translationFilter',
          name: 'xslt',
          value: {
            string: null,
            integer: null,
            entity: {
              type: 'XSLT',
              uuid: '760d0912-858f-4b8b-b11d-c69a8b412142',
              name: 'BITMAP-REFERENCE',
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
      add: [],
      remove: [],
    },
  },
];

const testPipelineElements = {
  myCsvSplitterFilter,
  myXsltFilter,
  myStreamAppender1,
  myStreamAppender2,
  myXmlWriter1,
  myXmlWriter2,
};

export { testPipeline, singleElementTestPipeline, testPipelineElements };
