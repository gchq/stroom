import * as React from "react";
import { History } from "history";
import { RouteProps } from "react-router";
import { RouterContext, HistoryContext } from "./BrowserRouter";

interface UseRouter {
  history: History;
  router: RouteProps;
}

export default function useRouter(): UseRouter {
  const router = React.useContext(RouterContext);
  const history = React.useContext(HistoryContext);

  if (!history) {
    throw new Error("No History Provided");
  }

  return {
    router,
    history,
  };
}
