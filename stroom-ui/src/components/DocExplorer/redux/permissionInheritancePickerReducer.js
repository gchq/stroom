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
import { createActions, handleActions } from 'redux-actions';

export const actionCreators = createActions({
  PERMISSION_INHERITANCE_PICKED: (pickerId, permissionInheritance) => ({
    pickerId,
    permissionInheritance,
  }),
});

const defaultState = {};

export const reducer = handleActions(
  {
    PERMISSION_INHERITANCE_PICKED: (state, action) => ({
      ...state,
      [action.payload.pickerId]: action.payload.permissionInheritance,
    }),
  },
  defaultState,
);
