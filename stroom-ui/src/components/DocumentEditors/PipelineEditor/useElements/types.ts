export interface ElementDefinition {
  type: string;
  category: string;
  roles: string[];
  icon: string;
}

export interface ElementDefinitionsByCategory {
  [category: string]: ElementDefinition[];
}
export interface ElementDefinitionsByType {
  [type: string]: ElementDefinition;
}

export interface ElementPropertyType {
  elementType: ElementDefinition;
  name: string;
  type: string;
  description: string;
  defaultValue: string;
  pipelineReference: boolean;
  docRefTypes: string[] | undefined;
  displayPriority: number;
}

export interface ElementPropertiesType {
  [propName: string]: ElementPropertyType;
}
export interface ElementPropertiesByElementIdType {
  [pipelineElementType: string]: ElementPropertiesType;
}
