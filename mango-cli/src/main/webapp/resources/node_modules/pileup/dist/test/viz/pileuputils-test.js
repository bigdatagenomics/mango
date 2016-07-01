'use strict';function _interopRequireDefault(obj) {return obj && obj.__esModule ? obj : { 'default': obj };}var _chai = require(




'chai');var _underscore = require(
'underscore');var _underscore2 = _interopRequireDefault(_underscore);var _mainVizPileuputils = require(

'../../main/viz/pileuputils');var _mainInterval = require(
'../../main/Interval');var _mainInterval2 = _interopRequireDefault(_mainInterval);var _mainContigInterval = require(
'../../main/ContigInterval');var _mainContigInterval2 = _interopRequireDefault(_mainContigInterval);var _mainDataBam = require(
'../../main/data/bam');var _mainDataBam2 = _interopRequireDefault(_mainDataBam);var _mainRemoteFile = require(
'../../main/RemoteFile');var _mainRemoteFile2 = _interopRequireDefault(_mainRemoteFile);

describe('pileuputils', function () {
  // This checks that pileup's guarantee is met.
  function checkGuarantee(reads, rows) {
    var readsByRow = _underscore2['default'].groupBy(reads, function (read, i) {return rows[i];});
    _underscore2['default'].each(readsByRow, function (reads) {
      // No pair of reads in the same row should intersect.
      for (var i = 0; i < reads.length - 1; i++) {
        for (var j = i + 1; j < reads.length; j++) {
          (0, _chai.expect)(reads[i].intersects(reads[j])).to.be['false'];}}});}





  it('should check the guarantee', function () {
    var reads = [
    new _mainInterval2['default'](0, 9), 
    new _mainInterval2['default'](5, 14), 
    new _mainInterval2['default'](10, 19)];

    checkGuarantee(reads, [0, 1, 2]); // ok
    checkGuarantee(reads, [0, 1, 0]); // ok
    (0, _chai.expect)(function () {return checkGuarantee(reads, [0, 0, 0]);}).to['throw'](); // not ok
  });

  it('should pile up a collection of reads', function () {
    var reads = [
    new _mainInterval2['default'](0, 9), 
    new _mainInterval2['default'](5, 14), 
    new _mainInterval2['default'](10, 19), 
    new _mainInterval2['default'](15, 24)];

    var rows = (0, _mainVizPileuputils.pileup)(reads);
    checkGuarantee(reads, rows);
    (0, _chai.expect)(rows).to.deep.equal([0, 1, 0, 1]);});


  it('should pile up a deep collection of reads', function () {
    var reads = [
    new _mainInterval2['default'](0, 9), 
    new _mainInterval2['default'](1, 10), 
    new _mainInterval2['default'](2, 11), 
    new _mainInterval2['default'](3, 12), 
    new _mainInterval2['default'](4, 13)];

    var rows = (0, _mainVizPileuputils.pileup)(reads);
    checkGuarantee(reads, rows);
    (0, _chai.expect)(rows).to.deep.equal([0, 1, 2, 3, 4]);});


  it('should pile up around a long read', function () {
    var reads = [
    new _mainInterval2['default'](1, 9), 
    new _mainInterval2['default'](0, 100), 
    new _mainInterval2['default'](5, 14), 
    new _mainInterval2['default'](10, 19), 
    new _mainInterval2['default'](15, 24)];

    var rows = (0, _mainVizPileuputils.pileup)(reads);
    checkGuarantee(reads, rows);
    (0, _chai.expect)(rows).to.deep.equal([0, 1, 2, 0, 2]);});


  it('should build a pileup progressively', function () {
    var reads = [
    new _mainInterval2['default'](1, 9), 
    new _mainInterval2['default'](0, 100), 
    new _mainInterval2['default'](5, 14), 
    new _mainInterval2['default'](10, 19), 
    new _mainInterval2['default'](15, 24)];

    var pileup = [];
    var rows = reads.map(function (read) {return (0, _mainVizPileuputils.addToPileup)(read, pileup);});
    checkGuarantee(reads, rows);
    (0, _chai.expect)(rows).to.deep.equal([0, 1, 2, 0, 2]);});


  var ref = // chr17:7513000-7513500
  "CCTTTTGGGTTCTTCCCTTAGCTCCTGCTCAAGTGTCCTCCCCACTCCCACAACCACTAATATTTTATCCA" + 
  "TTCCCTCTTCTTTTCCCTGTAATCCCAACACTTGGAGGCCGAGGTCGGTAGATCAGCTGAGGCCAGGAGTT" + 
  "CGAGACCAGTCTGGCCAATATGGCAAAACCCCATTGCTACTATATATATATGTATACATATACATATATAT" + 
  "ACACATACATATATATGTATATATACATGTATATGTATATATATACATGTATATGTATACATATATATACA" + 
  "TGTATATGTATACATATATATATACATGTATATGTATACATATATATATACATGTATATGTATACATGTAT" + 
  "GTATATATATACACACACACACACACACATATATATAAATTAGCCAGGCGTGGTGGCACATGGCTGTAACC" + 
  "TCAGCTATTCAGGGTGGCTGAGATATGAGAATCACTTGAAGCCAGGAGGCAGAGGCTGCAGGGTCGTCTGG" + 
  "ATTT";
  it('should split reads into ops', function () {
    var bamFile = new _mainRemoteFile2['default']('/test-data/synth3.normal.17.7500000-7515000.bam'), 
    bamIndexFile = new _mainRemoteFile2['default']('/test-data/synth3.normal.17.7500000-7515000.bam.bai'), 
    bam = new _mainDataBam2['default'](bamFile, bamIndexFile);

    var range = new _mainContigInterval2['default']('chr17', 7513106, 7513400);

    var fakeReferenceSource = { 
      getRangeAsString: function getRangeAsString(_ref) {var contig = _ref.contig;var start = _ref.start;var stop = _ref.stop;
        (0, _chai.expect)(contig).to.equal('17');
        (0, _chai.expect)(start).to.be.within(7513000, 7513500);
        (0, _chai.expect)(stop).to.be.within(7513000, 7513500);
        return ref.slice(start - 7513000, stop - 7513000 + 1);} };



    var unknownReferenceSource = { 
      getRangeAsString: function getRangeAsString(_ref2) {var start = _ref2.start;var stop = _ref2.stop;
        return _underscore2['default'].range(start, stop + 1).map(function (x) {return '.';}).join('');} };



    return bam.getAlignmentsInRange(range).then(function (reads) {
      var findRead = function findRead(startPos) {
        var r = null;
        for (var i = 0; i < reads.length; i++) {
          if (reads[i].pos == startPos) {
            (0, _chai.expect)(r).to.be['null']; // duplicate read
            r = reads[i];}}


        if (r) return r;
        throw 'Unable to find read starting at position ' + startPos;};


      var simpleMismatch = findRead(7513223 - 1), 
      deleteRead = findRead(7513329 - 1), 
      insertRead = findRead(7513205 - 1), 
      softClipRead = findRead(7513109 - 1);

      (0, _chai.expect)(simpleMismatch.getCigarString()).to.equal('101M');
      (0, _chai.expect)(deleteRead.getCigarString()).to.equal('37M4D64M');
      (0, _chai.expect)(insertRead.getCigarString()).to.equal('73M20I8M');
      (0, _chai.expect)(softClipRead.getCigarString()).to.equal('66S35M');

      (0, _chai.expect)((0, _mainVizPileuputils.getOpInfo)(simpleMismatch, fakeReferenceSource)).to.deep.equal({ 
        ops: [
        { op: 'M', length: 101, pos: 7513222, arrow: 'R' }], 

        mismatches: [{ pos: 7513272, basePair: 'G', quality: 1 }] });


      (0, _chai.expect)((0, _mainVizPileuputils.getOpInfo)(deleteRead, fakeReferenceSource)).to.deep.equal({ 
        ops: [
        { op: 'M', length: 37, pos: 7513328 + 0, arrow: null }, 
        { op: 'D', length: 4, pos: 7513328 + 37, arrow: null }, 
        { op: 'M', length: 64, pos: 7513328 + 41, arrow: 'R' }], 

        mismatches: [] });


      (0, _chai.expect)((0, _mainVizPileuputils.getOpInfo)(insertRead, fakeReferenceSource)).to.deep.equal({ 
        ops: [
        { op: 'M', length: 73, pos: 7513204 + 0, arrow: 'L' }, 
        { op: 'I', length: 20, pos: 7513204 + 73, arrow: null }, 
        { op: 'M', length: 8, pos: 7513204 + 73, arrow: null }], 

        mismatches: [] });


      (0, _chai.expect)((0, _mainVizPileuputils.getOpInfo)(softClipRead, fakeReferenceSource)).to.deep.equal({ 
        ops: [
        { op: 'S', length: 66, pos: 7513108 + 0, arrow: null }, 
        { op: 'M', length: 35, pos: 7513108 + 0, arrow: 'L' }], 

        mismatches: [
        { pos: 7513109, basePair: 'G', quality: 2 }, 
        { pos: 7513112, basePair: 'C', quality: 2 }] });



      (0, _chai.expect)((0, _mainVizPileuputils.getOpInfo)(simpleMismatch, unknownReferenceSource)).to.deep.equal({ 
        ops: [
        { op: 'M', length: 101, pos: 7513222, arrow: 'R' }], 

        mismatches: [] // no mismatches against unknown reference data
      });});});});