package stroom.activity.mock;

import stroom.activity.api.CurrentActivity;
import stroom.activity.shared.Activity;

public class MockCurrentActivity implements CurrentActivity {
    @Override
    public Activity getActivity() {
        return null;
    }

    @Override
    public void setActivity(final Activity activity) {
    }
}