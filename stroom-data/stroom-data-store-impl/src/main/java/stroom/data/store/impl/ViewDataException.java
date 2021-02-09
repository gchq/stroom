package stroom.data.store.impl;

import stroom.pipeline.shared.SourceLocation;

public class ViewDataException extends RuntimeException{
    private final SourceLocation sourceLocation;

    public ViewDataException(final SourceLocation sourceLocation, final String message) {
        super(message);
        this.sourceLocation = sourceLocation;
    }

    public SourceLocation getSourceLocation() {
        return sourceLocation;
    }
}
