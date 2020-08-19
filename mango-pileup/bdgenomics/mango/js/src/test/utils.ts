import { expect } from 'chai';
import {isValidUrl} from './widget-utils'
var utils = require("../utils");


describe("utils >", () => {
    it("has valid genome builds", async function() {
        for (var key in utils.genomeBuilds) {
          expect(isValidUrl(utils.genomeBuilds[key])).to.be.true;
        }
    });
});
