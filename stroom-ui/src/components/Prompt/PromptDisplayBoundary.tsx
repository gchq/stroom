import * as React from "react";
import {
  createContext,
  FunctionComponent,
  useCallback,
  useContext,
  useState,
} from "react";
import Prompt, { ContentProps, PromptProps, PromptType } from "./Prompt";

type PromptContextType = {
  props: PromptProps;
  setProps: (value: PromptProps) => void;
};

const PromptContext = createContext<PromptContextType | undefined>(undefined);

export interface Api {
  showPrompt: (props: PromptProps) => void;
  showInfo: (props: ContentProps) => void;
  showWarning: (props: ContentProps) => void;
  showError: (props: ContentProps) => void;
  showFatal: (props: ContentProps) => void;
}

export const usePrompt = (): Api => {
  const { setProps } = useContext(PromptContext);

  const showPrompt = useCallback(
    (props: PromptProps) => {
      setProps(props);
    },
    [setProps],
  );

  const showInfo = useCallback(
    ({ title = "Info", message = "" }) => {
      showPrompt({
        type: PromptType.INFO,
        title: title,
        message: message,
      });
    },
    [showPrompt],
  );

  const showWarning = useCallback(
    ({ title = "Warning", message = "" }) => {
      showPrompt({
        type: PromptType.WARNING,
        title: title,
        message: message,
      });
    },
    [showPrompt],
  );

  const showError = useCallback(
    ({ title = "Error", message = "" }) => {
      showPrompt({
        type: PromptType.ERROR,
        title: title,
        message: message,
      });
    },
    [showPrompt],
  );

  const showFatal = useCallback(
    ({ title = "Fatal", message = "" }) => {
      showPrompt({
        type: PromptType.FATAL,
        title: title,
        message: message,
      });
    },
    [showPrompt],
  );

  return { showPrompt, showInfo, showWarning, showError, showFatal };
};

const PromptOutlet: FunctionComponent = () => {
  const { props, setProps } = useContext(PromptContext);
  console.log("Render: Prompt");
  if (props !== undefined) {
    return (
      <Prompt promptProps={props} onCloseDialog={() => setProps(undefined)} />
    );
  } else {
    return null;
  }
};

export const PromptDisplayBoundary: FunctionComponent = ({ children }) => {
  const [props, setProps] = useState<PromptProps | undefined>(undefined);
  return (
    <PromptContext.Provider value={{ props, setProps }}>
      {children}
      <PromptOutlet />
    </PromptContext.Provider>
  );
};
