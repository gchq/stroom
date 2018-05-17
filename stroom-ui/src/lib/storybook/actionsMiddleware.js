
import { action } from '@storybook/addon-actions';

const sbActions = {};

function getAction(reduxAction) {
    if (!!sbActions[reduxAction.type]) {
        return sbActions[reduxAction.type];
    } else {
        let newAction = action(reduxAction.type);
        sbActions[reduxAction.type] = newAction;
        return newAction;
    }
}

function storybookMiddleware({ getState }) {
    return next => action => {
        let prevState = getState();

        // Call the next dispatch method in the middleware chain.
        const returnValue = next(action)

        let nextState = getState();

        let sbActionHandler = getAction(action);
        
        sbActionHandler({
            prevState,
            payload : action.payload, // assuming 'redux actions'
            nextState
        });

        // This will likely be the action itself, unless
        // a middleware further in chain changed it.
        return returnValue
    }
}

export default storybookMiddleware;