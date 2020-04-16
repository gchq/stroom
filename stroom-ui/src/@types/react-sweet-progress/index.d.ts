declare module "react-sweet-progress" {
  export enum Status {
    success,
    error,
    active,
  }

  // Only specified when it's not linear
  export enum Type {
    circle,
  }

  export interface Props {
    percent: number;
    status?: Status;
    theme?: object;
    style?: object;
    type?: Type;
    width?: number;
    strokeWidth?: number;
    className?: string;
    symbolClassName?: string;
  }
  export class Progress extends React.Component<Props> {}
}
