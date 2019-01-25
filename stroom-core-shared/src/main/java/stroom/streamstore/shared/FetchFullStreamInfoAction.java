package stroom.streamstore.shared;

import stroom.task.shared.Action;
import stroom.data.meta.shared.Meta;

public class FetchFullStreamInfoAction extends Action<FullStreamInfoResult> {
    private static final long serialVersionUID = -3560107233301674555L;

    private Meta stream;

    public FetchFullStreamInfoAction() {
    }

    public FetchFullStreamInfoAction(final Meta stream) {
        this.stream = stream;
    }

    public Meta getStream() {
        return stream;
    }

    @Override
    public String getTaskName() {
        return "Fetch Full Stream Info Action";
    }
}
