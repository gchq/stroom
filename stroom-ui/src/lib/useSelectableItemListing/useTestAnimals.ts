import * as React from "react";

import v4 from "uuid/v4";
import { loremIpsum } from "lorem-ipsum";

export interface Animal {
  uuid: string;
  species: string;
  name: string;
}

const generateAnimal = (): Animal => ({
  uuid: v4(),
  species: loremIpsum({ units: "words", count: 1 }),
  name: loremIpsum({ units: "words", count: 3 }),
});

const AMOUNT_TO_FETCH = 5;
const initialAnimals = Array(AMOUNT_TO_FETCH * 2)
  .fill(1)
  .map(generateAnimal);
const MAX_ANIMALS = AMOUNT_TO_FETCH * 4;

interface OutProps {
  animals: Animal[];
  preFocusWrap: () => boolean;
  reset: () => void;
  addAnimal: (species: string, name: string) => void;
}

const useTestAnimals = (): OutProps => {
  const [animals, setAnimals] = React.useState<Animal[]>(initialAnimals);

  const reset = React.useCallback(() => setAnimals(initialAnimals), [
    setAnimals,
  ]);
  const preFocusWrap = React.useCallback((): boolean => {
    if (animals.length < MAX_ANIMALS) {
      setAnimals(
        animals.concat(Array(AMOUNT_TO_FETCH).fill(1).map(generateAnimal)),
      );
      return false;
    }

    return true;
  }, [animals, setAnimals]);

  const addAnimal = React.useCallback(
    (species: string, name: string) =>
      setAnimals(
        animals.concat([
          {
            uuid: v4(),
            species,
            name,
          },
        ]),
      ),
    [animals, setAnimals],
  );

  return {
    animals,
    reset,
    preFocusWrap,
    addAnimal,
  };
};

export default useTestAnimals;
