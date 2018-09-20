import { Action, Reducer, AnyAction } from "redux";

// Putting the state values behind the 'byId' identifier prevents TypeScript from allowing access of
// any old nonsense from this object.
export interface StateById<TStatePerId> {
  byId: {
    [s: string]: TStatePerId;
  };
}

export interface ActionId {
  id: string;
}

class TypeSafeReducer<TStatePerId> {
  reducers: {
    [s: string]: Reducer<TStatePerId, Action & ActionId>;
  } = {};

  initialStatePerId: TStatePerId;

  constructor(initialStatePerId: TStatePerId) {
    this.initialStatePerId = initialStatePerId;
  }

  handleActions(names: Array<string>, reducer: Reducer<TStatePerId, Action>) {
    names.forEach(name => this.handleAction(name, reducer));
    return this;
  }

  handleAction<TAction extends Action & ActionId>(
    name: string,
    reducer: Reducer<TStatePerId, TAction>
  ) {
    this.reducers[name] = reducer;
    return this; // allow method chaining
  }

  getReducer(): Reducer<StateById<TStatePerId>, AnyAction> {
    return (
      state: StateById<TStatePerId> = { byId: {} },
      action: Action & ActionId
    ): StateById<TStatePerId> => {
      let reducer = this.reducers[action.type];

      if (reducer) {
        let currentThisId: TStatePerId =
          state.byId[action.id] || this.initialStatePerId;
        let stateThisId: TStatePerId = reducer(currentThisId, action);
        return {
          byId: {
            ...state.byId,
            [action.id]: stateThisId
          }
        };
      }

      return state;
    };
  }
}

const prepareReducerById = <TStatePerId>(initialState: TStatePerId) =>
  new TypeSafeReducer(initialState);

export default prepareReducerById;
