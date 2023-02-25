package stroom.query.common.v2;

import stroom.query.api.v2.SearchTaskProgress;
import stroom.task.api.TerminateHandler;

public interface SearchProcess extends TerminateHandler {

    SearchTaskProgress getSearchTaskProgress();
}
