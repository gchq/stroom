package stroom.index.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class IndexVolumeGroupResultPage extends ResultPage<IndexVolumeGroup> {
    public IndexVolumeGroupResultPage(final List<IndexVolumeGroup> values) {
        super(values);
    }

    @JsonCreator
    public IndexVolumeGroupResultPage(@JsonProperty("values") final List<IndexVolumeGroup> values,
                                      @JsonProperty("pageResponse") final PageResponse pageResponse) {
        super(values, pageResponse);
    }
}
