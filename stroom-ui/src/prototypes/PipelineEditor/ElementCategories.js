export class ElementCategory {
    constructor(displayName, index) {
        this.displayName = displayName;
        this.index = index;
    }
}

export const ElementCategories = {
    INTERNAL : new ElementCategory("Internal", -1),
    READER : new ElementCategory("Reader", 0),
    PARSER : new ElementCategory("Parser", 1),
    FILTER : new ElementCategory("Filter", 2),
    WRITER : new ElementCategory("Writer", 3),
    DESTINATION : new ElementCategory("Destination", 4)
}