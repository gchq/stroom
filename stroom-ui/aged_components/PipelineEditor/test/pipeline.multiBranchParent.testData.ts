export default {
  docRef: {
    uuid: 'multiBranchParent',
    name: 'Multi Branch Parent',
    type: 'Pipeline'
  },
  description: 'Another multi branching pipeline',
  configStack: [
    {
      elements: {
        add: [
          {
            id: 'dsParser1',
            type: 'DSParser',
          },
          {
            id: 'dsParser2',
            type: 'DSParser',
          },
          {
            id: 'dsParser3',
            type: 'DSParser',
          },
          {
            id: 'xsltFilter1',
            type: 'XSLTFilter',
          },
          {
            id: 'xmlWriter1',
            type: 'XMLWriter',
          },
          {
            id: 'streamAppender1',
            type: 'StreamAppender',
          },
          {
            id: 'idEnrichmentFilter2',
            type: 'IdEnrichmentFilter',
          },
          {
            id: 'xmlWriter2',
            type: 'XMLWriter',
          },
          {
            id: 'fileAppender2',
            type: 'FileAppender',
          },
          {
            id: 'recordCountFilter3',
            type: 'RecordCountFilter',
          },
          {
            id: 'xmlWriter3',
            type: 'XMLWriter',
          },
          {
            id: 'hdfsFileAppender3',
            type: 'HDFSFileAppender',
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
            to: 'dsParser1',
          },
          {
            from: 'Source',
            to: 'dsParser2',
          },
          {
            from: 'Source',
            to: 'dsParser3',
          },
          {
            from: 'dsParser1',
            to: 'xsltFilter1',
          },
          {
            from: 'dsParser2',
            to: 'idEnrichmentFilter2',
          },
          {
            from: 'dsParser3',
            to: 'recordCountFilter3',
          },
          {
            from: 'xsltFilter1',
            to: 'xmlWriter1',
          },
          {
            from: 'xmlWriter1',
            to: 'streamAppender1',
          },
          {
            from: 'idEnrichmentFilter2',
            to: 'xmlWriter2',
          },
          {
            from: 'xmlWriter2',
            to: 'fileAppender2',
          },
          {
            from: 'recordCountFilter3',
            to: 'xmlWriter3',
          },
          {
            from: 'xmlWriter3',
            to: 'hdfsFileAppender3',
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
          id: 'hdfsFileAppender3',
          type: 'HDFSFileAppender',
        },
        {
          id: 'streamAppender1',
          type: 'StreamAppender',
        },
        {
          id: 'Source',
          type: 'Source',
        },
        {
          id: 'dsParser3',
          type: 'DSParser',
        },
        {
          id: 'dsParser2',
          type: 'DSParser',
        },
        {
          id: 'dsParser1',
          type: 'DSParser',
        },
        {
          id: 'xsltFilter1',
          type: 'XSLTFilter',
        },
        {
          id: 'xmlWriter1',
          type: 'XMLWriter',
        },
        {
          id: 'fileAppender2',
          type: 'FileAppender',
        },
        {
          id: 'recordCountFilter3',
          type: 'RecordCountFilter',
        },
        {
          id: 'idEnrichmentFilter2',
          type: 'IdEnrichmentFilter',
        },
        {
          id: 'xmlWriter3',
          type: 'XMLWriter',
        },
        {
          id: 'xmlWriter2',
          type: 'XMLWriter',
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
          from: 'dsParser3',
          to: 'recordCountFilter3',
        },
        {
          from: 'dsParser2',
          to: 'idEnrichmentFilter2',
        },
        {
          from: 'xmlWriter1',
          to: 'streamAppender1',
        },
        {
          from: 'xsltFilter1',
          to: 'xmlWriter1',
        },
        {
          from: 'dsParser1',
          to: 'xsltFilter1',
        },
        {
          from: 'recordCountFilter3',
          to: 'xmlWriter3',
        },
        {
          from: 'idEnrichmentFilter2',
          to: 'xmlWriter2',
        },
        {
          from: 'xmlWriter3',
          to: 'hdfsFileAppender3',
        },
        {
          from: 'xmlWriter2',
          to: 'fileAppender2',
        },
        {
          from: 'Source',
          to: 'dsParser1',
        },
        {
          from: 'Source',
          to: 'dsParser2',
        },
        {
          from: 'Source',
          to: 'dsParser3',
        },
      ],
      remove: [],
    },
  },
};
