declare module "react-panelgroup" {
  interface PanelWidth {
    size?: string | number;
    minSize?: number;
    resize?: "fixed" | "dynamic" | "stretch";
    snap?: number[];
  }

  export interface Props {
    direction: "column" | "row";
    className?: string;
    panelWidths?: PanelWidth[];
    spacing?: number;
    borderColor?: string;
    panelColour?: string;
    onUpdate?: (panelWidths: any) => void;
  }
  export default class PanelGroup extends React.Component<Props> {}
}
