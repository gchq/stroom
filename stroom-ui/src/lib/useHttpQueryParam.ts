import useRouter from "./useRouter";
import * as queryString from "query-string";

export const useHttpQueryParam = function (param: string): string | undefined {
  const { router } = useRouter();
  if (router.location) {
    const params = queryString.parse(router.location.search);
    return params[param] as string | undefined;
  } else return undefined;
};

export default useHttpQueryParam;
