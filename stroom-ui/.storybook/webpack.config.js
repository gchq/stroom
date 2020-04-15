const path = require("path");

// Export a function. Accept the base config as the only param.
module.exports = ({ config, mode }) => {
  // mode has a value of 'DEVELOPMENT' or 'PRODUCTION'
  // You can change the configuration based on that.
  // 'PRODUCTION' is used when building the static version of storybook.

  // Remove old rules
  config.module.rules = config.module.rules.filter(
    item =>
      !(
        item.test &&
        typeof item.test === "object" &&
        item.test.test &&
        (item.test.test("t.svg") || item.test.test("t.png"))
      ),
  );

  // Updated image rule from a git forum
  config.module.rules.push({
    test: /\.(svg|ico|jpg|jpeg|png|gif|eot|otf|webp|ttf|woff|woff2)(\?.*)?$/,
    loader: require.resolve("file-loader"),
    query: {
      name: "static/media/[name].[hash:8].[ext]",
    },
  });

  config.module.rules.push({
    test: /\.(ts|tsx)$/,
    include: path.resolve(__dirname, "../src"),
    loader: require.resolve("ts-loader"),
  });
  config.resolve.extensions.push(".ts", ".tsx");

  config.resolve.modules = [
    ...(config.resolve.modules || []),
    path.resolve("./src"),
  ];

  // Return the altered config
  return config;
};
