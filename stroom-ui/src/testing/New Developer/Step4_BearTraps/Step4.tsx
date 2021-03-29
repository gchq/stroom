import * as React from "react";

export interface Car {
  model: string;
  manufacturer: string;
}

interface Props {
  cars: Car[];
  loadCars: () => void;
}

const Step4: React.FunctionComponent<Props> = ({ cars, loadCars }) => {
  const onClickThing = React.useCallback(
    () => console.log("Clicked on Thing"),
    [],
  );

  const carModels: string[] = React.useMemo(() => cars.map((c) => c.model), [
    cars,
  ]);

  React.useEffect(() => loadCars(), [loadCars]);

  React.useEffect(() => onClickThing(), [onClickThing]);

  // React.useEffect(() => {
  //   console.log("Cars passed in", cars);
  // }, [cars]);

  // React.useEffect(() => {
  //   console.log("Car Models", carModels);
  // }, [carModels]);

  return (
    <div>
      Step 4<button onClick={onClickThing}>Click Thing</button>
      <ul>
        {cars.map(({ model, manufacturer }, i) => (
          <li key={i}>{`${model}-${manufacturer}`}</li>
        ))}
      </ul>
      <h1>Car Names</h1>
      <ul>
        {carModels.map((c, i) => (
          <li key={i}>{c}</li>
        ))}
      </ul>
    </div>
  );
};

export default Step4;
