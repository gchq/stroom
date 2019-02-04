package stroom.streamstore.shared;

import stroom.task.shared.Action;
import stroom.meta.shared.Meta;

public class FetchFullMetaInfoAction extends Action<FullMetaInfoResult> {
    private static final long serialVersionUID = -3560107233301674555L;

    private Meta meta;

    public FetchFullMetaInfoAction() {
    }

    public FetchFullMetaInfoAction(final Meta meta) {
        this.meta = meta;
    }

    public Meta getMeta() {
        return meta;
    }

    @Override
    public String getTaskName() {
        return "Fetch Full Meta Info Action";
    }
}
