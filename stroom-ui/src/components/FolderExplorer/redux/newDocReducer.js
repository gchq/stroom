import { createActions, combineActions, handleActions } from 'redux-actions';

import { createActionHandlerPerId } from 'lib/reduxFormUtils';
import { actionCreators as documentTreeActionCreators } from './documentTree';

const { docRefCreated } = documentTreeActionCreators;

const actionCreators = createActions({
  PREPARE_DOC_REF_CREATION: (listingId, destination) => ({ listingId, isOpen: true, destination }),
  COMPLETE_DOC_REF_CREATION: listingId => ({ listingId, isOpen: false, destination: undefined }),
});

const { prepareDocRefCreation, completeDocRefCreation } = actionCreators;

// listings, keyed on ID, there may be several on a page
const defaultState = {};

const defaultListingState = {
  isOpen: false,
  destination: undefined,
};

const byListingId = createActionHandlerPerId(
  ({ payload: { listingId } }) => listingId,
  defaultListingState,
);

const reducer = handleActions(
  {
    [combineActions(prepareDocRefCreation, completeDocRefCreation)]: byListingId((state, { payload: { isOpen, destination } }) => ({
      isOpen,
      destination,
    })),
    [docRefCreated]: () => defaultState,
  },
  defaultState,
);

export { actionCreators, reducer, defaultListingState };
