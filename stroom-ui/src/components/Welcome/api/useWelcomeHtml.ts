// Service layer to define state to store and invoke rest http functions to populate state

import * as React from "react";
import useApi from "./useApi";
import { WelcomeData } from "./types";

interface DangerousInnerHtml {
  __html: string;
}

const useWelcomeHtml = (): DangerousInnerHtml => {
  // Get the API object the provides the function that returns the promise
  const { getWelcomeHtml } = useApi();

  // `<h1>About Stroom</h1><p>Stroom is designed to receive data from multiple systems.</p>`

  // Declare the React state object to hold the response from the REST API
  const [welcomeData, setWelcomeData] = React.useState<WelcomeData>({
    html: `TBD`,
  });

  // Use an effect to set the welcome data state when the component is mounted
  React.useEffect(() => {
    getWelcomeHtml().then(setWelcomeData);
  }, [setWelcomeData, getWelcomeHtml]);

  // Memoise the transformed return object so that any effect that uses the return value will not rerender
  return React.useMemo(() => ({ __html: welcomeData.html }), [welcomeData]);
};

export default useWelcomeHtml;
