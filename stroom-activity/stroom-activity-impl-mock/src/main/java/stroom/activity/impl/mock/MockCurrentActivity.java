package stroom.activity.impl.mock;

import stroom.activity.api.Activity;
import stroom.activity.api.CurrentActivity;

public class MockCurrentActivity implements CurrentActivity {
    @Override
    public Activity getActivity() {
        return null;
    }

    @Override
    public void setActivity(final Activity activity) {
    }
}
