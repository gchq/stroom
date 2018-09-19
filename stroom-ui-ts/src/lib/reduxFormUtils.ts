import { Action, Reducer } from "redux";

export const required = (value: any) => (value ? undefined : "Required");

export const minLength = (min: number) => (value: string) =>
  value && value.length < min ? `Must be ${min} characters or more` : undefined;
export const minLength2 = minLength(2);

export const truncate = (text: string, limit: number) =>
  text.length > limit ? `${text.substr(0, limit)}...` : text;

export interface ById<T> {
  [id: string]: T;
}

/**
 * Given an action handler for some perId state, returns an action handler that can generates total state for all IDs.
 */
export type ReducerPerIdCreator<TState> = (
  actionHandler: Reducer<TState>
) => Reducer<ById<TState>>;

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
export const createReducerPerId = <TStatePerId extends object>(
  fetchIdFunc: (x: Action) => string,
  defaultStatePerId: TStatePerId
): ReducerPerIdCreator<TStatePerId> => (
  reducerPerId: Reducer<TStatePerId>
): Reducer<ById<TStatePerId>> => (state: ById<TStatePerId>, action: Action) => {
  const id = fetchIdFunc(action);
  const stateForThisId = state[id] || defaultStatePerId;
  const updates = reducerPerId(stateForThisId, action);

  return {
    ...state,
    [id]: {
      ...(defaultStatePerId as any),
      ...(stateForThisId as any),
      ...(updates as any)
    }
  };
};
