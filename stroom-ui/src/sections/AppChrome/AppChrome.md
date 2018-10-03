```jsx
const { Switch, Route } = require("react-router-dom");

const appChromeRoutes = require("./appChromeRoutes").default;

// This basically replicates the 'Routes' implementation, but for test
const AppChromeWithRouter = () => (
  <Switch>
    {appChromeRoutes.map((p, i) => (
      <Route key={i} {...p} />
    ))}
  </Switch>
);

<AppChromeWithRouter />;
```
