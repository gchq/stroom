package stroom.activity.impl;

import stroom.activity.api.FindActivityCriteria;
import stroom.activity.shared.Activity;
import stroom.util.shared.HasIntCrud;

import java.util.List;

public interface ActivityDao extends HasIntCrud<Activity> {
    List<Activity> find(FindActivityCriteria criteria);
}
