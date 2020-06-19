import * as React from "react";
import { storiesOf } from "@storybook/react";

import { AlertDisplayBoundary, useAlert } from "./AlertDisplayBoundary";
import { FunctionComponent } from "react";
import { Alert, AlertType } from "./AlertDialog";

// interface ErrorInletProps {
//   alert?: Alert;
// }
//
// export const AlertInlet: FunctionComponent<ErrorInletProps> = ({ alert }) => {
//   const ref = useRef();
//   const errorContext = useContext(AlertContext);
//
//   useEffect(() => {
//     if (errorContext === ref.current) {
//       // This render has not been triggered via the context
//       errorContext.setAlert(alert);
//     } else {
//       ref.current = errorContext;
//     }
//   });
//   return null;
// };
//
// export const UsingAlertInlet: FunctionComponent = () => {
//   const [someError, setTheError] = useState(null);
//
//   const alert: Alert = {
//     type: AlertType.ERROR,
//     title: "Test",
//     message: "Ouch, that hurts!",
//   };
//
//   return (
//     <>
//       <h2>Via component</h2>
//       <AlertInlet alert={someError} />
//       <button onClick={() => setTheError(alert)}>
//         Press to render an error message somewhere
//       </button>
//       <button onClick={() => setTheError(undefined)}>Get rid of it</button>
//     </>
//   );
// };

export const UsingAlertHook: FunctionComponent = () => {
  const { alert } = useAlert();

  const info: Alert = {
    type: AlertType.INFO,
    title: "Test",
    message: "Ouch, that hurts!",
  };

  const warning: Alert = {
    type: AlertType.WARNING,
    title: "Test",
    message: "Ouch, that hurts!",
  };

  const error: Alert = {
    type: AlertType.ERROR,
    title: "Test",
    message: "Ouch, that hurts!",
  };

  const fatal: Alert = {
    type: AlertType.FATAL,
    title: "Test",
    message: "Ouch, that hurts!",
  };

  return (
    <>
      <h2>Via hook</h2>
      <button onClick={() => alert(info)}>Info</button>
      <button onClick={() => alert(warning)}>Warning</button>
      <button onClick={() => alert(error)}>Error</button>
      <button onClick={() => alert(fatal)}>Fatal</button>
      <button onClick={() => alert(null)}>Get rid of it</button>
    </>
  );
};

const TestHarness: React.FunctionComponent = () => {
  return (
    <AlertDisplayBoundary>
      <p>Here be errors...</p>
      <hr />
      <UsingAlertHook />
    </AlertDisplayBoundary>
  );
};

storiesOf("AlertDialog", module).add("AlertDisplay", () => <TestHarness />);
