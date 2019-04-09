package stroom.activity.impl;

import stroom.activity.shared.Activity;
import stroom.activity.shared.FindActivityCriteria;
import stroom.util.shared.BaseResultList;
import stroom.util.shared.HasIntCrud;

public interface ActivityDao extends HasIntCrud<Activity> {
    BaseResultList<Activity> find(FindActivityCriteria criteria);
}
