import { Action, Reducer, AnyAction } from "redux";

class TypeSafeReducer<TState> {
  reducers: {
    [s: string]: Reducer<TState, Action>;
  } = {};

  initialState: TState;

  constructor(initialState: TState) {
    this.initialState = initialState;
  }

  handleActions<TActionBase>(
    names: Array<string>,
    reducer: Reducer<TState, Action & TActionBase>
  ) {
    names.forEach(name => this.handleAction(name, reducer));
    return this;
  }

  handleAction<TAction extends Action>(
    name: string,
    reducer: Reducer<TState, TAction>
  ) {
    this.reducers[name] = reducer;
    return this; // allow method chaining
  }

  getReducer(): Reducer<TState, AnyAction> {
    return (state: TState = this.initialState, action: Action) => {
      let reducer = this.reducers[action.type];
      if (reducer) {
        return reducer(state, action);
      }

      return state;
    };
  }
}

const prepareReducer = <TState>(initialState: TState) =>
  new TypeSafeReducer(initialState);

export default prepareReducer;
