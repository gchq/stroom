import { Action } from "redux";

import {
  prepareReducerById,
  ActionId,
  StateById
} from "../../../lib/redux-actions-ts";

import { DOC_REF_CREATED, DocRefCreatedAction } from "./documentTree";
import { DocRefType } from "../../../types";

export const PREPARE_DOC_REF_CREATION = "PREPARE_DOC_REF_CREATION";
export const COMPLETE_DOC_REF_CREATION = "COMPLETE_DOC_REF_CREATION";

export interface PrepareDocRefCreationAction
  extends ActionId,
    Action<"PREPARE_DOC_REF_CREATION"> {
  destination: DocRefType;
}

export interface CompleteDocRefCreationAction
  extends ActionId,
    Action<"COMPLETE_DOC_REF_CREATION"> {}

export const actionCreators = {
  prepareDocRefCreation: (
    id: string,
    destination: DocRefType
  ): PrepareDocRefCreationAction => ({
    type: PREPARE_DOC_REF_CREATION,
    id,
    destination
  }),
  completeDocRefCreation: (id: string): CompleteDocRefCreationAction => ({
    type: COMPLETE_DOC_REF_CREATION,
    id
  })
};

export interface StoreStatePerId {
  isOpen: boolean;
  destination?: DocRefType;
}

export interface StoreState extends StateById<StoreStatePerId> {}

export const defaultStatePerId: StoreStatePerId = {
  isOpen: false,
  destination: undefined
};

export const reducer = prepareReducerById(defaultStatePerId)
  .handleAction<PrepareDocRefCreationAction>(
    PREPARE_DOC_REF_CREATION,
    (_, { destination }) => ({
      isOpen: true,
      destination
    })
  )
  .handleAction<CompleteDocRefCreationAction>(
    COMPLETE_DOC_REF_CREATION,
    () => defaultStatePerId
  )
  .handleForeignAction<DocRefCreatedAction>(
    DOC_REF_CREATED,
    () => defaultStatePerId
  )
  .getReducer();
