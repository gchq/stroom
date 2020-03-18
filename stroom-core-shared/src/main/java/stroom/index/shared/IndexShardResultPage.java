package stroom.index.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class IndexShardResultPage extends ResultPage<IndexShard> {
    public IndexShardResultPage(final List<IndexShard> values) {
        super(values);
    }

    @JsonCreator
    public IndexShardResultPage(@JsonProperty("values") final List<IndexShard> values,
                                @JsonProperty("pageResponse") final PageResponse pageResponse) {
        super(values, pageResponse);
    }
}
