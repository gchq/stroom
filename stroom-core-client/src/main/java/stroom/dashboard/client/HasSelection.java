package stroom.dashboard.client;

import stroom.datasource.api.v2.QueryField;

import java.util.List;
import java.util.Map;

public interface HasSelection {

    List<QueryField> getFields();

    List<Map<String, String>> getSelection();
}
