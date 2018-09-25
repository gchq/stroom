declare module "redux-localstorage" {
  export default function persistState<Out>(name: string): Out;
}
