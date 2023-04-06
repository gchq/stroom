package stroom.query.common.v2;

import stroom.event.logging.api.EventActionDecorator;

import event.logging.ProcessAction;
import event.logging.ProcessEventAction;

public class TerminateDecorator implements EventActionDecorator<ProcessEventAction> {

    @Override
    public ProcessEventAction decorate(final ProcessEventAction eventAction) {
        return eventAction.newCopyBuilder()
                .withAction(ProcessAction.TERMINATE)
                .build();
    }
}