'use strict';function _interopRequireDefault(obj) {return obj && obj.__esModule ? obj : { 'default': obj };}var _chai = require(






'chai');var _underscore = require(
'underscore');var _underscore2 = _interopRequireDefault(_underscore);var _mainVizCoverageCache = require(

'../../main/viz/CoverageCache');var _mainVizCoverageCache2 = _interopRequireDefault(_mainVizCoverageCache);var _mainContigInterval = require(
'../../main/ContigInterval');var _mainContigInterval2 = _interopRequireDefault(_mainContigInterval);var _FakeAlignment = require(
'../FakeAlignment');

describe('CoverageCache', function () {
  function ci(chr, start, end) {
    return new _mainContigInterval2['default'](chr, start, end);}


  function makeCache(args) {
    var cache = new _mainVizCoverageCache2['default'](_FakeAlignment.fakeSource);
    _underscore2['default'].flatten(args).forEach(function (read) {return cache.addAlignment(read);});
    return cache;}


  it('should collect coverage', function () {
    var cache = makeCache([
    (0, _FakeAlignment.makeReadPair)(ci('chr1', 100, 200), ci('chr1', 800, 900)), 
    (0, _FakeAlignment.makeReadPair)(ci('chr1', 300, 400), ci('chr1', 750, 850)), 
    (0, _FakeAlignment.makeReadPair)(ci('chr2', 100, 200), ci('chr2', 300, 400))]);


    var bins = cache.binsForRef('chr1');
    (0, _chai.expect)(bins[100]).to.deep.equal({ count: 1 });
    (0, _chai.expect)(bins[799]).to.deep.equal({ count: 1 });
    (0, _chai.expect)(bins[800]).to.deep.equal({ count: 2 });
    (0, _chai.expect)(bins[850]).to.deep.equal({ count: 2 });
    (0, _chai.expect)(bins[851]).to.deep.equal({ count: 1 });
    (0, _chai.expect)(cache.maxCoverageForRef('chr1')).to.equal(2);});


  it('should collect mismatches', function () {
    var letter = '.'; // pretend the reference is this letter, repeated
    var refSource = _underscore2['default'].extend({}, _FakeAlignment.fakeSource, { 
      getRangeAsString: function getRangeAsString(range) {
        return letter.repeat(range.stop - range.start + 1);} });



    var makeSeqRead = function makeSeqRead(ci, seq) {
      (0, _chai.expect)(seq.length).to.equal(ci.length());
      var read = (0, _FakeAlignment.makeRead)(ci, '+');
      _underscore2['default'].extend(read, { 
        getSequence: function getSequence() {return seq;}, 
        cigarOps: [{ op: 'M', length: seq.length }] });

      return read;};


    var cache = new _mainVizCoverageCache2['default'](refSource);
    // reference starts unknown.                     01234567890
    cache.addAlignment(makeSeqRead(ci('1', 10, 15), 'AAAAAA')); // = ref
    cache.addAlignment(makeSeqRead(ci('1', 11, 16), 'AAAATA')); // mismatch
    cache.addAlignment(makeSeqRead(ci('1', 12, 17), 'CAAAAC')); // mismatch
    cache.addAlignment(makeSeqRead(ci('1', 13, 18), 'AAAAAA')); // = ref
    cache.addAlignment(makeSeqRead(ci('1', 14, 19), 'AGAAAA'));
    cache.addAlignment(makeSeqRead(ci('1', 15, 20), 'AACAAA'));

    letter = 'A'; // now the reference is known.
    cache.updateMismatches(ci('chr1', 1, 20));
    var bins = cache.binsForRef('chr1');
    (0, _chai.expect)(bins[10]).to.deep.equal({ count: 1 });
    (0, _chai.expect)(bins[11]).to.deep.equal({ count: 2 });
    (0, _chai.expect)(bins[12]).to.deep.equal({ count: 3, ref: 'A', mismatches: { C: 1 } });
    (0, _chai.expect)(bins[13]).to.deep.equal({ count: 4 });
    (0, _chai.expect)(bins[14]).to.deep.equal({ count: 5 });
    (0, _chai.expect)(bins[15]).to.deep.equal({ count: 6, ref: 'A', mismatches: { T: 1, G: 1 } });
    (0, _chai.expect)(bins[16]).to.deep.equal({ count: 5 });
    (0, _chai.expect)(bins[17]).to.deep.equal({ count: 4, ref: 'A', mismatches: { C: 2 } });
    (0, _chai.expect)(bins[18]).to.deep.equal({ count: 3 });
    (0, _chai.expect)(bins[19]).to.deep.equal({ count: 2 });
    (0, _chai.expect)(bins[20]).to.deep.equal({ count: 1 });
    (0, _chai.expect)(cache.maxCoverageForRef('chr1')).to.equal(6);

    // Now change the reference
    letter = 'C';
    cache.updateMismatches(ci('chr1', 1, 20));
    bins = cache.binsForRef('chr1');
    (0, _chai.expect)(bins[10]).to.deep.equal({ count: 1, ref: 'C', mismatches: { A: 1 } });
    (0, _chai.expect)(bins[12]).to.deep.equal({ count: 3, ref: 'C', mismatches: { A: 2 } });
    (0, _chai.expect)(bins[15]).to.deep.equal({ count: 6, ref: 'C', mismatches: { A: 4, T: 1, G: 1 } });
    (0, _chai.expect)(bins[17]).to.deep.equal({ count: 4, ref: 'C', mismatches: { A: 2 } });
    (0, _chai.expect)(cache.maxCoverageForRef('chr1')).to.equal(6);});});