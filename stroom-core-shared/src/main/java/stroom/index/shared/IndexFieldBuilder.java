package stroom.index.shared;

import stroom.datasource.api.v2.FieldType;

public class IndexFieldBuilder {

    private String name;
    private FieldType type = FieldType.TEXT;
    private AnalyzerType analyzerType = AnalyzerType.KEYWORD;
    private boolean indexed = true;
    private boolean stored;
    private boolean termPositions;
    private boolean caseSensitive;

    public IndexFieldBuilder() {
    }

    public IndexFieldBuilder(final IndexField indexField) {
        this.name = indexField.name;
        this.type = indexField.type;
        this.analyzerType = indexField.analyzerType;
        this.indexed = indexField.indexed;
        this.stored = indexField.stored;
        this.termPositions = indexField.termPositions;
        this.caseSensitive = indexField.caseSensitive;
    }

    public IndexFieldBuilder name(final String name) {
        this.name = name;
        return this;
    }

    public IndexFieldBuilder type(final FieldType type) {
        this.type = type;
        return this;
    }

    public IndexFieldBuilder analyzerType(final AnalyzerType analyzerType) {
        this.analyzerType = analyzerType;
        return this;
    }

    public IndexFieldBuilder indexed(final boolean indexed) {
        this.indexed = indexed;
        return this;
    }

    public IndexFieldBuilder stored(final boolean stored) {
        this.stored = stored;
        return this;
    }

    public IndexFieldBuilder termPositions(final boolean termPositions) {
        this.termPositions = termPositions;
        return this;
    }

    public IndexFieldBuilder caseSensitive(final boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
        return this;
    }

    public IndexField build() {
        return new IndexField(
                name,
                type,
                analyzerType,
                indexed,
                stored,
                termPositions,
                caseSensitive);
    }
}