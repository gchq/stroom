declare module "react-panelgroup" {
  export interface Props {
    direction: "column";
    className: string;
    panelWidths: any;
  }
  export default class PanelGroup extends React.Component<Props> {}
}
