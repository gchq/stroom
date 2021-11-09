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

    public String getQualifiedFilterInput() {
        return qualifiedFilterInput;
    }

    /**
     * Used for filter queries (maybe bounded).
     */
    public static <T> QuickFilterResultPage<T> createCriterialBasedList(final List<T> realList,
                                                                        final BaseCriteria baseCriteria,
                                                                        final String qualifiedFilterInput) {
        return new QuickFilterResultPage<>(
                realList,
                createPageResponse(realList, baseCriteria.getPageRequest(),
                        null),
                qualifiedFilterInput);
    }

    /**
     * Used for filter queries (maybe bounded).
     */
    public static <T> QuickFilterResultPage<T> createCriterialBasedList(final List<T> realList,
                                                                        final BaseCriteria baseCriteria,
                                                                        final Long totalSize,
                                                                        final String qualifiedFilterInput) {
        return new QuickFilterResultPage<>(
                realList,
                createPageResponse(realList, baseCriteria.getPageRequest(), totalSize),
                qualifiedFilterInput);
    }

}
