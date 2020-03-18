package stroom.index.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class IndexVolumeResultPage extends ResultPage<IndexVolume> {
    public IndexVolumeResultPage(final List<IndexVolume> values) {
        super(values);
    }

    @JsonCreator
    public IndexVolumeResultPage(@JsonProperty("values") final List<IndexVolume> values,
                                 @JsonProperty("pageResponse") final PageResponse pageResponse) {
        super(values, pageResponse);
    }
}
