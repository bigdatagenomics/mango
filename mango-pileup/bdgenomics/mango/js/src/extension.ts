/* @flow */

// This file contains the javascript that is run when the notebook is loaded.
// It contains some requirejs configuration and the `load_ipython_extension`
// which is required for any notebook extension.

// Configure requirejs
if ((window as any).require) {
    (window as any).require.config({
        map: {
            "*" : {
                "bdgenomics.mango.pileup": "nbextensions/bdgenomics.mango.pileup/index"
            }
        }
    });
}

// Export the required load_ipython_extention
export function load_ipython_extension() {}
