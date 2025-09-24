package stroom.pathways.shared;

import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import java.util.List;

public class TracesResultPage extends ResultPage<TraceRoot> {

    public TracesResultPage(final List<TraceRoot> values,
                            final PageResponse pageResponse) {
        super(values, pageResponse);
    }
}
