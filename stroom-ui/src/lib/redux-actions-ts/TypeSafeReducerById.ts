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
    domestic: {
      [s: string]: Reducer<TStatePerId, Action & ActionId>;
    };
    foreign: {
      [s: string]: Reducer<TStatePerId, Action>;
    };
  } = {
    domestic: {},
    foreign: {}
  };

  initialStatePerId: TStatePerId;

  constructor(initialStatePerId: TStatePerId) {
    this.initialStatePerId = initialStatePerId;
  }

  handleActions<TActionBase>(
    names: Array<string>,
    reducer: Reducer<TStatePerId, ActionId & Action & TActionBase>
  ) {
    names.forEach(name => this.handleAction(name, reducer));
    return this;
  }

  handleAction<TAction extends Action & ActionId>(
    name: string,
    reducer: Reducer<TStatePerId, TAction>
  ) {
    this.reducers.domestic[name] = reducer;
    return this; // allow method chaining
  }

  handleForeignAction<TAction extends Action>(
    name: string,
    reducer: Reducer<TStatePerId, TAction>
  ) {
    this.reducers.foreign[name] = reducer;
    return this;
  }

  getReducer(): Reducer<StateById<TStatePerId>, AnyAction> {
    return (
      state: StateById<TStatePerId> = { byId: {} },
      action: Action & ActionId
    ): StateById<TStatePerId> => {
      let reducer =
        this.reducers.domestic[action.type] ||
        this.reducers.foreign[action.type];

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
