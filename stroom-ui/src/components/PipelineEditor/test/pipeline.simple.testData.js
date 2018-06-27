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
                name: 'dsParser',
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
                name: 'xsltFilter',
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
              name: 'xsltFilter',
            },
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
              name: 'dsParser',
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
