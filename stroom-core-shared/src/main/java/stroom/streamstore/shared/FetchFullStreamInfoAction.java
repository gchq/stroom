package stroom.streamstore.shared;

import stroom.entity.shared.Action;
import stroom.streamstore.meta.api.Stream;

public class FetchFullStreamInfoAction extends Action<FullStreamInfoResult> {
    private static final long serialVersionUID = -3560107233301674555L;

    private Stream stream;

    public FetchFullStreamInfoAction() {
    }

    public FetchFullStreamInfoAction(final Stream stream) {
        this.stream = stream;
    }

    public Stream getStream() {
        return stream;
    }

    @Override
    public String getTaskName() {
        return "Fetch Full Stream Info Action";
    }
}
