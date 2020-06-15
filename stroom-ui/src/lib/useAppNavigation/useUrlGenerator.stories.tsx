import * as React from "react";

import { storiesOf } from "@storybook/react";
import useUrlGenerator from "./useUrlGenerator";
import JsonDebug from "testing/JsonDebug";

const TestHarness: React.FunctionComponent = () => {
  const [prefix, setPrefix] = React.useState<string>(":stuff");
  const [lastFunction, setLastFunction] = React.useState<string | undefined>(
    undefined,
  );
  const [lastUrl, setLastUrl] = React.useState<string | undefined>(undefined);
  const urlGenerator = useUrlGenerator(prefix);

  const onPrefixChange: React.ChangeEventHandler<HTMLInputElement> = React.useCallback(
    ({ target: { value } }) => setPrefix(value),
    [setPrefix],
  );

  const onButtonClick = React.useCallback(
    (name, navigationFn: () => string) => {
      setLastFunction(name);
      setLastUrl(navigationFn());
    },
    [setLastFunction, setLastUrl],
  );

  return (
    <div>
      <form>
        <label>Prefix</label>
        <input value={prefix} onChange={onPrefixChange} />
      </form>
      {Object.entries(urlGenerator)
        .map((k) => ({
          name: k[0],
          navigationFn: k[1],
        }))
        .map(({ name, navigationFn }) => (
          <div key={name}>
            <button onClick={() => onButtonClick(name, navigationFn)}>
              {name}
            </button>
          </div>
        ))}
      <JsonDebug value={{ prefix, lastFunction, lastUrl }} />
    </div>
  );
};

storiesOf("lib/useUrlGenerator", module).add("test", () => <TestHarness />);
