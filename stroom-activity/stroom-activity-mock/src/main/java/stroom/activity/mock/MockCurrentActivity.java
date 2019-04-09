package stroom.activity.mock;

import stroom.activity.shared.Activity;
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