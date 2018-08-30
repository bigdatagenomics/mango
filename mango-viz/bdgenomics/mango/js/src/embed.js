/* @flow */

// Export widget models and views, and the npm package version number.
// Export widget models and views, and the npm package version number.
module.exports = {};

var loadedModules = [
    require('./reads'),
    require('./features'),
    require('./variants'),
    require('./pileup')
];

for (var i = 0; i < loadedModules.length; i++) {
    if (loadedModules.hasOwnProperty(i)) {
        var loadedModule = loadedModules[i];
        for (var target_name in loadedModule) {
            if (loadedModule.hasOwnProperty(target_name)) {
                module.exports[target_name] = loadedModule[target_name];
            }
        }
    }
}

module.exports['version'] = require('../package.json').version;