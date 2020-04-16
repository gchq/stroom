export class ElementCategoryType {
  displayName: string;
  index: number;

  constructor(displayName: string, index: number) {
    this.displayName = displayName;
    this.index = index;
  }
}

export const ElementCategories = {
  INTERNAL: new ElementCategoryType("Internal", -1),
  READER: new ElementCategoryType("Reader", 0),
  PARSER: new ElementCategoryType("Parser", 1),
  FILTER: new ElementCategoryType("Filter", 2),
  WRITER: new ElementCategoryType("Writer", 3),
  DESTINATION: new ElementCategoryType("Destination", 4),
};
