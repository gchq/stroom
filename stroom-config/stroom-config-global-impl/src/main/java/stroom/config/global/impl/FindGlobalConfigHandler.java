package stroom.config.global.impl;

import stroom.config.global.shared.ConfigProperty;
import stroom.config.global.shared.FindGlobalConfigAction;
import stroom.task.api.AbstractTaskHandler;
import stroom.util.shared.BaseResultList;
import stroom.util.shared.ResultList;

import javax.inject.Inject;

public class FindGlobalConfigHandler extends AbstractTaskHandler<FindGlobalConfigAction, ResultList<ConfigProperty>> {

    private final GlobalConfigService globalConfigService;

    @Inject
    FindGlobalConfigHandler(final GlobalConfigService globalConfigService) {
        this.globalConfigService = globalConfigService;
    }

    @Override
    public ResultList<ConfigProperty> exec(final FindGlobalConfigAction task) {
        return BaseResultList.createPageLimitedList(
                globalConfigService.list(task.getCriteria()), task.getCriteria().obtainPageRequest());
    }
}
