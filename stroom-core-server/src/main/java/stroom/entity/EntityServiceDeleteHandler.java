package stroom.entity;

import stroom.entity.shared.EntityServiceDeleteAction;
import stroom.task.AbstractTaskHandler;
import stroom.task.TaskHandlerBean;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;

@TaskHandlerBean(task = EntityServiceDeleteAction.class)
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
