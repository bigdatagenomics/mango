/**
 * This tests that pileup mismatches are rendered correctly, regardless of the
 * order in which the alignment and reference data come off the network.
 * 
 */
'use strict';var _createClass = (function () {function defineProperties(target, props) {for (var i = 0; i < props.length; i++) {var descriptor = props[i];descriptor.enumerable = descriptor.enumerable || false;descriptor.configurable = true;if ('value' in descriptor) descriptor.writable = true;Object.defineProperty(target, descriptor.key, descriptor);}}return function (Constructor, protoProps, staticProps) {if (protoProps) defineProperties(Constructor.prototype, protoProps);if (staticProps) defineProperties(Constructor, staticProps);return Constructor;};})();var _get = function get(_x, _x2, _x3) {var _again = true;_function: while (_again) {var object = _x, property = _x2, receiver = _x3;_again = false;if (object === null) object = Function.prototype;var desc = Object.getOwnPropertyDescriptor(object, property);if (desc === undefined) {var parent = Object.getPrototypeOf(object);if (parent === null) {return undefined;} else {_x = parent;_x2 = property;_x3 = receiver;_again = true;desc = parent = undefined;continue _function;}} else if ('value' in desc) {return desc.value;} else {var getter = desc.get;if (getter === undefined) {return undefined;}return getter.call(receiver);}}};function _interopRequireDefault(obj) {return obj && obj.__esModule ? obj : { 'default': obj };}function _classCallCheck(instance, Constructor) {if (!(instance instanceof Constructor)) {throw new TypeError('Cannot call a class as a function');}}function _inherits(subClass, superClass) {if (typeof superClass !== 'function' && superClass !== null) {throw new TypeError('Super expression must either be null or a function, not ' + typeof superClass);}subClass.prototype = Object.create(superClass && superClass.prototype, { constructor: { value: subClass, enumerable: false, writable: true, configurable: true } });if (superClass) Object.setPrototypeOf ? Object.setPrototypeOf(subClass, superClass) : subClass.__proto__ = superClass;}var _chai = require(



'chai');var _q = require(

'q');var _q2 = _interopRequireDefault(_q);var _underscore = require(
'underscore');var _underscore2 = _interopRequireDefault(_underscore);var _mainPileup = require(

'../../main/pileup');var _mainPileup2 = _interopRequireDefault(_mainPileup);var _mainDataTwoBit = require(
'../../main/data/TwoBit');var _mainDataTwoBit2 = _interopRequireDefault(_mainDataTwoBit);var _mainSourcesTwoBitDataSource = require(
'../../main/sources/TwoBitDataSource');var _mainSourcesTwoBitDataSource2 = _interopRequireDefault(_mainSourcesTwoBitDataSource);var _mainDataBam = require(
'../../main/data/bam');var _mainDataBam2 = _interopRequireDefault(_mainDataBam);var _mainSourcesBamDataSource = require(
'../../main/sources/BamDataSource');var _mainSourcesBamDataSource2 = _interopRequireDefault(_mainSourcesBamDataSource);var _mainRemoteFile = require(
'../../main/RemoteFile');var _mainRemoteFile2 = _interopRequireDefault(_mainRemoteFile);var _MappedRemoteFile = require(
'../MappedRemoteFile');var _MappedRemoteFile2 = _interopRequireDefault(_MappedRemoteFile);var _mainContigInterval = require(
'../../main/ContigInterval');var _mainContigInterval2 = _interopRequireDefault(_mainContigInterval);var _async = require(
'../async');var _dataCanvas = require(
'data-canvas');var _dataCanvas2 = _interopRequireDefault(_dataCanvas);


