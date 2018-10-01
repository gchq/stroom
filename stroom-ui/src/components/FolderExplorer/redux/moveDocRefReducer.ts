/*
 * Moveright 2018 Crown Moveright
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
import { Action } from "redux";

import {
  prepareReducerById,
  StateById,
  ActionId
} from "../../../lib/redux-actions-ts";
import { DOC_REFS_COPIED, DocRefsMovedAction } from "./documentTree";

const PREPARE_DOC_REF_MOVE = "PREPARE_DOC_REF_MOVE";
const COMPLETE_DOC_REF_MOVE = "COMPLETE_DOC_REF_MOVE";

export interface PrepareDocRefMoveAction
  extends ActionId,
    Action<"PREPARE_DOC_REF_MOVE"> {
  uuids: Array<string>;
  destinationUuid: string;
}

export interface CompleteDocRefMoveAction
  extends ActionId,
    Action<"COMPLETE_DOC_REF_MOVE"> {}

export const actionCreators = {
  prepareDocRefMove: (
    id: string,
    uuids: Array<string>,
    destinationUuid: string
  ): PrepareDocRefMoveAction => ({
    type: PREPARE_DOC_REF_MOVE,
    id,
    uuids,
    destinationUuid
  }),
  completeDocRefMove: (id: string): CompleteDocRefMoveAction => ({
    type: COMPLETE_DOC_REF_MOVE,
    id
  })
};

export interface StoreStatePerId {
  isMoving: boolean;
  uuids: Array<string>;
  destinationUuid?: string;
}

export interface StoreState extends StateById<StoreStatePerId> {}

// The state will contain a map of arrays.
// Keyed on explorer ID, the arrays will contain the doc refs being moved
export const defaultStatePerId: StoreStatePerId = {
  isMoving: false,
  uuids: [],
  destinationUuid: undefined
};

export const reducer = prepareReducerById(defaultStatePerId)
  .handleAction<PrepareDocRefMoveAction>(
    PREPARE_DOC_REF_MOVE,
    (state, { uuids, destinationUuid }: PrepareDocRefMoveAction) => ({
      isMoving: uuids.length > 0,
      uuids,
      destinationUuid
    })
  )
  .handleAction<CompleteDocRefMoveAction>(
    COMPLETE_DOC_REF_MOVE,
    () => defaultStatePerId
  )
  .handleForeignAction<DocRefsMovedAction>(
    DOC_REFS_COPIED,
    () => defaultStatePerId
  )
  .getReducer();
