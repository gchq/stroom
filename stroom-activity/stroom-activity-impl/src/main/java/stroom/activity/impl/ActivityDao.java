package stroom.activity.impl;

import stroom.activity.api.FindActivityCriteria;
import stroom.activity.shared.Activity;
import stroom.util.shared.HasIntCrud;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

public interface ActivityDao extends HasIntCrud<Activity> {

    List<Activity> find(FindActivityCriteria criteria);

    List<Activity> find(
            final FindActivityCriteria criteria,
            final Function<Stream<Activity>, Stream<Activity>> streamFunction);
}
