import { ElementCategories } from '../ElementCategories';

export const testElementTypes = {
	"FileAppender": {
		"type": "FileAppender",
		"category": "DESTINATION",
		"roles": [
			"destination",
			"stepping",
			"target"
		],
		"icon": "file.svg"
	},
	"JSONWriter": {
		"type": "JSONWriter",
		"category": "WRITER",
		"roles": [
			"writer",
			"mutator",
			"stepping",
			"target"
		],
		"icon": "json.svg"
	},
	"XMLFragmentParser": {
		"type": "XMLFragmentParser",
		"category": "PARSER",
		"roles": [
			"parser",
			"hasCode",
			"simple",
			"hasTargets",
			"stepping",
			"mutator"
		],
		"icon": "xml.svg"
	},
	"RecordCountFilter": {
		"type": "RecordCountFilter",
		"category": "FILTER",
		"roles": [
			"hasTargets",
			"target"
		],
		"icon": "recordCount.svg"
	},
	"TextWriter": {
		"type": "TextWriter",
		"category": "WRITER",
		"roles": [
			"hasTargets",
			"writer",
			"mutator",
			"stepping",
			"target"
		],
		"icon": "text.svg"
	},
	"DSParser": {
		"type": "DSParser",
		"category": "PARSER",
		"roles": [
			"parser",
			"hasCode",
			"simple",
			"hasTargets",
			"stepping",
			"mutator"
		],
		"icon": "text.svg"
	},
	"CombinedParser": {
		"type": "CombinedParser",
		"category": "PARSER",
		"roles": [
			"parser",
			"hasCode",
			"simple",
			"hasTargets",
			"stepping",
			"mutator"
		],
		"icon": "text.svg"
	},
	"XMLWriter": {
		"type": "XMLWriter",
		"category": "WRITER",
		"roles": [
			"hasTargets",
			"writer",
			"mutator",
			"stepping",
			"target"
		],
		"icon": "xml.svg"
	},
	"Source": {
		"type": "Source",
		"category": "INTERNAL",
		"roles": [
			"simple",
			"source",
			"hasTargets"
		],
		"icon": "stream.svg"
	},
	"SchemaFilter": {
		"type": "SchemaFilter",
		"category": "FILTER",
		"roles": [
			"validator",
			"hasTargets",
			"stepping",
			"target"
		],
		"icon": "xsd.svg"
	},
	"SplitFilter": {
		"type": "SplitFilter",
		"category": "FILTER",
		"roles": [
			"hasTargets",
			"target"
		],
		"icon": "split.svg"
	},
	"XSLTFilter": {
		"type": "XSLTFilter",
		"category": "FILTER",
		"roles": [
			"hasCode",
			"simple",
			"hasTargets",
			"stepping",
			"mutator",
			"target"
		],
		"icon": "xslt.svg"
	},
	"JSONParser": {
		"type": "JSONParser",
		"category": "PARSER",
		"roles": [
			"parser",
			"simple",
			"hasTargets",
			"stepping",
			"mutator"
		],
		"icon": "json.svg"
	},
	"StreamAppender": {
		"type": "StreamAppender",
		"category": "DESTINATION",
		"roles": [
			"destination",
			"stepping",
			"target"
		],
		"icon": "stream.svg"
	},
	"RecordOutputFilter": {
		"type": "RecordOutputFilter",
		"category": "FILTER",
		"roles": [
			"hasTargets",
			"target"
		],
		"icon": "recordOutput.svg"
	},
	"XMLParser": {
		"type": "XMLParser",
		"category": "PARSER",
		"roles": [
			"parser",
			"simple",
			"hasTargets",
			"stepping",
			"mutator"
		],
		"icon": "xml.svg"
	}
}

