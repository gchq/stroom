const path = require("path");
//const TSDocgenPlugin = require("react-docgen-typescript-webpack-plugin");

// Export a function. Accept the base config as the only param.
module.exports = (config, configType) => {
  // configType has a value of 'DEVELOPMENT' or 'PRODUCTION'
  // You can change the configuration based on that.
  // 'PRODUCTION' is used when building the static version of storybook.

  // Make whatever fine-grained changes you need
  config.module.rules.push({
    test: /\.scss$/,
    loaders: ["style-loader", "css-loader", "sass-loader"],
    include: path.resolve(__dirname, "../")
  });

  config.module.rules.push({
    test: /\.(ts|tsx)$/,
    include: path.resolve(__dirname, "../src"),
    loader: require.resolve("ts-loader")
  });
  //config.plugins.push(new TSDocgenPlugin()); // optional
  config.resolve.extensions.push(".ts", ".tsx");

  // Return the altered config
  return config;
};