// This is like TwoBit, but allows a controlled release of sequence data.
var FakeTwoBit = (function (_TwoBit) {_inherits(FakeTwoBit, _TwoBit);


  function FakeTwoBit(remoteFile) {_classCallCheck(this, FakeTwoBit);
    _get(Object.getPrototypeOf(FakeTwoBit.prototype), 'constructor', this).call(this, remoteFile);
    this.deferred = _q2['default'].defer();}














  // This is like Bam, but allows a controlled release of one batch of alignments.
  _createClass(FakeTwoBit, [{ key: 'getFeaturesInRange', value: function getFeaturesInRange(contig, start, stop) {(0, _chai.expect)(contig).to.equal('chr17');(0, _chai.expect)(start).to.equal(7500000);(0, _chai.expect)(stop).to.equal(7510000);return this.deferred.promise;} }, { key: 'release', value: function release(sequence) {this.deferred.resolve(sequence);} }]);return FakeTwoBit;})(_mainDataTwoBit2['default']);var FakeBam = (function (_Bam) {_inherits(FakeBam, _Bam);


  function FakeBam(remoteFile, 
  remoteIndexFile, 
  indexChunks) {_classCallCheck(this, FakeBam);
    _get(Object.getPrototypeOf(FakeBam.prototype), 'constructor', this).call(this, remoteFile, remoteIndexFile, indexChunks);
    this.deferred = _q2['default'].defer();}_createClass(FakeBam, [{ key: 'getAlignmentsInRange', value: 


    function getAlignmentsInRange(range, opt_contained) {
      return this.deferred.promise;} }, { key: 'release', value: 


    function release(alignments) {
      this.deferred.resolve(alignments);} }]);return FakeBam;})(_mainDataBam2['default']);




