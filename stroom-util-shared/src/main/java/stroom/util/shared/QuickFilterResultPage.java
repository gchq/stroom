package stroom.util.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class QuickFilterResultPage<T> extends ResultPage<T> {

    @JsonProperty
    private final String qualifiedFilterInput;

    @JsonCreator
    public QuickFilterResultPage(@JsonProperty("values") final List<T> values,
                                 @JsonProperty("pageResponse") final PageResponse pageResponse,
                                 @JsonProperty("qualifiedFilterInput") final String qualifiedFilterInput) {
        super(values, pageResponse);
        this.qualifiedFilterInput = qualifiedFilterInput;
    }

    public QuickFilterResultPage(final List<T> values, final String qualifiedFilterInput) {
        super(values);
        this.qualifiedFilterInput = qualifiedFilterInput;
    }

    public QuickFilterResultPage(final ResultPage<T> resultPage, final String qualifiedFilterInput) {
        super(resultPage.getValues(), resultPage.getPageResponse());
        this.qualifiedFilterInput = qualifiedFilterInput;
    }

    public String getQualifiedFilterInput() {
        return qualifiedFilterInput;
    }

    /**
     * Used for filter queries (maybe bounded).
     */
    public static <T> QuickFilterResultPage<T> createCriterialBasedList(final List<T> realList,
                                                                        final BaseCriteria baseCriteria,
                                                                        final String qualifiedFilterInput) {
        final ResultPage<T> resultPage = ResultPage.createCriterialBasedList(realList, baseCriteria);
        return new QuickFilterResultPage<>(resultPage.getValues(), resultPage.getPageResponse(), qualifiedFilterInput);
    }

    /**
     * Used for filter queries (maybe bounded).
     */
    public static <T> QuickFilterResultPage<T> createCriterialBasedList(final List<T> realList,
                                                                        final BaseCriteria baseCriteria,
                                                                        final long totalSize,
                                                                        final String qualifiedFilterInput) {
        final ResultPage<T> resultPage = ResultPage.createCriterialBasedList(realList, baseCriteria, totalSize);
        return new QuickFilterResultPage<>(resultPage.getValues(), resultPage.getPageResponse(), qualifiedFilterInput);
    }
}