export const testElementProperties = {
	"JSONWriter": {
		"indentOutput": {
			"elementType": {
				"type": "JSONWriter",
				"category": "WRITER",
				"roles": [
					"writer",
					"mutator",
					"stepping",
					"target"
				],
				"icon": "json.svg"
			},
			"name": "indentOutput",
			"type": "boolean",
			"description": "Should output JSON be indented and include new lines (pretty printed)?",
			"defaultValue": "false",
			"pipelineReference": false,
			"docRefTypes": null
		},
		"encoding": {
			"elementType": {
				"type": "JSONWriter",
				"category": "WRITER",
				"roles": [
					"writer",
					"mutator",
					"stepping",
					"target"
				],
				"icon": "json.svg"
			},
			"name": "encoding",
			"type": "String",
			"description": "The output character encoding to use.",
			"defaultValue": "UTF-8",
			"pipelineReference": false,
			"docRefTypes": null
		}
	},
	"FileAppender": {
		"outputPaths": {
			"elementType": {
				"type": "FileAppender",
				"category": "DESTINATION",
				"roles": [
					"destination",
					"stepping",
					"target"
				],
				"icon": "file.svg"
			},
			"name": "outputPaths",
			"type": "String",
			"description": "One or more destination paths for output files separated with commas. Replacement variables can be used in path strings such as ${feed}.",
			"defaultValue": "",
			"pipelineReference": false,
			"docRefTypes": null
		}
	},
	"XMLFragmentParser": {
		"textConverter": {
			"elementType": {
				"type": "XMLFragmentParser",
				"category": "PARSER",
				"roles": [
					"parser",
					"hasCode",
					"simple",
					"hasTargets",
					"stepping",
					"mutator"
				],
				"icon": "xml.svg"
			},
			"name": "textConverter",
			"type": "DocRef",
			"description": "The XML fragment wrapper that should be used to wrap the input XML.",
			"defaultValue": "",
			"pipelineReference": false,
			"docRefTypes": [
				"TextConverter"
			]
		}
	},
	"RecordCountFilter": {
		"countRead": {
			"elementType": {
				"type": "RecordCountFilter",
				"category": "FILTER",
				"roles": [
					"hasTargets",
					"target"
				],
				"icon": "recordCount.svg"
			},
			"name": "countRead",
			"type": "boolean",
			"description": "Is this filter counting records read or records written?",
			"defaultValue": "true",
			"pipelineReference": false,
			"docRefTypes": null
		}
	},
	"TextWriter": {
		"footer": {
			"elementType": {
				"type": "TextWriter",
				"category": "WRITER",
				"roles": [
					"hasTargets",
					"writer",
					"mutator",
					"stepping",
					"target"
				],
				"icon": "text.svg"
			},
			"name": "footer",
			"type": "String",
			"description": "Footer text that can be added to the output at the end.",
			"defaultValue": "",
			"pipelineReference": false,
			"docRefTypes": null
		},
		"header": {
			"elementType": {
				"type": "TextWriter",
				"category": "WRITER",
				"roles": [
					"hasTargets",
					"writer",
					"mutator",
					"stepping",
					"target"
				],
				"icon": "text.svg"
			},
			"name": "header",
			"type": "String",
			"description": "Header text that can be added to the output at the start.",
			"defaultValue": "",
			"pipelineReference": false,
			"docRefTypes": null
		},
		"encoding": {
			"elementType": {
				"type": "TextWriter",
				"category": "WRITER",
				"roles": [
					"hasTargets",
					"writer",
					"mutator",
					"stepping",
					"target"
				],
				"icon": "text.svg"
			},
			"name": "encoding",
			"type": "String",
			"description": "The output character encoding to use.",
			"defaultValue": "UTF-8",
			"pipelineReference": false,
			"docRefTypes": null
		}
	},
	"DSParser": {
		"textConverter": {
			"elementType": {
				"type": "DSParser",
				"category": "PARSER",
				"roles": [
					"parser",
					"hasCode",
					"simple",
					"hasTargets",
					"stepping",
					"mutator"
				],
				"icon": "text.svg"
			},
			"name": "textConverter",
			"type": "DocRef",
			"description": "The data splitter configuration that should be used to parse the input data.",
			"defaultValue": "",
			"pipelineReference": false,
			"docRefTypes": [
				"TextConverter"
			]
		}
	},
	"CombinedParser": {
		"textConverter": {
			"elementType": {
				"type": "CombinedParser",
				"category": "PARSER",
				"roles": [
					"parser",
					"hasCode",
					"simple",
					"hasTargets",
					"stepping",
					"mutator"
				],
				"icon": "text.svg"
			},
			"name": "textConverter",
			"type": "DocRef",
			"description": "The text converter configuration that should be used to parse the input data.",
			"defaultValue": "",
			"pipelineReference": false,
			"docRefTypes": [
				"TextConverter"
			]
		},
		"fixInvalidChars": {
			"elementType": {
				"type": "CombinedParser",
				"category": "PARSER",
				"roles": [
					"parser",
					"hasCode",
					"simple",
					"hasTargets",
					"stepping",
					"mutator"
				],
				"icon": "text.svg"
			},
			"name": "fixInvalidChars",
			"type": "boolean",
			"description": "Fix invalid XML characters from the input stream.",
			"defaultValue": "false",
			"pipelineReference": false,
			"docRefTypes": null
		},
		"type": {
			"elementType": {
				"type": "CombinedParser",
				"category": "PARSER",
				"roles": [
					"parser",
					"hasCode",
					"simple",
					"hasTargets",
					"stepping",
					"mutator"
				],
				"icon": "text.svg"
			},
			"name": "type",
			"type": "String",
			"description": "The parser type, e.g. 'JSON', 'XML', 'Data Splitter'.",
			"defaultValue": "",
			"pipelineReference": false,
			"docRefTypes": null
		}
	},
	"XMLWriter": {
		"indentOutput": {
			"elementType": {
				"type": "XMLWriter",
				"category": "WRITER",
				"roles": [
					"hasTargets",
					"writer",
					"mutator",
					"stepping",
					"target"
				],
				"icon": "xml.svg"
			},
			"name": "indentOutput",
			"type": "boolean",
			"description": "Should output XML be indented and include new lines (pretty printed)?",
			"defaultValue": "false",
			"pipelineReference": false,
			"docRefTypes": null
		},
		"encoding": {
			"elementType": {
				"type": "XMLWriter",
				"category": "WRITER",
				"roles": [
					"hasTargets",
					"writer",
					"mutator",
					"stepping",
					"target"
				],
				"icon": "xml.svg"
			},
			"name": "encoding",
			"type": "String",
			"description": "The output character encoding to use.",
			"defaultValue": "UTF-8",
			"pipelineReference": false,
			"docRefTypes": null
		}
	},
	"Source": {},
	"SplitFilter": {
		"splitDepth": {
			"elementType": {
				"type": "SplitFilter",
				"category": "FILTER",
				"roles": [
					"hasTargets",
					"target"
				],
				"icon": "split.svg"
			},
			"name": "splitDepth",
			"type": "int",
			"description": "The depth of XML elements to split at.",
			"defaultValue": "1",
			"pipelineReference": false,
			"docRefTypes": null
		},
		"splitCount": {
			"elementType": {
				"type": "SplitFilter",
				"category": "FILTER",
				"roles": [
					"hasTargets",
					"target"
				],
				"icon": "split.svg"
			},
			"name": "splitCount",
			"type": "int",
			"description": "The number of elements at the split depth to count before the XML is split.",
			"defaultValue": "10000",
			"pipelineReference": false,
			"docRefTypes": null
		}
	},
	"SchemaFilter": {
		"systemId": {
			"elementType": {
				"type": "SchemaFilter",
				"category": "FILTER",
				"roles": [
					"validator",
					"hasTargets",
					"stepping",
					"target"
				],
				"icon": "xsd.svg"
			},
			"name": "systemId",
			"type": "String",
			"description": "Limits the schemas that can be used to validate data to those with a matching system id.",
			"defaultValue": "",
			"pipelineReference": false,
			"docRefTypes": null
		},
		"schemaValidation": {
			"elementType": {
				"type": "SchemaFilter",
				"category": "FILTER",
				"roles": [
					"validator",
					"hasTargets",
					"stepping",
					"target"
				],
				"icon": "xsd.svg"
			},
			"name": "schemaValidation",
			"type": "boolean",
			"description": "Should schema validation be performed?",
			"defaultValue": "true",
			"pipelineReference": false,
			"docRefTypes": null
		},
		"namespaceURI": {
			"elementType": {
				"type": "SchemaFilter",
				"category": "FILTER",
				"roles": [
					"validator",
					"hasTargets",
					"stepping",
					"target"
				],
				"icon": "xsd.svg"
			},
			"name": "namespaceURI",
			"type": "String",
			"description": "Limits the schemas that can be used to validate data to those with a matching namespace URI.",
			"defaultValue": "",
			"pipelineReference": false,
			"docRefTypes": null
		},
		"schemaLanguage": {
			"elementType": {
				"type": "SchemaFilter",
				"category": "FILTER",
				"roles": [
					"validator",
					"hasTargets",
					"stepping",
					"target"
				],
				"icon": "xsd.svg"
			},
			"name": "schemaLanguage",
			"type": "String",
			"description": "The schema language that the schema is written in.",
			"defaultValue": "http://www.w3.org/2001/XMLSchema",
			"pipelineReference": false,
			"docRefTypes": null
		},
		"schemaGroup": {
			"elementType": {
				"type": "SchemaFilter",
				"category": "FILTER",
				"roles": [
					"validator",
					"hasTargets",
					"stepping",
					"target"
				],
				"icon": "xsd.svg"
			},
			"name": "schemaGroup",
			"type": "String",
			"description": "Limits the schemas that can be used to validate data to those with a matching schema group name.",
			"defaultValue": "",
			"pipelineReference": false,
			"docRefTypes": null
		}
	},
	"JSONParser": {},
	"XSLTFilter": {
		"suppressXSLTNotFoundWarnings": {
			"elementType": {
				"type": "XSLTFilter",
				"category": "FILTER",
				"roles": [
					"hasCode",
					"simple",
					"hasTargets",
					"stepping",
					"mutator",
					"target"
				],
				"icon": "xslt.svg"
			},
			"name": "suppressXSLTNotFoundWarnings",
			"type": "boolean",
			"description": "If XSLT cannot be found to match the name pattern suppress warnings.",
			"defaultValue": "false",
			"pipelineReference": false,
			"docRefTypes": null
		},
		"usePool": {
			"elementType": {
				"type": "XSLTFilter",
				"category": "FILTER",
				"roles": [
					"hasCode",
					"simple",
					"hasTargets",
					"stepping",
					"mutator",
					"target"
				],
				"icon": "xslt.svg"
			},
			"name": "usePool",
			"type": "boolean",
			"description": "Advanced: Choose whether or not you want to use cached XSLT templates to improve performance.",
			"defaultValue": "true",
			"pipelineReference": false,
			"docRefTypes": null
		},
		"pipelineReference": {
			"elementType": {
				"type": "XSLTFilter",
				"category": "FILTER",
				"roles": [
					"hasCode",
					"simple",
					"hasTargets",
					"stepping",
					"mutator",
					"target"
				],
				"icon": "xslt.svg"
			},
			"name": "pipelineReference",
			"type": "PipelineReference",
			"description": "A list of places to load reference data from if required.",
			"defaultValue": "",
			"pipelineReference": true,
			"docRefTypes": null
		},
		"xslt": {
			"elementType": {
				"type": "XSLTFilter",
				"category": "FILTER",
				"roles": [
					"hasCode",
					"simple",
					"hasTargets",
					"stepping",
					"mutator",
					"target"
				],
				"icon": "xslt.svg"
			},
			"name": "xslt",
			"type": "DocRef",
			"description": "The XSLT to use.",
			"defaultValue": "",
			"pipelineReference": false,
			"docRefTypes": [
				"XSLT"
			]
		},
		"xsltNamePattern": {
			"elementType": {
				"type": "XSLTFilter",
				"category": "FILTER",
				"roles": [
					"hasCode",
					"simple",
					"hasTargets",
					"stepping",
					"mutator",
					"target"
				],
				"icon": "xslt.svg"
			},
			"name": "xsltNamePattern",
			"type": "String",
			"description": "A name pattern to load XSLT dynamically.",
			"defaultValue": "",
			"pipelineReference": false,
			"docRefTypes": null
		}
	},
	"StreamAppender": {
		"feed": {
			"elementType": {
				"type": "StreamAppender",
				"category": "DESTINATION",
				"roles": [
					"destination",
					"stepping",
					"target"
				],
				"icon": "stream.svg"
			},
			"name": "feed",
			"type": "DocRef",
			"description": "The feed that output stream should be written to. If not specified the feed the input stream belongs to will be used.",
			"defaultValue": "",
			"pipelineReference": false,
			"docRefTypes": [
				"Feed"
			]
		},
		"segmentOutput": {
			"elementType": {
				"type": "StreamAppender",
				"category": "DESTINATION",
				"roles": [
					"destination",
					"stepping",
					"target"
				],
				"icon": "stream.svg"
			},
			"name": "segmentOutput",
			"type": "boolean",
			"description": "Should the output stream be marked with indexed segments to allow fast access to individual records?",
			"defaultValue": "true",
			"pipelineReference": false,
			"docRefTypes": null
		},
		"streamType": {
			"elementType": {
				"type": "StreamAppender",
				"category": "DESTINATION",
				"roles": [
					"destination",
					"stepping",
					"target"
				],
				"icon": "stream.svg"
			},
			"name": "streamType",
			"type": "String",
			"description": "The stream type that the output stream should be written as. This must be specified.",
			"defaultValue": "",
			"pipelineReference": false,
			"docRefTypes": null
		}
	},
	"XMLParser": {},
	"RecordOutputFilter": {}
}