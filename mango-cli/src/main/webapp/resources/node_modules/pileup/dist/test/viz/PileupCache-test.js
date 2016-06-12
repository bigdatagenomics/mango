'use strict';function _interopRequireDefault(obj) {return obj && obj.__esModule ? obj : { 'default': obj };}var _chai = require(






'chai');var _underscore = require(
'underscore');var _underscore2 = _interopRequireDefault(_underscore);var _mainVizPileupCache = require(

'../../main/viz/PileupCache');var _mainVizPileupCache2 = _interopRequireDefault(_mainVizPileupCache);var _mainContigInterval = require(
'../../main/ContigInterval');var _mainContigInterval2 = _interopRequireDefault(_mainContigInterval);var _mainDataBam = require(
'../../main/data/bam');var _mainDataBam2 = _interopRequireDefault(_mainDataBam);var _mainRemoteFile = require(
'../../main/RemoteFile');var _mainRemoteFile2 = _interopRequireDefault(_mainRemoteFile);var _FakeAlignment = require(
'../FakeAlignment');


describe('PileupCache', function () {
  function ci(chr, start, end) {
    return new _mainContigInterval2['default'](chr, start, end);}


  function makeCache(args, viewAsPairs) {
    var cache = new _mainVizPileupCache2['default'](_FakeAlignment.fakeSource, viewAsPairs);
    _underscore2['default'].flatten(args).forEach(function (read) {return cache.addAlignment(read);});
    return cache;}


  it('should group read pairs', function () {
    var cache = makeCache((0, _FakeAlignment.makeReadPair)(ci('chr1', 100, 200), 
    ci('chr1', 300, 400)), true /* viewAsPairs */);

    var groups = _underscore2['default'].values(cache.groups);
    (0, _chai.expect)(groups).to.have.length(1);
    var g = groups[0];
    (0, _chai.expect)(g.row).to.equal(0);
    (0, _chai.expect)(g.insert).to.not.be['null'];
    if (!g.insert) return; // for flow
    (0, _chai.expect)(g.insert.toString()).to.equal('[200, 300]');
    (0, _chai.expect)(g.alignments).to.have.length(2);
    (0, _chai.expect)(g.alignments[0].read.getInterval().toString()).to.equal('chr1:100-200');
    (0, _chai.expect)(g.alignments[1].read.getInterval().toString()).to.equal('chr1:300-400');
    (0, _chai.expect)(g.span.toString()).to.equal('chr1:100-400');
    (0, _chai.expect)(cache.pileupHeightForRef('chr1')).to.equal(1);
    (0, _chai.expect)(cache.pileupHeightForRef('chr2')).to.equal(0);});


  it('should group pile up pairs', function () {
    // A & B overlap, B & C overlap, but A & C do not. So two rows will suffice.
    var cache = makeCache([
    (0, _FakeAlignment.makeReadPair)(ci('chr1', 100, 200), ci('chr1', 300, 400)), // A
    (0, _FakeAlignment.makeReadPair)(ci('chr1', 300, 400), ci('chr1', 500, 600)), // B
    (0, _FakeAlignment.makeReadPair)(ci('chr1', 700, 800), ci('chr1', 500, 600)) // C
    ], true /* viewAsPairs */);

    var groups = _underscore2['default'].values(cache.groups);
    (0, _chai.expect)(groups).to.have.length(3);
    (0, _chai.expect)(groups[0].row).to.equal(0);
    (0, _chai.expect)(groups[1].row).to.equal(1);
    (0, _chai.expect)(groups[2].row).to.equal(0);
    (0, _chai.expect)(cache.pileupHeightForRef('chr1')).to.equal(2);});


  it('should pile pairs which overlap only in their inserts', function () {
    // No individual reads overlap, but they do when their inserts are included.
    var cache = makeCache([
    (0, _FakeAlignment.makeReadPair)(ci('chr1', 100, 200), ci('chr1', 800, 900)), 
    (0, _FakeAlignment.makeReadPair)(ci('chr1', 300, 400), ci('chr1', 500, 600))], 
    true /* viewAsPairs */);

    var groups = _underscore2['default'].values(cache.groups);
    (0, _chai.expect)(groups).to.have.length(2);
    (0, _chai.expect)(groups[0].row).to.equal(0);
    (0, _chai.expect)(groups[1].row).to.equal(1);
    (0, _chai.expect)(cache.pileupHeightForRef('chr1')).to.equal(2);});


  it('should pack unpaired reads more tightly', function () {
    // Same as the previous test, but with viewAsPairs = false.
    // When the inserts aren't rendered, the reads all fit on a single line.
    var cache = makeCache([
    (0, _FakeAlignment.makeReadPair)(ci('chr1', 100, 200), ci('chr1', 800, 900)), 
    (0, _FakeAlignment.makeReadPair)(ci('chr1', 300, 400), ci('chr1', 500, 600))], 
    false /* viewAsPairs */);
    var groups = _underscore2['default'].values(cache.groups);
    (0, _chai.expect)(groups).to.have.length(4);
    (0, _chai.expect)(cache.pileupHeightForRef('chr1')).to.equal(1);});


  it('should separate pairs on differing contigs', function () {
    var cache = makeCache((0, _FakeAlignment.makeReadPair)(ci('chr1', 100, 200), 
    ci('chr2', 150, 250)), true /* viewAsPairs */);

    var groups = _underscore2['default'].values(cache.groups);
    (0, _chai.expect)(groups).to.have.length(2);
    (0, _chai.expect)(groups[0].row).to.equal(0);
    (0, _chai.expect)(groups[1].row).to.equal(0);
    (0, _chai.expect)(groups[0].alignments).to.have.length(1);
    (0, _chai.expect)(groups[1].alignments).to.have.length(1);
    (0, _chai.expect)(groups[0].insert).to.be['null'];
    (0, _chai.expect)(groups[1].insert).to.be['null'];
    (0, _chai.expect)(cache.pileupHeightForRef('chr1')).to.equal(1);
    (0, _chai.expect)(cache.pileupHeightForRef('chr2')).to.equal(1);
    (0, _chai.expect)(cache.pileupHeightForRef('1')).to.equal(1);
    (0, _chai.expect)(cache.pileupHeightForRef('2')).to.equal(1);});


  it('should find overlapping reads', function () {
    var cache = makeCache([
    (0, _FakeAlignment.makeReadPair)(ci('chr1', 100, 200), ci('chr1', 800, 900)), 
    (0, _FakeAlignment.makeReadPair)(ci('chr1', 300, 400), ci('chr1', 500, 600)), 
    (0, _FakeAlignment.makeReadPair)(ci('chr2', 100, 200), ci('chr2', 300, 400))], 
    true /* viewAsPairs */);

    (0, _chai.expect)(cache.getGroupsOverlapping(ci('chr1', 50, 150))).to.have.length(1);
    (0, _chai.expect)(cache.getGroupsOverlapping(ci('chr1', 50, 350))).to.have.length(2);
    (0, _chai.expect)(cache.getGroupsOverlapping(ci('chr1', 300, 400))).to.have.length(2);
    (0, _chai.expect)(cache.getGroupsOverlapping(ci('chr1', 850, 950))).to.have.length(1);
    (0, _chai.expect)(cache.getGroupsOverlapping(ci('chr1', 901, 950))).to.have.length(0);
    (0, _chai.expect)(cache.getGroupsOverlapping(ci('chr2', 50, 150))).to.have.length(1);
    (0, _chai.expect)(cache.getGroupsOverlapping(ci('chr2', 250, 260))).to.have.length(1);
    (0, _chai.expect)(cache.getGroupsOverlapping(ci('chr3', 250, 260))).to.have.length(0);

    // 'chr'-tolerance
    (0, _chai.expect)(cache.getGroupsOverlapping(ci('1', 50, 150))).to.have.length(1);
    (0, _chai.expect)(cache.getGroupsOverlapping(ci('1', 50, 350))).to.have.length(2);});


  it('should sort reads at a locus', function () {
    var read = function read(start, stop) {return (0, _FakeAlignment.makeRead)(ci('chr1', start, stop), '+');};
    var cache = makeCache([
    read(100, 200), 
    read(150, 250), 
    read(200, 300), 
    read(250, 350)], 
    false /* viewAsPairs */);
    (0, _chai.expect)(cache.pileupHeightForRef('chr1')).to.equal(3);

    var formatReads = function formatReads(g) {return [g.row, g.span.toString()];};

    (0, _chai.expect)(cache.getGroupsOverlapping(ci('chr1', 0, 500)).
    map(formatReads)).to.deep.equal([
    [0, 'chr1:100-200'], 
    [1, 'chr1:150-250'], 
    [2, 'chr1:200-300'], 
    [0, 'chr1:250-350']]);


    cache.sortReadsAt('1', 275); // note 'chr'-tolerance
    (0, _chai.expect)(cache.getGroupsOverlapping(ci('chr1', 0, 500)).
    map(formatReads)).to.deep.equal([
    [1, 'chr1:100-200'], 
    [2, 'chr1:150-250'], 
    [0, 'chr1:200-300'], // these last two reads are now on top
    [1, 'chr1:250-350']]);


    // reads on another contig should not be affected by sorting.
    cache.addAlignment((0, _FakeAlignment.makeRead)(ci('chr2', 0, 100), '+'));
    cache.addAlignment((0, _FakeAlignment.makeRead)(ci('chr2', 50, 150), '+'));
    cache.addAlignment((0, _FakeAlignment.makeRead)(ci('chr2', 100, 200), '+'));
    (0, _chai.expect)(cache.getGroupsOverlapping(ci('chr2', 0, 500)).
    map(formatReads)).to.deep.equal([
    [0, 'chr2:0-100'], 
    [1, 'chr2:50-150'], 
    [2, 'chr2:100-200']]);


    cache.sortReadsAt('chr1', 150);
    (0, _chai.expect)(cache.getGroupsOverlapping(ci('chr1', 0, 500)).
    map(formatReads)).to.deep.equal([
    [0, 'chr1:100-200'], 
    [1, 'chr1:150-250'], 
    [2, 'chr1:200-300'], 
    [0, 'chr1:250-350']]);

    (0, _chai.expect)(cache.getGroupsOverlapping(ci('chr2', 0, 500)).
    map(formatReads)).to.deep.equal([
    [0, 'chr2:0-100'], 
    [1, 'chr2:50-150'], 
    [2, 'chr2:100-200']]);});



  it('should sort paired reads at a locus', function () {
    var cache = makeCache([
    (0, _FakeAlignment.makeReadPair)(ci('chr1', 100, 200), ci('chr1', 800, 900)), 
    (0, _FakeAlignment.makeReadPair)(ci('chr1', 300, 400), ci('chr1', 500, 600))], 
    true /* viewAsPairs */);


    var groups = _underscore2['default'].values(cache.groups);
    (0, _chai.expect)(groups).to.have.length(2);

    var rows = function rows() {return groups.map(function (g) {return g.row;});};
    (0, _chai.expect)(rows()).to.deep.equal([0, 1]);
    (0, _chai.expect)(cache.pileupHeightForRef('chr1')).to.equal(2);

    // While both groups overlap this locus when you include the insert, only
    // the second group has a read which overlaps it.
    cache.sortReadsAt('chr1', 350);
    (0, _chai.expect)(rows()).to.deep.equal([1, 0]);

    cache.sortReadsAt('chr1', 850);
    (0, _chai.expect)(rows()).to.deep.equal([0, 1]);});


  it('should sort a larger pileup of pairs', function () {
    // A:   <---        --->
    // B:        <---  --->
    // C:      <---   --->
    // x          |
    // (x intersects reads on B&C but only the insert on A)
    var cache = makeCache([
    (0, _FakeAlignment.makeReadPair)(ci('chr1', 100, 200), ci('chr1', 600, 700)), // A
    (0, _FakeAlignment.makeReadPair)(ci('chr1', 300, 400), ci('chr1', 550, 650)), // B
    (0, _FakeAlignment.makeReadPair)(ci('chr1', 250, 350), ci('chr1', 500, 600)) // C
    ], true /* viewAsPairs */);

    var groups = _underscore2['default'].values(cache.groups);
    var rows = function rows() {return groups.map(function (g) {return g.row;});};
    (0, _chai.expect)(rows()).to.deep.equal([0, 1, 2]);

    cache.sortReadsAt('chr1', 325); // x
    (0, _chai.expect)(rows()).to.deep.equal([2, 1, 0]);});


  it('should compute statistics on a BAM file', function () {
    this.timeout(5000);
    var bam = new _mainDataBam2['default'](
    new _mainRemoteFile2['default']('/test-data/synth4.tumor.1.4930000-4950000.bam'), 
    new _mainRemoteFile2['default']('/test-data/synth4.tumor.1.4930000-4950000.bam.bai'));
    return bam.getAlignmentsInRange(ci('chr1', 4930382, 4946898)).then(function (reads) {
      (0, _chai.expect)(reads).to.have.length.above(1000);
      var cache = makeCache(reads, true /* viewAsPairs */);
      var stats = cache.getInsertStats();
      (0, _chai.expect)(stats.minOutlierSize).to.be.within(1, 100);
      (0, _chai.expect)(stats.maxOutlierSize).to.be.within(500, 600);});});



  // TODO:
  // - a mate with an insertion or deletion
  // - unpaired reads
});