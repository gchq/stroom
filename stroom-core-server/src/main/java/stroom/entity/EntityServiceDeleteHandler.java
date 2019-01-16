package stroom.entity;

import stroom.entity.shared.EntityServiceDeleteAction;
import stroom.security.Security;
import stroom.task.api.AbstractTaskHandler;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;


class EntityServiceDeleteHandler extends AbstractTaskHandler<EntityServiceDeleteAction, VoidResult> {
    private final GenericEntityService entityService;
    private final Security security;

    @Inject
    EntityServiceDeleteHandler(final GenericEntityService entityService,
                               final Security security) {
        this.entityService = entityService;
        this.security = security;
    }

    @Override
    public VoidResult exec(final EntityServiceDeleteAction action) {
        return security.secureResult(() -> {
            entityService.delete(action.getEntity());
            return VoidResult.INSTANCE;
        });
    }
}
