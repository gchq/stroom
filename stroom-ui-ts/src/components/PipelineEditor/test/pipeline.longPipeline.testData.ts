export default {
  docRef: {
    uuid: 'longPipeline',
    name: 'Long Pipeline',
    type: 'Pipeline'
  },
  description: 'This pipeline is long enough to stress out the rendering',
  configStack: [
    {
      elements: {
        add: [
          {
            id: 'dsParser',
            type: 'DSParser',
          },
          {
            id: 'splitFilter',
            type: 'SplitFilter',
          },
          {
            id: 'readCount',
            type: 'RecordCountFilter',
          },
          {
            id: 'xsltFilter',
            type: 'XSLTFilter',
          },
          {
            id: 'idEnrichmentFilter',
            type: 'IdEnrichmentFilter',
          },
          {
            id: 'writeCount',
            type: 'RecordCountFilter',
          },
          {
            id: 'recordOutputFilter',
            type: 'RecordOutputFilter',
          },
          {
            id: 'xsltOut',
            type: 'XSLTFilter',
          },
          {
            id: 'xmlWriter',
            type: 'XMLWriter',
          },
          {
            id: 'kafkaAppender',
            type: 'KafkaAppender',
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
            from: 'Source',
            to: 'dsParser',
          },
          {
            from: 'dsParser',
            to: 'splitFilter',
          },
          {
            from: 'splitFilter',
            to: 'readCount',
          },
          {
            from: 'readCount',
            to: 'xsltFilter',
          },
          {
            from: 'xsltFilter',
            to: 'idEnrichmentFilter',
          },
          {
            from: 'idEnrichmentFilter',
            to: 'writeCount',
          },
          {
            from: 'writeCount',
            to: 'recordOutputFilter',
          },
          {
            from: 'recordOutputFilter',
            to: 'xsltOut',
          },
          {
            from: 'xsltOut',
            to: 'xmlWriter',
          },
          {
            from: 'xmlWriter',
            to: 'kafkaAppender',
          },
        ],
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
          id: 'kafkaAppender',
          type: 'KafkaAppender',
        },
        {
          id: 'xmlWriter',
          type: 'XMLWriter',
        },
        {
          id: 'writeCount',
          type: 'RecordCountFilter',
        },
        {
          id: 'recordOutputFilter',
          type: 'RecordOutputFilter',
        },
        {
          id: 'splitFilter',
          type: 'SplitFilter',
        },
        {
          id: 'idEnrichmentFilter',
          type: 'IdEnrichmentFilter',
        },
        {
          id: 'xsltOut',
          type: 'XSLTFilter',
        },
        {
          id: 'dsParser',
          type: 'DSParser',
        },
        {
          id: 'readCount',
          type: 'RecordCountFilter',
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
          to: 'idEnrichmentFilter',
        },
        {
          from: 'xmlWriter',
          to: 'kafkaAppender',
        },
        {
          from: 'recordOutputFilter',
          to: 'xsltOut',
        },
        {
          from: 'writeCount',
          to: 'recordOutputFilter',
        },
        {
          from: 'idEnrichmentFilter',
          to: 'writeCount',
        },
        {
          from: 'splitFilter',
          to: 'readCount',
        },
        {
          from: 'xsltOut',
          to: 'xmlWriter',
        },
        {
          from: 'readCount',
          to: 'xsltFilter',
        },
        {
          from: 'dsParser',
          to: 'splitFilter',
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
