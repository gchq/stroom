package stroom.activity.shared;

import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class ActivityResultPage extends ResultPage<Activity> {

    @JsonProperty
    private final String qualifiedFilterInput;

    @JsonCreator
    public ActivityResultPage(@JsonProperty("values") final List<Activity> values,
                              @JsonProperty("pageResponse") final PageResponse pageResponse,
                              @JsonProperty("qualifiedFilterInput") final String qualifiedFilterInput) {
        super(values, pageResponse);
        this.qualifiedFilterInput = qualifiedFilterInput;
    }

    public String getQualifiedFilterInput() {
        return qualifiedFilterInput;
    }

    public static ActivityResultPage create(final ResultPage<Activity> resultPage,
                                            final String qualifiedFilterInput) {
        return new ActivityResultPage(
                resultPage.getValues(),
                resultPage.getPageResponse(),
                qualifiedFilterInput);
    }
}
