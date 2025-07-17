package stroom.dropwizard.common.prometheus;

import java.util.Map;

public interface AppInfoProvider {

    Map<String, String> getAppInfo();

    Map<String, String> getNodeLabels();
}
