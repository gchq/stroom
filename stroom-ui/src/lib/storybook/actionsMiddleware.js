
/*
 * Copyright 2018 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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