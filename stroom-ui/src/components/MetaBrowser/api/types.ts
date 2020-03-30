import { ExpressionOperatorType } from "components/ExpressionBuilder/types";
import { PageRequest, StreamAttributeMapResult } from "../types";

export interface UseStreamSearch {
  fetch: (p: PageRequest) => void;
  search: (expression: ExpressionOperatorType, page: PageRequest) => void;
  streams: StreamAttributeMapResult;
}
