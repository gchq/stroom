import * as React from "react";

type Callback = () => void;

interface Props {
  callback: Callback;
  delay: number;
}

export const useInterval = ({ callback, delay }: Props) => {
  // Set up the interval.
  React.useEffect(() => {
    const id = setInterval(callback, delay);
    return () => clearInterval(id);
  }, [callback, delay]);
};
