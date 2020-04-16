import * as uuidv4 from "uuid/v4";
import { PipelineDocumentType } from "components/DocumentEditors/useDocumentApi/types/pipelineDoc";

export const noParent = {
  uuid: uuidv4(),
  name: "No Parent",
  type: "Pipeline",
  description: "Demonstrates properties with no parent pipeline",
  configStack: [
    {
      elements: {
        add: [
          {
            id: "combinedParser",
            type: "CombinedParser",
          },
          {
            id: "xsltFilter",
            type: "XSLTFilter",
          },
          {
            id: "Source",
            type: "Source",
          },
        ],
        remove: [],
      },
      properties: {
        add: [
          {
            element: "xsltFilter",
            name: "xsltNamePattern",
            value: {
              string: "DSD",
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
            from: "Source",
            to: "combinedParser",
          },
          {
            from: "combinedParser",
            to: "xsltFilter",
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
          id: "xsltFilter",
          type: "XSLTFilter",
        },
        {
          id: "combinedParser",
          type: "CombinedParser",
        },
        {
          id: "Source",
          type: "Source",
        },
      ],
      remove: [],
    },
    properties: {
      add: [
        {
          element: "xsltFilter",
          name: "xsltNamePattern",
          value: {
            string: "DSD",
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
          from: "combinedParser",
          to: "xsltFilter",
        },
        {
          from: "Source",
          to: "combinedParser",
        },
      ],
      remove: [],
    },
  },
} as PipelineDocumentType;

export const childNoProperty = {
  uuid: "childNoProperty",
  name: "Parent No Property",
  type: "Pipeline",
  description: "Demonstrates properties when parent has no property",
  configStack: [
    {
      elements: {
        add: [
          { id: "combinedParser", type: "CombinedParser" },
          { id: "xsltFilter", type: "XSLTFilter" },
          { id: "Source", type: "Source" },
        ],
        remove: [],
      },
      properties: {
        add: [
          {
            element: "xsltFilter",
            name: "xsltNamePattern",
            value: {
              string: "DSD",
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
          { from: "Source", to: "combinedParser" },
          { from: "combinedParser", to: "xsltFilter" },
        ],
        remove: [],
      },
    },
    {
      elements: { add: [], remove: [] },
      properties: {
        add: [
          {
            element: "combinedParser",
            name: "type",
            value: {
              string: "JSON",
              integer: null,
              entity: null,
              boolean: null,
              long: null,
            },
          },
          {
            element: "xsltFilter",
            name: "xsltNamePattern",
            value: {
              string: "D",
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
        { id: "xsltFilter", type: "XSLTFilter" },
        { id: "combinedParser", type: "CombinedParser" },
        { id: "Source", type: "Source" },
      ],
      remove: [],
    },
    properties: {
      add: [
        {
          element: "xsltFilter",
          name: "xsltNamePattern",
          value: {
            string: "D",
            integer: null,
            entity: null,
            boolean: null,
            long: null,
          },
        },
        {
          element: "combinedParser",
          name: "type",
          value: {
            string: "JSON",
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
        { from: "combinedParser", to: "xsltFilter" },
        { from: "Source", to: "combinedParser" },
      ],
      remove: [],
    },
  },
} as PipelineDocumentType;

export const childWithProperty = {
  uuid: "childWithProperty",
  name: "Parent With Property",
  type: "Pipeline",
  description: "Demonstrates properties with parent that has properties",
  configStack: [
    {
      elements: {
        add: [
          { id: "combinedParser", type: "CombinedParser" },
          { id: "xsltFilter", type: "XSLTFilter" },
          { id: "Source", type: "Source" },
        ],
        remove: [],
      },
      properties: {
        add: [
          {
            element: "combinedParser",
            name: "type",
            value: {
              string: "JS",
              integer: null,
              entity: null,
              boolean: null,
              long: null,
            },
          },
          {
            element: "xsltFilter",
            name: "xsltNamePattern",
            value: {
              string: "DSD",
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
          { from: "Source", to: "combinedParser" },
          { from: "combinedParser", to: "xsltFilter" },
        ],
        remove: [],
      },
    },
    {
      elements: { add: [], remove: [] },
      properties: {
        add: [
          {
            element: "combinedParser",
            name: "type",
            value: {
              string: "JSON",
              integer: null,
              entity: null,
              boolean: null,
              long: null,
            },
          },
          {
            element: "xsltFilter",
            name: "xsltNamePattern",
            value: {
              string: "D",
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
        { id: "xsltFilter", type: "XSLTFilter" },
        { id: "combinedParser", type: "CombinedParser" },
        { id: "Source", type: "Source" },
      ],
      remove: [],
    },
    properties: {
      add: [
        {
          element: "xsltFilter",
          name: "xsltNamePattern",
          value: {
            string: "D",
            integer: null,
            entity: null,
            boolean: null,
            long: null,
          },
        },
        {
          element: "combinedParser",
          name: "type",
          value: {
            string: "JSON",
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
        { from: "combinedParser", to: "xsltFilter" },
        { from: "Source", to: "combinedParser" },
      ],
      remove: [],
    },
  },
} as PipelineDocumentType;

export const childNoPropertyParentNoProperty = {
  uuid: "childNoPropertyParentNoProperty",
  name: "This No Property, Parent No Property",
  type: "Pipeline",
  description:
    "Demonstrates properties with no property, parent has no property either",
  configStack: [
    {
      elements: {
        add: [
          { id: "combinedParser", type: "CombinedParser" },
          { id: "xsltFilter", type: "XSLTFilter" },
          { id: "Source", type: "Source" },
        ],
        remove: [],
      },
      properties: {
        add: [
          {
            element: "xsltFilter",
            name: "xsltNamePattern",
            value: {
              string: "DSD",
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
          { from: "Source", to: "combinedParser" },
          { from: "combinedParser", to: "xsltFilter" },
        ],
        remove: [],
      },
    },
    {
      elements: {
        add: [
          { id: "combinedParser", type: "CombinedParser" },
          { id: "xsltFilter", type: "XSLTFilter" },
          { id: "Source", type: "Source" },
        ],
        remove: [],
      },
      properties: {
        add: [
          {
            element: "xsltFilter",
            name: "xsltNamePattern",
            value: {
              string: "DSD",
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
          { from: "Source", to: "combinedParser" },
          { from: "combinedParser", to: "xsltFilter" },
        ],
        remove: [],
      },
    },
    {
      elements: { add: [], remove: [] },
      properties: {
        add: [
          {
            element: "combinedParser",
            name: "type",
            value: {
              string: "JSON",
              integer: null,
              entity: null,
              boolean: null,
              long: null,
            },
          },
          {
            element: "xsltFilter",
            name: "xsltNamePattern",
            value: {
              string: "D",
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
        { id: "xsltFilter", type: "XSLTFilter" },
        { id: "combinedParser", type: "CombinedParser" },
        { id: "Source", type: "Source" },
      ],
      remove: [],
    },
    properties: {
      add: [
        {
          element: "xsltFilter",
          name: "xsltNamePattern",
          value: {
            string: "D",
            integer: null,
            entity: null,
            boolean: null,
            long: null,
          },
        },
        {
          element: "combinedParser",
          name: "type",
          value: {
            string: "JSON",
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
        { from: "combinedParser", to: "xsltFilter" },
        { from: "Source", to: "combinedParser" },
      ],
      remove: [],
    },
  },
} as PipelineDocumentType;

export const childNoPropertyParentWithProperty = {
  uuid: "childNoPropertyParentWithProperty",
  name: "This No Property, Parent Has Property",
  type: "Pipeline",
  description:
    "Demonstrates properties with no property, parent has the property either",
  configStack: [
    {
      elements: {
        add: [
          { id: "combinedParser", type: "CombinedParser" },
          { id: "xsltFilter", type: "XSLTFilter" },
          { id: "Source", type: "Source" },
        ],
        remove: [],
      },
      properties: {
        add: [
          {
            element: "xsltFilter",
            name: "xsltNamePattern",
            value: {
              string: "DSD",
              integer: null,
              entity: null,
              boolean: null,
              long: null,
            },
          },
          {
            element: "xsltFilter",
            name: "property1",
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
          { from: "Source", to: "combinedParser" },
          { from: "combinedParser", to: "xsltFilter" },
        ],
        remove: [],
      },
    },
    {
      elements: {
        add: [
          { id: "combinedParser", type: "CombinedParser" },
          { id: "xsltFilter", type: "XSLTFilter" },
          { id: "Source", type: "Source" },
        ],
        remove: [],
      },
      properties: {
        add: [
          {
            element: "xsltFilter",
            name: "xsltNamePattern",
            value: {
              string: "DSD",
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
          { from: "Source", to: "combinedParser" },
          { from: "combinedParser", to: "xsltFilter" },
        ],
        remove: [],
      },
    },
    {
      elements: { add: [], remove: [] },
      properties: {
        add: [
          {
            element: "combinedParser",
            name: "type",
            value: {
              string: "JSON",
              integer: null,
              entity: null,
              boolean: null,
              long: null,
            },
          },
          {
            element: "xsltFilter",
            name: "xsltNamePattern",
            value: {
              string: "D",
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
        { id: "xsltFilter", type: "XSLTFilter" },
        { id: "combinedParser", type: "CombinedParser" },
        { id: "Source", type: "Source" },
      ],
      remove: [],
    },
    properties: {
      add: [
        {
          element: "xsltFilter",
          name: "xsltNamePattern",
          value: {
            string: "D",
            integer: null,
            entity: null,
            boolean: null,
            long: null,
          },
        },
        {
          element: "combinedParser",
          name: "type",
          value: {
            string: "JSON",
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
        { from: "combinedParser", to: "xsltFilter" },
        { from: "Source", to: "combinedParser" },
      ],
      remove: [],
    },
  },
} as PipelineDocumentType;

export const childWithPropertyParentNoProperty = {
  uuid: "childWithPropertyParentNoProperty",
  name: "This With Property, Parent No Property",
  type: "Pipeline",
  description:
    "Demonstrates properties with a property, parent has no property either",
  configStack: [
    {
      elements: {
        add: [
          { id: "combinedParser", type: "CombinedParser" },
          { id: "xsltFilter", type: "XSLTFilter" },
          { id: "Source", type: "Source" },
        ],
        remove: [],
      },
      properties: {
        add: [
          {
            element: "xsltFilter",
            name: "xsltNamePattern",
            value: {
              string: "DSD",
              integer: null,
              entity: null,
              boolean: null,
              long: null,
            },
          },
          {
            element: "xsltFilter",
            name: "property2",
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
          { from: "Source", to: "combinedParser" },
          { from: "combinedParser", to: "xsltFilter" },
        ],
        remove: [],
      },
    },
    {
      elements: {
        add: [
          { id: "combinedParser", type: "CombinedParser" },
          { id: "xsltFilter", type: "XSLTFilter" },
          { id: "Source", type: "Source" },
        ],
        remove: [],
      },
      properties: {
        add: [
          {
            element: "xsltFilter",
            name: "xsltNamePattern",
            value: {
              string: "DSD",
              integer: null,
              entity: null,
              boolean: null,
              long: null,
            },
          },
          {
            element: "xsltFilter",
            name: "property1",
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
          { from: "Source", to: "combinedParser" },
          { from: "combinedParser", to: "xsltFilter" },
        ],
        remove: [],
      },
    },
    {
      elements: { add: [], remove: [] },
      properties: {
        add: [
          {
            element: "combinedParser",
            name: "type",
            value: {
              string: "JSON",
              integer: null,
              entity: null,
              boolean: null,
              long: null,
            },
          },
          {
            element: "xsltFilter",
            name: "xsltNamePattern",
            value: {
              string: "D",
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
        { id: "xsltFilter", type: "XSLTFilter" },
        { id: "combinedParser", type: "CombinedParser" },
        { id: "Source", type: "Source" },
      ],
      remove: [],
    },
    properties: {
      add: [
        {
          element: "xsltFilter",
          name: "xsltNamePattern",
          value: {
            string: "D",
            integer: null,
            entity: null,
            boolean: null,
            long: null,
          },
        },
        {
          element: "combinedParser",
          name: "type",
          value: {
            string: "JSON",
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
        { from: "combinedParser", to: "xsltFilter" },
        { from: "Source", to: "combinedParser" },
      ],
      remove: [],
    },
  },
} as PipelineDocumentType;

export const childWithPropertyParentWithProperty = {
  uuid: "childWithPropertyParentWithProperty",
  name: "This With Property, Parent With Property",
  type: "Pipeline",
  description:
    "Demonstrates properties with a property, parent also has property",
  configStack: [
    {
      elements: {
        add: [
          { id: "combinedParser", type: "CombinedParser" },
          { id: "xsltFilter", type: "XSLTFilter" },
          { id: "Source", type: "Source" },
        ],
        remove: [],
      },
      properties: {
        add: [
          {
            element: "xsltFilter",
            name: "xsltNamePattern",
            value: {
              string: "DSD123",
              integer: null,
              entity: null,
              boolean: null,
              long: null,
            },
          },
          {
            element: "xsltFilter",
            name: "property2",
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
          { from: "Source", to: "combinedParser" },
          { from: "combinedParser", to: "xsltFilter" },
        ],
        remove: [],
      },
    },
    {
      elements: {
        add: [
          { id: "combinedParser", type: "CombinedParser" },
          { id: "xsltFilter", type: "XSLTFilter" },
          { id: "Source", type: "Source" },
        ],
        remove: [],
      },
      properties: {
        add: [
          {
            element: "xsltFilter",
            name: "xsltNamePattern",
            value: {
              string: "DSD",
              integer: null,
              entity: null,
              boolean: null,
              long: null,
            },
          },
          {
            element: "xsltFilter",
            name: "property1",
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
          { from: "Source", to: "combinedParser" },
          { from: "combinedParser", to: "xsltFilter" },
        ],
        remove: [],
      },
    },
    {
      elements: { add: [], remove: [] },
      properties: {
        add: [
          {
            element: "combinedParser",
            name: "type",
            value: {
              string: "JSON",
              integer: null,
              entity: null,
              boolean: null,
              long: null,
            },
          },
          {
            element: "xsltFilter",
            name: "xsltNamePattern",
            value: {
              string: "D",
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
        { id: "xsltFilter", type: "XSLTFilter" },
        { id: "combinedParser", type: "CombinedParser" },
        { id: "Source", type: "Source" },
      ],
      remove: [],
    },
    properties: {
      add: [
        {
          element: "xsltFilter",
          name: "xsltNamePattern",
          value: {
            string: "D",
            integer: null,
            entity: null,
            boolean: null,
            long: null,
          },
        },
        {
          element: "combinedParser",
          name: "type",
          value: {
            string: "JSON",
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
        { from: "combinedParser", to: "xsltFilter" },
        { from: "Source", to: "combinedParser" },
      ],
      remove: [],
    },
  },
} as PipelineDocumentType;

export const childWithRemoveForItsParentsAdd = {
  uuid: "childWithRemoveForItsParentsAdd",
  name: "This Removing Property from parent",
  type: "Pipeline",
  description: "Demonstrates property removal from a parent",
  configStack: [
    {
      elements: {
        add: [
          { id: "combinedParser", type: "CombinedParser" },
          { id: "xsltFilter", type: "XSLTFilter" },
          { id: "Source", type: "Source" },
        ],
        remove: [],
      },
      properties: {
        add: [
          {
            element: "xsltFilter",
            name: "xsltNamePattern",
            value: {
              string: "DSD123",
              integer: null,
              entity: null,
              boolean: null,
              long: null,
            },
          },
          {
            element: "xsltFilter",
            name: "property2",
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
          { from: "Source", to: "combinedParser" },
          { from: "combinedParser", to: "xsltFilter" },
        ],
        remove: [],
      },
    },
    {
      elements: {
        add: [
          { id: "combinedParser", type: "CombinedParser" },
          { id: "xsltFilter", type: "XSLTFilter" },
          { id: "Source", type: "Source" },
        ],
        remove: [],
      },
      properties: {
        add: [
          {
            element: "xsltFilter",
            name: "xsltNamePattern",
            value: {
              string: "DSD",
              integer: null,
              entity: null,
              boolean: null,
              long: null,
            },
          },
          {
            element: "xsltFilter",
            name: "property1",
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
            element: "xsltFilter",
            name: "property2",
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
          { from: "Source", to: "combinedParser" },
          { from: "combinedParser", to: "xsltFilter" },
        ],
        remove: [],
      },
    },
    {
      elements: { add: [], remove: [] },
      properties: {
        add: [
          {
            element: "combinedParser",
            name: "type",
            value: {
              string: "JSON",
              integer: null,
              entity: null,
              boolean: null,
              long: null,
            },
          },
          {
            element: "xsltFilter",
            name: "xsltNamePattern",
            value: {
              string: "D",
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
        { id: "xsltFilter", type: "XSLTFilter" },
        { id: "combinedParser", type: "CombinedParser" },
        { id: "Source", type: "Source" },
      ],
      remove: [],
    },
    properties: {
      add: [
        {
          element: "xsltFilter",
          name: "xsltNamePattern",
          value: {
            string: "D",
            integer: null,
            entity: null,
            boolean: null,
            long: null,
          },
        },
        {
          element: "combinedParser",
          name: "type",
          value: {
            string: "JSON",
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
        { from: "combinedParser", to: "xsltFilter" },
        { from: "Source", to: "combinedParser" },
      ],
      remove: [],
    },
  },
} as PipelineDocumentType;

export const emptyChildParentWithProperty = {
  uuid: "emptyChildParentWithProperty",
  name: "Empty Child, Parent With Property",
  type: "Pipeline",
  description: "Demonstrates properties",
  configStack: [
    {
      elements: {
        add: [
          { id: "combinedParser", type: "CombinedParser" },
          { id: "xsltFilter", type: "XSLTFilter" },
          { id: "Source", type: "Source" },
        ],
        remove: [],
      },
      properties: {
        add: [
          {
            element: "combinedParser",
            name: "type",
            value: {
              string: "JS",
              integer: null,
              entity: null,
              boolean: null,
              long: null,
            },
          },
          {
            element: "xsltFilter",
            name: "xsltNamePattern",
            value: {
              string: "DSD",
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
          { from: "Source", to: "combinedParser" },
          { from: "combinedParser", to: "xsltFilter" },
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
        { id: "xsltFilter", type: "XSLTFilter" },
        { id: "combinedParser", type: "CombinedParser" },
        { id: "Source", type: "Source" },
      ],
      remove: [],
    },
    properties: {
      add: [
        {
          element: "xsltFilter",
          name: "xsltNamePattern",
          value: {
            string: "D",
            integer: null,
            entity: null,
            boolean: null,
            long: null,
          },
        },
        {
          element: "combinedParser",
          name: "type",
          value: {
            string: "JSON",
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
        { from: "combinedParser", to: "xsltFilter" },
        { from: "Source", to: "combinedParser" },
      ],
      remove: [],
    },
  },
} as PipelineDocumentType;
