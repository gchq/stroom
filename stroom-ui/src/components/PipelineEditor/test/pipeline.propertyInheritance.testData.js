export const noParent = {
  description: 'Demonstrates properties',
  configStack: [
    {
      elements: {
        add: [
          {
            id: 'combinedParser',
            type: 'CombinedParser',
          },
          {
            id: 'xsltFilter',
            type: 'XSLTFilter',
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
            name: 'xsltNamePattern',
            value: {
              string: 'DSD',
              integer: null,
              entity: null,
              boolean: null,
              long: null,
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
            to: 'combinedParser',
          },
          {
            from: 'combinedParser',
            to: 'xsltFilter',
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
          id: 'combinedParser',
          type: 'CombinedParser',
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
          name: 'xsltNamePattern',
          value: {
            string: 'DSD',
            integer: null,
            entity: null,
            boolean: null,
            long: null,
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
          from: 'combinedParser',
          to: 'xsltFilter',
        },
        {
          from: 'Source',
          to: 'combinedParser',
        },
      ],
      remove: [],
    },
  },
};

export const parentNoProperty = {
  configStack: [
    {
      elements: {
        add: [
          { id: 'combinedParser', type: 'CombinedParser' },
          { id: 'xsltFilter', type: 'XSLTFilter' },
          { id: 'Source', type: 'Source' },
        ],
        remove: [],
      },
      properties: {
        add: [
          {
            element: 'xsltFilter',
            name: 'xsltNamePattern',
            value: {
              string: 'DSD',
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
          { from: 'Source', to: 'combinedParser' },
          { from: 'combinedParser', to: 'xsltFilter' },
        ],
        remove: [],
      },
    },
    {
      elements: { add: [], remove: [] },
      properties: {
        add: [
          {
            element: 'combinedParser',
            name: 'type',
            value: {
              string: 'JSON',
              integer: null,
              entity: null,
              boolean: null,
              long: null,
            },
          },
          {
            element: 'xsltFilter',
            name: 'xsltNamePattern',
            value: {
              string: 'D',
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
      links: { add: [], remove: [] },
    },
  ],
  merged: {
    elements: {
      add: [
        { id: 'xsltFilter', type: 'XSLTFilter' },
        { id: 'combinedParser', type: 'CombinedParser' },
        { id: 'Source', type: 'Source' },
      ],
      remove: [],
    },
    properties: {
      add: [
        {
          element: 'xsltFilter',
          name: 'xsltNamePattern',
          value: {
            string: 'D',
            integer: null,
            entity: null,
            boolean: null,
            long: null,
          },
        },
        {
          element: 'combinedParser',
          name: 'type',
          value: {
            string: 'JSON',
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
      add: [{ from: 'combinedParser', to: 'xsltFilter' }, { from: 'Source', to: 'combinedParser' }],
      remove: [],
    },
  },
};

export const parentWithProperty = {
  configStack: [
    {
      elements: {
        add: [
          { id: 'combinedParser', type: 'CombinedParser' },
          { id: 'xsltFilter', type: 'XSLTFilter' },
          { id: 'Source', type: 'Source' },
        ],
        remove: [],
      },
      properties: {
        add: [
          {
            element: 'combinedParser',
            name: 'type',
            value: {
              string: 'JS',
              integer: null,
              entity: null,
              boolean: null,
              long: null,
            },
          },
          {
            element: 'xsltFilter',
            name: 'xsltNamePattern',
            value: {
              string: 'DSD',
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
          { from: 'Source', to: 'combinedParser' },
          { from: 'combinedParser', to: 'xsltFilter' },
        ],
        remove: [],
      },
    },
    {
      elements: { add: [], remove: [] },
      properties: {
        add: [
          {
            element: 'combinedParser',
            name: 'type',
            value: {
              string: 'JSON',
              integer: null,
              entity: null,
              boolean: null,
              long: null,
            },
          },
          {
            element: 'xsltFilter',
            name: 'xsltNamePattern',
            value: {
              string: 'D',
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
      links: { add: [], remove: [] },
    },
  ],
  merged: {
    elements: {
      add: [
        { id: 'xsltFilter', type: 'XSLTFilter' },
        { id: 'combinedParser', type: 'CombinedParser' },
        { id: 'Source', type: 'Source' },
      ],
      remove: [],
    },
    properties: {
      add: [
        {
          element: 'xsltFilter',
          name: 'xsltNamePattern',
          value: {
            string: 'D',
            integer: null,
            entity: null,
            boolean: null,
            long: null,
          },
        },
        {
          element: 'combinedParser',
          name: 'type',
          value: {
            string: 'JSON',
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
      add: [{ from: 'combinedParser', to: 'xsltFilter' }, { from: 'Source', to: 'combinedParser' }],
      remove: [],
    },
  },
};

export const parentNoPropertyParentNoProperty = {
  configStack: [
    {
      elements: {
        add: [
          { id: 'combinedParser', type: 'CombinedParser' },
          { id: 'xsltFilter', type: 'XSLTFilter' },
          { id: 'Source', type: 'Source' },
        ],
        remove: [],
      },
      properties: {
        add: [
          {
            element: 'xsltFilter',
            name: 'xsltNamePattern',
            value: {
              string: 'DSD',
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
          { from: 'Source', to: 'combinedParser' },
          { from: 'combinedParser', to: 'xsltFilter' },
        ],
        remove: [],
      },
    },
    {
      elements: {
        add: [
          { id: 'combinedParser', type: 'CombinedParser' },
          { id: 'xsltFilter', type: 'XSLTFilter' },
          { id: 'Source', type: 'Source' },
        ],
        remove: [],
      },
      properties: {
        add: [
          {
            element: 'xsltFilter',
            name: 'xsltNamePattern',
            value: {
              string: 'DSD',
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
          { from: 'Source', to: 'combinedParser' },
          { from: 'combinedParser', to: 'xsltFilter' },
        ],
        remove: [],
      },
    },
    {
      elements: { add: [], remove: [] },
      properties: {
        add: [
          {
            element: 'combinedParser',
            name: 'type',
            value: {
              string: 'JSON',
              integer: null,
              entity: null,
              boolean: null,
              long: null,
            },
          },
          {
            element: 'xsltFilter',
            name: 'xsltNamePattern',
            value: {
              string: 'D',
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
      links: { add: [], remove: [] },
    },
  ],
  merged: {
    elements: {
      add: [
        { id: 'xsltFilter', type: 'XSLTFilter' },
        { id: 'combinedParser', type: 'CombinedParser' },
        { id: 'Source', type: 'Source' },
      ],
      remove: [],
    },
    properties: {
      add: [
        {
          element: 'xsltFilter',
          name: 'xsltNamePattern',
          value: {
            string: 'D',
            integer: null,
            entity: null,
            boolean: null,
            long: null,
          },
        },
        {
          element: 'combinedParser',
          name: 'type',
          value: {
            string: 'JSON',
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
      add: [{ from: 'combinedParser', to: 'xsltFilter' }, { from: 'Source', to: 'combinedParser' }],
      remove: [],
    },
  },
};

export const parentNoPropertyParentWithProperty = {
  configStack: [
    {
      elements: {
        add: [
          { id: 'combinedParser', type: 'CombinedParser' },
          { id: 'xsltFilter', type: 'XSLTFilter' },
          { id: 'Source', type: 'Source' },
        ],
        remove: [],
      },
      properties: {
        add: [
          {
            element: 'xsltFilter',
            name: 'xsltNamePattern',
            value: {
              string: 'DSD',
              integer: null,
              entity: null,
              boolean: null,
              long: null,
            },
          },
          {
            element: 'xsltFilter',
            name: 'property1',
            value: {
              string: null,
              integer: null,
              entity: null,
              boolean: false,
              long: null,
            },
          },
        ],
        remove: [],
      },
      pipelineReferences: { add: [], remove: [] },
      links: {
        add: [
          { from: 'Source', to: 'combinedParser' },
          { from: 'combinedParser', to: 'xsltFilter' },
        ],
        remove: [],
      },
    },
    {
      elements: {
        add: [
          { id: 'combinedParser', type: 'CombinedParser' },
          { id: 'xsltFilter', type: 'XSLTFilter' },
          { id: 'Source', type: 'Source' },
        ],
        remove: [],
      },
      properties: {
        add: [
          {
            element: 'xsltFilter',
            name: 'xsltNamePattern',
            value: {
              string: 'DSD',
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
          { from: 'Source', to: 'combinedParser' },
          { from: 'combinedParser', to: 'xsltFilter' },
        ],
        remove: [],
      },
    },
    {
      elements: { add: [], remove: [] },
      properties: {
        add: [
          {
            element: 'combinedParser',
            name: 'type',
            value: {
              string: 'JSON',
              integer: null,
              entity: null,
              boolean: null,
              long: null,
            },
          },
          {
            element: 'xsltFilter',
            name: 'xsltNamePattern',
            value: {
              string: 'D',
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
      links: { add: [], remove: [] },
    },
  ],
  merged: {
    elements: {
      add: [
        { id: 'xsltFilter', type: 'XSLTFilter' },
        { id: 'combinedParser', type: 'CombinedParser' },
        { id: 'Source', type: 'Source' },
      ],
      remove: [],
    },
    properties: {
      add: [
        {
          element: 'xsltFilter',
          name: 'xsltNamePattern',
          value: {
            string: 'D',
            integer: null,
            entity: null,
            boolean: null,
            long: null,
          },
        },
        {
          element: 'combinedParser',
          name: 'type',
          value: {
            string: 'JSON',
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
      add: [{ from: 'combinedParser', to: 'xsltFilter' }, { from: 'Source', to: 'combinedParser' }],
      remove: [],
    },
  },
};

export const parentWithPropertyParentNoProperty = {
  configStack: [
    {
      elements: {
        add: [
          { id: 'combinedParser', type: 'CombinedParser' },
          { id: 'xsltFilter', type: 'XSLTFilter' },
          { id: 'Source', type: 'Source' },
        ],
        remove: [],
      },
      properties: {
        add: [
          {
            element: 'xsltFilter',
            name: 'xsltNamePattern',
            value: {
              string: 'DSD',
              integer: null,
              entity: null,
              boolean: null,
              long: null,
            },
          },
          {
            element: 'xsltFilter',
            name: 'property2',
            value: {
              string: null,
              integer: null,
              entity: null,
              boolean: false,
              long: null,
            },
          },
        ],
        remove: [],
      },
      pipelineReferences: { add: [], remove: [] },
      links: {
        add: [
          { from: 'Source', to: 'combinedParser' },
          { from: 'combinedParser', to: 'xsltFilter' },
        ],
        remove: [],
      },
    },
    {
      elements: {
        add: [
          { id: 'combinedParser', type: 'CombinedParser' },
          { id: 'xsltFilter', type: 'XSLTFilter' },
          { id: 'Source', type: 'Source' },
        ],
        remove: [],
      },
      properties: {
        add: [
          {
            element: 'xsltFilter',
            name: 'xsltNamePattern',
            value: {
              string: 'DSD',
              integer: null,
              entity: null,
              boolean: null,
              long: null,
            },
          },
          {
            element: 'xsltFilter',
            name: 'property1',
            value: {
              string: null,
              integer: null,
              entity: null,
              boolean: false,
              long: null,
            },
          },
        ],
        remove: [],
      },
      pipelineReferences: { add: [], remove: [] },
      links: {
        add: [
          { from: 'Source', to: 'combinedParser' },
          { from: 'combinedParser', to: 'xsltFilter' },
        ],
        remove: [],
      },
    },
    {
      elements: { add: [], remove: [] },
      properties: {
        add: [
          {
            element: 'combinedParser',
            name: 'type',
            value: {
              string: 'JSON',
              integer: null,
              entity: null,
              boolean: null,
              long: null,
            },
          },
          {
            element: 'xsltFilter',
            name: 'xsltNamePattern',
            value: {
              string: 'D',
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
      links: { add: [], remove: [] },
    },
  ],
  merged: {
    elements: {
      add: [
        { id: 'xsltFilter', type: 'XSLTFilter' },
        { id: 'combinedParser', type: 'CombinedParser' },
        { id: 'Source', type: 'Source' },
      ],
      remove: [],
    },
    properties: {
      add: [
        {
          element: 'xsltFilter',
          name: 'xsltNamePattern',
          value: {
            string: 'D',
            integer: null,
            entity: null,
            boolean: null,
            long: null,
          },
        },
        {
          element: 'combinedParser',
          name: 'type',
          value: {
            string: 'JSON',
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
      add: [{ from: 'combinedParser', to: 'xsltFilter' }, { from: 'Source', to: 'combinedParser' }],
      remove: [],
    },
  },
};

export const parentWithPropertyParentWithProperty = {
  configStack: [
    {
      elements: {
        add: [
          { id: 'combinedParser', type: 'CombinedParser' },
          { id: 'xsltFilter', type: 'XSLTFilter' },
          { id: 'Source', type: 'Source' },
        ],
        remove: [],
      },
      properties: {
        add: [
          {
            element: 'xsltFilter',
            name: 'xsltNamePattern',
            value: {
              string: 'DSD123',
              integer: null,
              entity: null,
              boolean: null,
              long: null,
            },
          },
          {
            element: 'xsltFilter',
            name: 'property2',
            value: {
              string: null,
              integer: null,
              entity: null,
              boolean: false,
              long: null,
            },
          },
        ],
        remove: [],
      },
      pipelineReferences: { add: [], remove: [] },
      links: {
        add: [
          { from: 'Source', to: 'combinedParser' },
          { from: 'combinedParser', to: 'xsltFilter' },
        ],
        remove: [],
      },
    },
    {
      elements: {
        add: [
          { id: 'combinedParser', type: 'CombinedParser' },
          { id: 'xsltFilter', type: 'XSLTFilter' },
          { id: 'Source', type: 'Source' },
        ],
        remove: [],
      },
      properties: {
        add: [
          {
            element: 'xsltFilter',
            name: 'xsltNamePattern',
            value: {
              string: 'DSD',
              integer: null,
              entity: null,
              boolean: null,
              long: null,
            },
          },
          {
            element: 'xsltFilter',
            name: 'property1',
            value: {
              string: null,
              integer: null,
              entity: null,
              boolean: false,
              long: null,
            },
          },
        ],
        remove: [],
      },
      pipelineReferences: { add: [], remove: [] },
      links: {
        add: [
          { from: 'Source', to: 'combinedParser' },
          { from: 'combinedParser', to: 'xsltFilter' },
        ],
        remove: [],
      },
    },
    {
      elements: { add: [], remove: [] },
      properties: {
        add: [
          {
            element: 'combinedParser',
            name: 'type',
            value: {
              string: 'JSON',
              integer: null,
              entity: null,
              boolean: null,
              long: null,
            },
          },
          {
            element: 'xsltFilter',
            name: 'xsltNamePattern',
            value: {
              string: 'D',
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
      links: { add: [], remove: [] },
    },
  ],
  merged: {
    elements: {
      add: [
        { id: 'xsltFilter', type: 'XSLTFilter' },
        { id: 'combinedParser', type: 'CombinedParser' },
        { id: 'Source', type: 'Source' },
      ],
      remove: [],
    },
    properties: {
      add: [
        {
          element: 'xsltFilter',
          name: 'xsltNamePattern',
          value: {
            string: 'D',
            integer: null,
            entity: null,
            boolean: null,
            long: null,
          },
        },
        {
          element: 'combinedParser',
          name: 'type',
          value: {
            string: 'JSON',
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
      add: [{ from: 'combinedParser', to: 'xsltFilter' }, { from: 'Source', to: 'combinedParser' }],
      remove: [],
    },
  },
};

export const parentWithRemoveforItsParentsAdd = {
  configStack: [
    {
      elements: {
        add: [
          { id: 'combinedParser', type: 'CombinedParser' },
          { id: 'xsltFilter', type: 'XSLTFilter' },
          { id: 'Source', type: 'Source' },
        ],
        remove: [],
      },
      properties: {
        add: [
          {
            element: 'xsltFilter',
            name: 'xsltNamePattern',
            value: {
              string: 'DSD123',
              integer: null,
              entity: null,
              boolean: null,
              long: null,
            },
          },
          {
            element: 'xsltFilter',
            name: 'property2',
            value: {
              string: null,
              integer: null,
              entity: null,
              boolean: false,
              long: null,
            },
          },
        ],
        remove: [],
      },
      pipelineReferences: { add: [], remove: [] },
      links: {
        add: [
          { from: 'Source', to: 'combinedParser' },
          { from: 'combinedParser', to: 'xsltFilter' },
        ],
        remove: [],
      },
    },
    {
      elements: {
        add: [
          { id: 'combinedParser', type: 'CombinedParser' },
          { id: 'xsltFilter', type: 'XSLTFilter' },
          { id: 'Source', type: 'Source' },
        ],
        remove: [],
      },
      properties: {
        add: [
          {
            element: 'xsltFilter',
            name: 'xsltNamePattern',
            value: {
              string: 'DSD',
              integer: null,
              entity: null,
              boolean: null,
              long: null,
            },
          },
          {
            element: 'xsltFilter',
            name: 'property1',
            value: {
              string: null,
              integer: null,
              entity: null,
              boolean: false,
              long: null,
            },
          },
        ],
        remove: [
          {
            element: 'xsltFilter',
            name: 'property2',
            value: {
              string: null,
              integer: null,
              entity: null,
              boolean: false,
              long: null,
            },
          },
        ],
      },
      pipelineReferences: { add: [], remove: [] },
      links: {
        add: [
          { from: 'Source', to: 'combinedParser' },
          { from: 'combinedParser', to: 'xsltFilter' },
        ],
        remove: [],
      },
    },
    {
      elements: { add: [], remove: [] },
      properties: {
        add: [
          {
            element: 'combinedParser',
            name: 'type',
            value: {
              string: 'JSON',
              integer: null,
              entity: null,
              boolean: null,
              long: null,
            },
          },
          {
            element: 'xsltFilter',
            name: 'xsltNamePattern',
            value: {
              string: 'D',
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
      links: { add: [], remove: [] },
    },
  ],
  merged: {
    elements: {
      add: [
        { id: 'xsltFilter', type: 'XSLTFilter' },
        { id: 'combinedParser', type: 'CombinedParser' },
        { id: 'Source', type: 'Source' },
      ],
      remove: [],
    },
    properties: {
      add: [
        {
          element: 'xsltFilter',
          name: 'xsltNamePattern',
          value: {
            string: 'D',
            integer: null,
            entity: null,
            boolean: null,
            long: null,
          },
        },
        {
          element: 'combinedParser',
          name: 'type',
          value: {
            string: 'JSON',
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
      add: [{ from: 'combinedParser', to: 'xsltFilter' }, { from: 'Source', to: 'combinedParser' }],
      remove: [],
    },
  },
};
