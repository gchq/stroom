package stroom.pipeline.filter;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.inject.Singleton;

@Singleton
public class XsltConfig {
    private static final int DEFAULT_MAX_ELEMENTS = 1000000;

    private int maxElements = DEFAULT_MAX_ELEMENTS;

    @JsonPropertyDescription("The maximum number of elements that the XSLT filter will expect to receive before it errors. This protects Stroom from ruinning out of memory in cases where an appropriate XML splitter has not been used in a pipeline.")
    public int getMaxElements() {
        return maxElements;
    }

    public void setMaxElements(final int maxElements) {
        this.maxElements = maxElements;
    }
}