describe('PileupTrack', function () {
  var testDiv = document.getElementById('testdiv');

  beforeEach(function () {
    // A fixed width container results in predictable x-positions for mismatches.
    testDiv.style.width = '800px';
    _dataCanvas2['default'].RecordingContext.recordAll();});


  afterEach(function () {
    _dataCanvas2['default'].RecordingContext.reset();
    // avoid pollution between tests.
    testDiv.innerHTML = '';});


  // Test data files
  var twoBitFile = new _MappedRemoteFile2['default'](
  '/test-data/hg19.2bit.mapped', 
  [[0, 16383], [691179834, 691183928], [694008946, 694011447]]), 
  bamFile = new _mainRemoteFile2['default']('/test-data/synth3.normal.17.7500000-7515000.bam'), 
  bamIndexFile = new _mainRemoteFile2['default']('/test-data/synth3.normal.17.7500000-7515000.bam.bai');

  // It simplifies the tests to have these variables available synchronously.
  var reference = '', 
  alignments = [];

  before(function () {
    var twoBit = new _mainDataTwoBit2['default'](twoBitFile), 
    bam = new _mainDataBam2['default'](bamFile, bamIndexFile);
    return twoBit.getFeaturesInRange('chr17', 7500000, 7510000).then(function (seq) {
      reference = seq;
      return bam.getAlignmentsInRange(new _mainContigInterval2['default']('chr17', 7500734, 7500795));}).
    then(function (result) {
      (0, _chai.expect)(result).to.have.length.above(0);
      alignments = result;});});



  function testSetup() {
    // The fake sources allow precise control over when they give up their data.
    var fakeTwoBit = new FakeTwoBit(twoBitFile), 
    fakeBam = new FakeBam(bamFile, bamIndexFile), 
    referenceSource = _mainSourcesTwoBitDataSource2['default'].createFromTwoBitFile(fakeTwoBit), 
    bamSource = _mainSourcesBamDataSource2['default'].createFromBamFile(fakeBam);

    var p = _mainPileup2['default'].create(testDiv, { 
      range: { contig: 'chr17', start: 7500734, stop: 7500795 }, 
      tracks: [
      { 
        data: referenceSource, 
        viz: _mainPileup2['default'].viz.genome(), 
        isReference: true }, 

      { 
        data: bamSource, 
        viz: _mainPileup2['default'].viz.pileup() }] });




    return { p: p, fakeTwoBit: fakeTwoBit, fakeBam: fakeBam };}var 


  drawnObjectsWith = _dataCanvas2['default'].RecordingContext.drawnObjectsWith;

  var hasReference = function hasReference() {
    // The reference initially shows "unknown" base pairs, so we have to
    // check for a specific known one to ensure that it's really loaded.
    return testDiv.querySelector('.reference canvas') && 
    drawnObjectsWith(testDiv, '.reference', function (x) {return x.letter;}).length > 0;}, 

  hasAlignments = function hasAlignments() {
    return testDiv.querySelector('.pileup canvas') && 
    drawnObjectsWith(testDiv, '.pileup', function (x) {return x.span;}).length > 0;}, 


  // Helpers for working with DataCanvas
  mismatchesAtPos = function mismatchesAtPos(pos) {return drawnObjectsWith(testDiv, '.pileup', function (x) {return x.basePair && x.pos == pos;});}, 

  // This checks that there are 22 C/T SNVs at chr17:7,500,765
  // XXX: IGV only shows 20
  assertHasColumnOfTs = function assertHasColumnOfTs() {
    var ref = drawnObjectsWith(testDiv, '.reference', function (x) {return x.pos == 7500765 - 1;});
    (0, _chai.expect)(ref).to.have.length(1);
    (0, _chai.expect)(ref[0].letter).to.equal('C');

    var mismatches = mismatchesAtPos(7500765 - 1);
    (0, _chai.expect)(mismatches).to.have.length(22);
    _underscore2['default'].each(mismatches, function (mm) {
      (0, _chai.expect)(mm.basePair).to.equal('T');});

    // Make sure there are no variants in the previous column, just the reference.
    (0, _chai.expect)(mismatchesAtPos(7500764 - 1).length).to.equal(0);};


  it('should indicate mismatches when the reference loads first', function () {var _testSetup = 
    testSetup();var p = _testSetup.p;var fakeTwoBit = _testSetup.fakeTwoBit;var fakeBam = _testSetup.fakeBam;

    // Release the reference first.
    fakeTwoBit.release(reference);

    // Wait for the track to render, then release the alignments.
    return (0, _async.waitFor)(hasReference, 2000).then(function () {
      fakeBam.release(alignments);
      return (0, _async.waitFor)(hasAlignments, 2000);}).
    then(function () {
      // Some number of mismatches are expected, but it should be dramatically
      // lower than the number of total base pairs in alignments.
      var mismatches = drawnObjectsWith(testDiv, '.pileup', function (x) {return x.basePair;});
      (0, _chai.expect)(mismatches).to.have.length.below(60);
      assertHasColumnOfTs();
      p.destroy();});});



  // Same as the previous test, but with the loads reversed.
  it('should indicate mismatches when the alignments load first', function () {var _testSetup2 = 
    testSetup();var p = _testSetup2.p;var fakeTwoBit = _testSetup2.fakeTwoBit;var fakeBam = _testSetup2.fakeBam;

    // Release the alignments first.
    fakeBam.release(alignments);

    // Wait for the track to render, then release the reference.
    return (0, _async.waitFor)(hasAlignments, 2000).then(function () {
      fakeTwoBit.release(reference);
      return (0, _async.waitFor)(hasReference, 2000);}).
    then(function () {
      var mismatches = drawnObjectsWith(testDiv, '.pileup', function (x) {return x.basePair;});
      (0, _chai.expect)(mismatches).to.have.length.below(60);
      assertHasColumnOfTs();
      p.destroy();});});



  it('should sort reads', function () {
    var p = _mainPileup2['default'].create(testDiv, { 
      range: { contig: 'chr17', start: 7500734, stop: 7500796 }, 
      tracks: [
      { 
        data: _mainPileup2['default'].formats.twoBit({ 
          url: '/test-data/test.2bit' }), 

        viz: _mainPileup2['default'].viz.genome(), 
        isReference: true }, 

      { 
        data: _mainPileup2['default'].formats.bam({ 
          url: '/test-data/synth3.normal.17.7500000-7515000.bam', 
          indexUrl: '/test-data/synth3.normal.17.7500000-7515000.bam.bai' }), 

        viz: _mainPileup2['default'].viz.pileup({ 
          viewAsPairs: false }) }] });





    return (0, _async.waitFor)(hasAlignments, 2000).then(function () {
      var alignments = drawnObjectsWith(testDiv, '.pileup', function (x) {return x.span;});
      var center = (7500796 + 7500734) / 2;
      (0, _chai.expect)(Math.floor(center)).to.equal(center); // no rounding issues

      var rowsAndSpans = alignments.map(function (x) {return [x.row, x.span.interval];});
      var centerRows = _underscore2['default'].uniq(
      rowsAndSpans.filter(function (rowIv) {return rowIv[1].contains(center);}).
      map(function (rowIv) {return rowIv[0];}));

      // The rows with alignments overlapping the center should be the first
      // ones, e.g. 0, 1, 2, 3. Any gaps will make this comparison fail.
      (0, _chai.expect)(_underscore2['default'].max(centerRows)).to.equal(centerRows.length - 1);

      p.destroy();});});});