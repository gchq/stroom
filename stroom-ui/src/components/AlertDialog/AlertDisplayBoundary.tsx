import * as React from "react";
import {
  createContext,
  FunctionComponent,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import AlertDialog, { Alert, AlertType } from "./AlertDialog";

const AlertContext = createContext(null);

export const useAlert = () => {
  const errorCtx = useContext(AlertContext);

  const alert = useCallback(
    (alert: Alert) => {
      errorCtx.setError(alert);
    },
    [errorCtx],
  );

  return { alert };
};

export const AlertDisplayBoundary: FunctionComponent = ({ children }) => {
  const [error, setError] = useState<Alert>(null);
  const ctx = useMemo(() => ({ error, setError }), [error]);

  return <AlertContext.Provider value={ctx}>{children}</AlertContext.Provider>;
};

export const AlertOutlet: FunctionComponent = () => {
  const { error, setError } = useContext(AlertContext);

  return (
    // error && (
    // <div role="alert">
    //   {error.message}
    // </div>

    // <AlertForm title={error.message} message={error.message}/>

    <AlertDialog
      alert={error}
      isOpen={error !== null}
      onCloseDialog={() => setError(null)}
    />
    // <ThemedModal
    //   isOpen={error !== null}
    //   // onRequestClose={clearError()}
    //   header={<ImageHeader imageSrc={require("../../images/alert/error.svg")} text="Error"/>}
    //   content={<AlertForm title={error ? error.message : undefined} message={error ? error.stackTrace : undefined}/>}
    //   actions={
    //     <OkButtons
    //       onOk={() => setError(null)}
    //     />
    //   }
    // />
    // )
  );
};

interface ErrorInletProps {
  alert?: Alert;
}

export const AlertInlet: FunctionComponent<ErrorInletProps> = ({ alert }) => {
  const ref = useRef();
  const errorContext = useContext(AlertContext);

  useEffect(() => {
    if (errorContext === ref.current) {
      // This render has not been triggered via the context
      errorContext.setError(alert);
    } else {
      ref.current = errorContext;
    }
  });
  return null;
};

export const UsingAlertInlet: FunctionComponent = () => {
  const [someError, setTheError] = useState(null);

  const alert: Alert = {
    type: AlertType.ERROR,
    title: "Test",
    message: "Ouch, that hurts!",
  };

  return (
    <>
      <h2>Via component</h2>
      <AlertInlet alert={someError} />
      <button onClick={() => setTheError(alert)}>
        Press to render an error message somewhere
      </button>
      <button onClick={() => setTheError(null)}>Get rid of it</button>
    </>
  );
};

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
