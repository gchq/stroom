package stroom.config.global.impl.db;

import stroom.config.global.api.ConfigProperty;
import stroom.config.global.api.FetchGlobalConfigAction;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.ResultList;
import stroom.task.api.AbstractTaskHandler;
import stroom.task.api.TaskHandlerBean;

import javax.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

@TaskHandlerBean(task = FetchGlobalConfigAction.class)
class FetchGlobalConfigHandler extends AbstractTaskHandler<FetchGlobalConfigAction, ResultList<ConfigProperty>> {
    private final GlobalConfigService globalConfigService;

    @Inject
    FetchGlobalConfigHandler(final GlobalConfigService globalConfigService) {
        this.globalConfigService = globalConfigService;
    }

    @Override
    public ResultList<ConfigProperty> exec(final FetchGlobalConfigAction task) {
        List<ConfigProperty> list = globalConfigService.list();

        if (task.getCriteria().getName() != null) {
            list = list.stream()
                    .filter(v -> task.getCriteria().getName().isMatch(v.getName()))
                    .peek(v -> {
                        if (v.isPassword()) {
                            v.setValue("********************");
                        }
                    })
                    .collect(Collectors.toList());
        }

        return BaseResultList.createPageLimitedList(list, task.getCriteria().obtainPageRequest());
    }
}
