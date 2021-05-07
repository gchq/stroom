package stroom.dashboard.client;

import stroom.datasource.api.v2.AbstractField;

import java.util.List;
import java.util.Map;

public interface HasSelection {

    List<AbstractField> getFields();

    List<Map<String, String>> getSelection();
}
