package stroom.streamstore.shared;

import stroom.entity.shared.Action;
import stroom.data.meta.api.Data;

public class FetchFullStreamInfoAction extends Action<FullStreamInfoResult> {
    private static final long serialVersionUID = -3560107233301674555L;

    private Data stream;

    public FetchFullStreamInfoAction() {
    }

    public FetchFullStreamInfoAction(final Data stream) {
        this.stream = stream;
    }

    public Data getStream() {
        return stream;
    }

    @Override
    public String getTaskName() {
        return "Fetch Full Stream Info Action";
    }
}
