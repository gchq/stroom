package stroom.activity.impl;

import stroom.activity.api.FindActivityCriteria;
import stroom.activity.shared.Activity;
import stroom.util.shared.HasIntCrud;
import stroom.util.shared.ResultPage;

public interface ActivityDao extends HasIntCrud<Activity> {
    ResultPage<Activity> find(FindActivityCriteria criteria);
}
