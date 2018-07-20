// This is pipeline b15e0cc8-3f82-446d-b106-04f43c38e19c
const pipeline01 = {
  elements: {
    add: [
      {
        elementType: null,
        source: null,
        id: 'combinedParser',
        type: 'CombinedParser',
      },
      {
        elementType: null,
        source: null,
        id: 'readRecordCountFilter',
        type: 'RecordCountFilter',
      },
      {
        elementType: null,
        source: null,
        id: 'splitFilter',
        type: 'SplitFilter',
      },
      {
        elementType: null,
        source: null,
        id: 'translationFilter',
        type: 'XSLTFilter',
      },
      {
        elementType: null,
        source: null,
        id: 'schemaFilter',
        type: 'SchemaFilter',
      },
      {
        elementType: null,
        source: null,
        id: 'recordOutputFilter',
        type: 'RecordOutputFilter',
      },
      {
        elementType: null,
        source: null,
        id: 'writeRecordCountFilter',
        type: 'RecordCountFilter',
      },
      {
        elementType: null,
        source: null,
        id: 'xmlWriter',
        type: 'XMLWriter',
      },
      {
        elementType: null,
        source: null,
        id: 'streamAppender',
        type: 'StreamAppender',
      },
    ],
    remove: [],
  },
  properties: {
    add: [
      {
        propertyType: null,
        source: null,
        element: 'readRecordCountFilter',
        name: 'countRead',
        value: {
          string: null,
          integer: null,
          entity: null,
          boolean: true,
          long: null,
        },
      },
      {
        propertyType: null,
        source: null,
        element: 'splitFilter',
        name: 'splitDepth',
        value: {
          string: null,
          integer: 1,
          entity: null,
          boolean: null,
          long: null,
        },
      },
      {
        propertyType: null,
        source: null,
        element: 'splitFilter',
        name: 'splitCount',
        value: {
          string: null,
          integer: 100,
          entity: null,
          boolean: null,
          long: null,
        },
      },
      {
        propertyType: null,
        source: null,
        element: 'schemaFilter',
        name: 'schemaGroup',
        value: {
          string: 'REFERENCE_DATA',
          integer: null,
          entity: null,
          boolean: null,
          long: null,
        },
      },
      {
        propertyType: null,
        source: null,
        element: 'writeRecordCountFilter',
        name: 'countRead',
        value: {
          string: null,
          integer: null,
          entity: null,
          boolean: false,
          long: null,
        },
      },
      {
        propertyType: null,
        source: null,
        element: 'streamAppender',
        name: 'segmentOutput',
        value: {
          string: null,
          integer: null,
          entity: null,
          boolean: true,
          long: null,
        },
      },
      {
        propertyType: null,
        source: null,
        element: 'streamAppender',
        name: 'streamType',
        value: {
          string: 'Reference',
          integer: null,
          entity: null,
          boolean: null,
          long: null,
        },
      },
    ],
    remove: [],
  },
  pipelineReferences: { add: [], remove: [] },
  links: {
    add: [
      { source: null, from: 'combinedParser', to: 'readRecordCountFilter' },
      { source: null, from: 'readRecordCountFilter', to: 'splitFilter' },
      { source: null, from: 'splitFilter', to: 'translationFilter' },
      { source: null, from: 'translationFilter', to: 'schemaFilter' },
      { source: null, from: 'schemaFilter', to: 'recordOutputFilter' },
      { source: null, from: 'recordOutputFilter', to: 'writeRecordCountFilter' },
      { source: null, from: 'writeRecordCountFilter', to: 'xmlWriter' },
      { source: null, from: 'xmlWriter', to: 'streamAppender' },
    ],
    remove: [],
  },
};

export { pipeline01 };
