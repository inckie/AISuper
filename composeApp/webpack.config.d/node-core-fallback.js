// Kotlin/JS browser bundles can choke on Node's `node:` scheme in transitive deps.
module.exports = (config) => {
  config.resolve = config.resolve || {};
  config.resolve.fallback = {
    ...(config.resolve.fallback || {}),
    fs: false,
    net: false,
    os: false,
    path: false,
    tls: false,
  };

  config.plugins = config.plugins || [];
  config.plugins.push(
    {
      apply(compiler) {
        compiler.hooks.normalModuleFactory.tap("StripNodeProtocol", (nmf) => {
          nmf.hooks.beforeResolve.tap("StripNodeProtocol", (data) => {
            if (data && typeof data.request === "string" && data.request.startsWith("node:")) {
              data.request = data.request.slice(5);
            }
            return data;
          });
        });
      },
    }
  );

  return config;
};

