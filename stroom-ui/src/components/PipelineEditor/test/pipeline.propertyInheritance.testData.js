export const noParent = {
  docRef: {
    uuid: 'noParent',
    name: 'No Parent',
    type: 'Pipeline',
  },
  description: 'Demonstrates properties with no parent pipeline',
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

export const childNoProperty = {
  docRef: {
    uuid: 'childNoProperty',
    name: 'Parent No Property',
    type: 'Pipeline',
  },
  description: 'Demonstrates properties when parent has no property',
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

export const childWithProperty = {
  docRef: {
    uuid: 'childWithProperty',
    name: 'Parent With Property',
    type: 'Pipeline',
  },
  description: 'Demonstrates properties with parent that has properties',
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

export const childNoPropertyParentNoProperty = {
  docRef: {
    uuid: 'childNoPropertyParentNoProperty',
    name: 'This No Property, Parent No Property',
    type: 'Pipeline',
  },
  description: 'Demonstrates properties with no property, parent has no property either',
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

export const childNoPropertyParentWithProperty = {
  docRef: {
    uuid: 'childNoPropertyParentWithProperty',
    name: 'This No Property, Parent Has Property',
    type: 'Pipeline',
  },
  description: 'Demonstrates properties with no property, parent has the property either',
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

export const childWithPropertyParentNoProperty = {
  docRef: {
    uuid: 'childWithPropertyParentNoProperty',
    name: 'This With Property, Parent No Property',
    type: 'Pipeline',
  },
  description: 'Demonstrates properties with a property, parent has no property either',
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

export const childWithPropertyParentWithProperty = {
  docRef: {
    uuid: 'childWithPropertyParentWithProperty',
    name: 'This With Property, Parent With Property',
    type: 'Pipeline',
  },
  description: 'Demonstrates properties with a property, parent also has property',
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

export const childWithRemoveForItsParentsAdd = {
  docRef: {
    uuid: 'childWithRemoveForItsParentsAdd',
    name: 'This Removing Property from parent',
    type: 'Pipeline',
  },
  description: 'Demonstrates property removal from a parent',
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

export const emptyChildParentWithProperty = {
  docRef: {
    uuid: 'emptyChildParentWithProperty',
    name: 'Empty Child, Parent With Property',
    type: 'Pipeline',
  },
  description: 'Demonstrates properties',
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
        add: [],
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
