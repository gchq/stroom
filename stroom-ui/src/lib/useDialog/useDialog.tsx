import { useState } from "react";

/**
 * These are the things returned by the custom hook that allow the owning component to interact
 * with this dialog.
 */
export type UseDialog<T> = {
  /**
   * The owning component is ready to start a deletion process.
   * Calling this will open the dialog, and setup the UUIDs
   */
  showDialog: (values: T) => void;
  /**
   * These are the properties that the owning component can just give to the Dialog component
   * using destructing.
   */
  componentProps: {
    isOpen: boolean;
    onCloseDialog: () => void;
    props: T | undefined;
  };
};

/**
 * This is a React custom hook that sets up things required by the owning component.
 */
export const useDialog = function<T>(defaultProps: T): UseDialog<T> {
  const [props, setProps] = useState<T | undefined>(defaultProps);
  const [isOpen, setIsOpen] = useState<boolean>(false);

  return {
    componentProps: {
      props,
      isOpen,
      onCloseDialog: () => {
        setIsOpen(false);
        setProps(defaultProps);
      }
    },
    showDialog: _props => {
      setIsOpen(true);
      setProps(_props);
    }
  };
};
