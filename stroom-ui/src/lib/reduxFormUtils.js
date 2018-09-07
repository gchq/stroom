import { mapObject } from 'lib/treeUtils';

export const required = value => (value ? undefined : 'Required');

export const minLength = min => value =>
  (value && value.length < min ? `Must be ${min} characters or more` : undefined);
export const minLength2 = minLength(2);

export const truncate = (text, limit) =>
  (text.length > limit ? `${text.substr(0, limit)}...` : text);

/**
 * A Higher Order action handler, it can be used for reducers which maintain a state per ID.
 * The ID is retrieved using the fetchIdFunc, the default state per ID is applied to any changes.
 * The function that this wraps then just has to supply the updates to the state for a given ID.
 *
 * This function can be used if just a single action handler operators 'per id'.
 * If all the action handlers operate per ID, then use 'createActionHandlersPerId'
 *
 * @param {A function that returns the ID when given the action} fetchIdFunc
 * @param {The default state to use for any new ID} defaultStatePerId
 */
export const createActionHandlerPerId = (fetchIdFunc, defaultStatePerId) => actionHandler => (
  state,
  action,
) => {
  const id = fetchIdFunc(action);
  const current = state[id] || defaultStatePerId;
  const updates = actionHandler(state, action, current);

  return {
    ...state,
    [id]: {
      ...defaultStatePerId,
      ...current,
      ...updates,
    },
  };
};

/**
 * Creates a wrapper around an object containing action handlers.
 *
 * @param {A function that returns the ID when given the action} fetchIdFunc
 * @param {The default state to use for any new ID} defaultStatePerId
 */
export const createActionHandlersPerId = (fetchIdFunc, defaultStatePerId) => (actionHandlers) => {
  const wrapper = createActionHandlerPerId(fetchIdFunc, defaultStatePerId);

  return mapObject(actionHandlers, ah => wrapper(ah));
};
