import * as React from "react";
import * as ReactDOM from "react-dom";
import Hello from "./components/Hello";
import "./index.css";
import registerServiceWorker from "./registerServiceWorker";

ReactDOM.render(
  <Hello name="Typescript" enthusiasmLevel={5} />,
  document.getElementById("root") as HTMLElement
);
registerServiceWorker();
