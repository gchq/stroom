import * as React from "react";
import { createContext, FunctionComponent, useContext, useEffect, useMemo, useRef, useState } from "react";
import AlertDialog, { Alert, AlertType } from "./AlertDialog";

const ErrorContext = createContext(null);

export function useErrorOutlet() {
  const errorCtx = useContext(ErrorContext);
  return errorCtx.setError;
}

export const AlertDisplayBoundary: FunctionComponent = ({
                                                          children,
                                                        }) => {
  const [error, setError] = useState(null);
  const ctx = useMemo(() => ({ error, setError }), [error]);

  return <ErrorContext.Provider value={ctx}>{children}</ErrorContext.Provider>;
};

export const ErrorOutlet: FunctionComponent = () => {
  const { error, setError } = useContext(ErrorContext);

  return (
    // error && (
    // <div role="alert">
    //   {error.message}
    // </div>

    // <AlertForm title={error.message} message={error.message}/>

    <AlertDialog alert={error} isOpen={error !== null} onCloseDialog={() => setError(null)}/>
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

export const ErrorInlet: FunctionComponent<ErrorInletProps> = ({ alert }) => {
  const ref = useRef();
  const errorContext = useContext(ErrorContext);

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

export const UsingErrorInlet: FunctionComponent = () => {
  const [someError, setTheError] = useState(null);

  const alert: Alert = {
    type: AlertType.ERROR,
    title: "Test",
    message: "Ouch, that hurts!",
  };

  return (
    <>
      <h2>Via component</h2>
      <ErrorInlet alert={someError}/>
      <button onClick={() => setTheError(alert)}>
        Press to render an error message somewhere
      </button>
      <button onClick={() => setTheError(null)}>Get rid of it</button>
    </>
  );
};

export const UsingErrorHook: FunctionComponent = () => {
  const setError = useErrorOutlet();

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
      <button onClick={() => setError(info)}>
        Info
      </button>
      <button onClick={() => setError(warning)}>
        Warning
      </button>
      <button onClick={() => setError(error)}>
        Error
      </button>
      <button onClick={() => setError(fatal)}>
        Fatal
      </button>
      <button onClick={() => setError(null)}>Get rid of it</button>
    </>
  );
};