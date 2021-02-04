import { expect } from 'chai';
var pileupViewer = require('../pileupViewer');
import { DummyManager } from './dummy-manager';
import {create_pileup} from './widget-utils'

describe("pileupViewer", () => {
    beforeEach(async function() {
    this.manager = new DummyManager({ pileupViewer: pileupViewer });
    });

    it("has set correct parameters", async function() {
        const { pileup } = await create_pileup(this.manager);
        expect(pileup.attributes.start).to.equal(1);
        expect(pileup.attributes.stop).to.equal(50);
        expect(pileup.attributes.chrom).to.equal("chr1");

    });
});
