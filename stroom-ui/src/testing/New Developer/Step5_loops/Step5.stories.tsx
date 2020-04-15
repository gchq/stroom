import * as React from "react";
import { storiesOf } from "@storybook/react";

interface Person {
  name: string;
  age: number;
}

const people: Person[] = [
  {
    name: "Arnold",
    age: 34,
  },
  {
    name: "David",
    age: 35,
  },
  {
    name: "Kryten",
    age: 3000000,
  },
  {
    name: "Cat",
    age: 24,
  },
];

const TestHarness: React.FunctionComponent = () => {
  // So you can put JavaScript here

  return (
    // The parentheses indicate a 'HTML' expression
    <div>
      {/* The curly braces indicate a switch back to JavaScript expression
      It's an expression rather than a full chunk of code though*/}
      {people.map(({ name, age }, i) => (
        // Oh lookup, more parentheses, back to HTML
        <div key={i}>
          {/* ah, but there are more curly braces, back to JavaScript.... */}
          <h1>{name}</h1>
          <p>Is {age} years old</p>
        </div>
      ))}
    </div>
  );
};

storiesOf("New Developer/Step 5", module)
  .add("all", () => <TestHarness />)
  .add("avoid", () => {
    return (
      <div>
        If you put enough component logic in here, there are certain things
        about React that are not respected (mostly around Context)
      </div>
    );
  });
