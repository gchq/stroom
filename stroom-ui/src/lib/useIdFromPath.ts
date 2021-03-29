import useRouter from "./useRouter";
import { useContext } from "react";
import { WithChromeContext } from "./useRouter/BrowserRouter";

export const useIdFromPath = (pathBeforeId: string): string | undefined => {
  const { urlPrefix } = useContext(WithChromeContext);
  pathBeforeId = `${urlPrefix}/${pathBeforeId}`;
  const { router } = useRouter();
  if (!!router.location) {
    const path = router.location.pathname;
    const sliceIndex = path.lastIndexOf(pathBeforeId) + pathBeforeId.length;
    const id = path.slice(sliceIndex);
    return id;
  }
  return undefined;
};

export default useIdFromPath;
