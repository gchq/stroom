package stroom.query.common.v2;

import stroom.query.api.SearchTaskProgress;
import stroom.task.api.TerminateHandler;

public interface SearchProcess extends TerminateHandler {

    SearchTaskProgress getSearchTaskProgress();
}
