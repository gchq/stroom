import useRouter from "./useRouter";
import * as queryString from "query-string";

export const useHttpQueryParam = function <String>(
  param: string,
): string | undefined {
  const { router } = useRouter();
  if (!!router.location) {
    const params = queryString.parse(router.location.search);
    const actualParam = params[param] as string | undefined;
    return actualParam;
  } else return undefined;
};

export default useHttpQueryParam;
