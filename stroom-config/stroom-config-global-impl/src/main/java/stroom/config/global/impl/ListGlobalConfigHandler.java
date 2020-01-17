package stroom.config.global.impl;

import stroom.config.global.shared.ConfigProperty;
import stroom.config.global.shared.ListGlobalConfigAction;
import stroom.task.api.AbstractTaskHandler;
import stroom.util.shared.BaseResultList;
import stroom.util.shared.ResultList;

import javax.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;


class ListGlobalConfigHandler extends AbstractTaskHandler<ListGlobalConfigAction, ResultList<ConfigProperty>> {
    private final GlobalConfigService globalConfigService;

    @Inject
    ListGlobalConfigHandler(final GlobalConfigService globalConfigService) {
        this.globalConfigService = globalConfigService;
    }

    @Override
    public ResultList<ConfigProperty> exec(final ListGlobalConfigAction task) {
        List<ConfigProperty> list = globalConfigService.list();

        if (task.getCriteria().getName() != null) {
            list = list.stream()
                    .filter(configProperty ->
                        task.getCriteria().getName().isMatch(configProperty.getName().toString()))
//                    .peek(v -> {
//                        if (v.isPassword()) {
//                            v.setValue("********************");
//                        }
//                    })
                    .collect(Collectors.toList());
        }

        return BaseResultList.createPageLimitedList(list, task.getCriteria().obtainPageRequest());
    }
}
