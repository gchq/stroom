package stroom.entity.server;

import org.springframework.context.annotation.Scope;
import stroom.entity.shared.EntityServiceDeleteAction;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.shared.VoidResult;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;

@TaskHandlerBean(task = EntityServiceDeleteAction.class)
@Scope(value = StroomScope.TASK)
class EntityServiceDeleteHandler extends AbstractTaskHandler<EntityServiceDeleteAction, VoidResult> {
    private final GenericEntityService entityService;

    @Inject
    EntityServiceDeleteHandler(final GenericEntityService entityService) {
        this.entityService = entityService;
    }

    @Override
    public VoidResult exec(final EntityServiceDeleteAction action) {
        entityService.delete(action.getEntity());
        return VoidResult.INSTANCE;
    }
}
