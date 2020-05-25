const path = require('path');

var rules = [
    { test: /\.css$/, use: [{loader: "style-loader"}, {loader: "css-loader" }]},
    { test: /\.less$/, use: [{loader: "style-loader"}, {loader: "css-loader" }, {loader: "less-loader" }]},
    { test: /\.(jpg|png|gif)$/, use: "file" },
    // required to load font-awesome
    { test: /\.woff2(\?v=\d+\.\d+\.\d+)?$/, use: "url?limit=10000&mimetype=application/font-woff" },
    { test: /\.woff(\?v=\d+\.\d+\.\d+)?$/, use: "url?limit=10000&mimetype=application/font-woff" },
    { test: /\.ttf(\?v=\d+\.\d+\.\d+)?$/, use: "url?limit=10000&mimetype=application/octet-stream" },
    { test: /\.eot(\?v=\d+\.\d+\.\d+)?$/, use: "file" },
    { test: /\.svg(\?v=\d+\.\d+\.\d+)?$/, use: "url?limit=10000&mimetype=image/svg+xml" }
];

module.exports = [
    {// Notebook extension
     //
     // This bundle only contains the part of the JavaScript that is run on
     // load of the notebook. This section generally only performs
     // some configuration for requirejs, and provides the legacy
     // "load_ipython_extension" function which is required for any notebook
     // extension.
     //
        entry: './lib/extension.js',
        output: {
            filename: 'extension.js',
            path: path.resolve(__dirname, '../pileup/static/'),
            libraryTarget: 'amd'
        },
        externals: ['@jupyter-widgets/base'],
        mode: 'production'
    },
    {// Bundle for the notebook containing the custom widget views and models
     //
     // This bundle contains the implementation for the custom widget views and
     // custom widget.
     // It must be an amd module
     //
        entry: './lib/index.js',
        output: {
            filename: 'index.js',
            path: path.resolve(__dirname, '../pileup/static'),
            libraryTarget: 'amd'
        },
        devtool: 'source-map',
        module: {
            rules: rules
        },
        externals: ['@jupyter-widgets/base'],
        mode: 'production'
    },
    {// bqplot bundle for unpkg.
        entry: './lib/embed.js',
        output: {
            filename: 'index.js',
            path: path.resolve(__dirname, './dist/'),
            libraryTarget: 'amd'
        },
        devtool: 'source-map',
        module: {
            rules: rules
        },
        externals: ['@jupyter-widgets/base'],
        mode: 'production'
    }
];
