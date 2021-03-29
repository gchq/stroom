import { useElements } from "../useElements";
import { ElementDefinition, ElementPropertyType } from "../useElements/types";

interface UseElement {
  definition?: ElementDefinition;
  properties: ElementPropertyType[];
}

export const useElement = (type?: string): UseElement => {
  const { elementDefinitions, elementProperties } = useElements();

  return {
    definition: elementDefinitions.find((e) => e.type === type),
    properties: Object.values(
      elementProperties[type] || {},
    ).sort((a: ElementPropertyType, b: ElementPropertyType) =>
      a.displayPriority > b.displayPriority ? 1 : -1,
    ),
  };
};
