'use strict';function _interopRequireDefault(obj) {return obj && obj.__esModule ? obj : { 'default': obj };}var _chai = require(


'chai');var _pako = require(

'pako');var _pako2 = _interopRequireDefault(_pako);var _jbinary = require(
'jbinary');var _jbinary2 = _interopRequireDefault(_jbinary);var _mainUtils = require(

'../main/utils');var _mainUtils2 = _interopRequireDefault(_mainUtils);var _mainInterval = require(
'../main/Interval');var _mainInterval2 = _interopRequireDefault(_mainInterval);

describe('utils', function () {
  describe('tupleLessOrEqual', function () {
    var lessEqual = _mainUtils2['default'].tupleLessOrEqual;

    it('should work on 1-tuples', function () {
      (0, _chai.expect)(lessEqual([0], [1])).to.be['true'];
      (0, _chai.expect)(lessEqual([1], [0])).to.be['false'];
      (0, _chai.expect)(lessEqual([0], [0])).to.be['true'];});


    it('should work on 2-tuples', function () {
      (0, _chai.expect)(lessEqual([0, 1], [0, 2])).to.be['true'];
      (0, _chai.expect)(lessEqual([0, 1], [0, 0])).to.be['false'];
      (0, _chai.expect)(lessEqual([0, 1], [1, 0])).to.be['true'];});});



  describe('tupleRangeOverlaps', function () {
    var overlap = _mainUtils2['default'].tupleRangeOverlaps;
    it('should work on 1-tuples', function () {
      var ivs = [
      [[0], [10]], 
      [[5], [15]], 
      [[-5], [5]], 
      [[-5], [4]]];

      var empty = [[4], [3]];
      (0, _chai.expect)(overlap(ivs[0], ivs[1])).to.be['true'];
      (0, _chai.expect)(overlap(ivs[0], ivs[2])).to.be['true'];
      (0, _chai.expect)(overlap(ivs[1], ivs[3])).to.be['false'];

      (0, _chai.expect)(overlap(ivs[0], empty)).to.be['false'];
      (0, _chai.expect)(overlap(ivs[1], empty)).to.be['false'];
      (0, _chai.expect)(overlap(ivs[2], empty)).to.be['false'];
      (0, _chai.expect)(overlap(ivs[3], empty)).to.be['false'];});


    it('should work on 2-tuples', function () {
      (0, _chai.expect)(overlap([[0, 0], [0, 10]], 
      [[0, 5], [0, 15]])).to.be['true'];
      (0, _chai.expect)(overlap([[0, 0], [0, 10]], 
      [[-1, 15], [0, 0]])).to.be['true'];
      (0, _chai.expect)(overlap([[0, 0], [0, 10]], 
      [[-1, 15], [1, -15]])).to.be['true'];
      (0, _chai.expect)(overlap([[0, 0], [0, 10]], 
      [[-1, 15], [0, -1]])).to.be['false'];
      (0, _chai.expect)(overlap([[0, 0], [0, 10]], 
      [[-1, 15], [0, 0]])).to.be['true'];
      (0, _chai.expect)(overlap([[0, 0], [0, 10]], 
      [[0, 10], [0, 11]])).to.be['true'];
      (0, _chai.expect)(overlap([[1, 0], [3, 10]], 
      [[-1, 10], [2, 1]])).to.be['true'];
      (0, _chai.expect)(overlap([[3, 0], [3, 10]], 
      [[-1, 10], [2, 1]])).to.be['false'];});});



  it('should concatenate ArrayBuffers', function () {
    var u8a = new Uint8Array([0, 1, 2, 3]), 
    u8b = new Uint8Array([4, 5, 6]), 
    concat = new Uint8Array(_mainUtils2['default'].concatArrayBuffers([u8a.buffer, u8b.buffer]));
    var result = [];
    for (var i = 0; i < concat.byteLength; i++) {
      result.push(concat[i]);}

    (0, _chai.expect)(result).to.deep.equal([0, 1, 2, 3, 4, 5, 6]);});


  function bufferToText(buf) {
    return new _jbinary2['default'](buf).read('string');}


  it('should inflate concatenated buffers', function () {
    var str1 = 'Hello World', 
    str2 = 'Goodbye, World', 
    buf1 = _pako2['default'].deflate(str1), 
    buf2 = _pako2['default'].deflate(str2), 
    merged = _mainUtils2['default'].concatArrayBuffers([buf1, buf2]);
    (0, _chai.expect)(buf1.byteLength).to.equal(19);
    (0, _chai.expect)(buf2.byteLength).to.equal(22);

    var inflated = _mainUtils2['default'].inflateConcatenatedGzip(merged);
    (0, _chai.expect)(inflated).to.have.length(2);
    (0, _chai.expect)(bufferToText(inflated[0].buffer)).to.equal('Hello World');
    (0, _chai.expect)(bufferToText(inflated[1].buffer)).to.equal('Goodbye, World');

    (0, _chai.expect)(inflated[0].offset).to.equal(0);
    (0, _chai.expect)(inflated[0].compressedLength).to.equal(19);
    (0, _chai.expect)(inflated[1].offset).to.equal(19);
    (0, _chai.expect)(inflated[1].compressedLength).to.equal(22);

    inflated = _mainUtils2['default'].inflateConcatenatedGzip(merged, 19);
    (0, _chai.expect)(inflated).to.have.length(2);
    (0, _chai.expect)(bufferToText(inflated[0].buffer)).to.equal('Hello World');
    (0, _chai.expect)(bufferToText(inflated[1].buffer)).to.equal('Goodbye, World');

    inflated = _mainUtils2['default'].inflateConcatenatedGzip(merged, 18);
    (0, _chai.expect)(inflated).to.have.length(1);
    (0, _chai.expect)(bufferToText(inflated[0].buffer)).to.equal('Hello World');

    inflated = _mainUtils2['default'].inflateConcatenatedGzip(merged, 0);
    (0, _chai.expect)(inflated).to.have.length(1);
    (0, _chai.expect)(bufferToText(inflated[0].buffer)).to.equal('Hello World');});


  it('should add or remove chr from contig names', function () {
    (0, _chai.expect)(_mainUtils2['default'].altContigName('21')).to.equal('chr21');
    (0, _chai.expect)(_mainUtils2['default'].altContigName('chr21')).to.equal('21');
    (0, _chai.expect)(_mainUtils2['default'].altContigName('M')).to.equal('chrM');
    (0, _chai.expect)(_mainUtils2['default'].altContigName('chrM')).to.equal('M');});


  describe('scaleRanges', function () {
    // This matches how LocationTrack and PileupTrack define "center".
    function center(iv) {
      return Math.floor((iv.stop + iv.start) / 2);}


    it('should scaleRanges', function () {
      // Zooming in and out should not change the center.
      // See https://github.com/hammerlab/pileup.js/issues/321
      var iv = new _mainInterval2['default'](7, 17);
      (0, _chai.expect)(center(iv)).to.equal(12);
      var iv2 = _mainUtils2['default'].scaleRange(iv, 0.5);
      (0, _chai.expect)(center(iv2)).to.equal(12);
      var iv3 = _mainUtils2['default'].scaleRange(iv2, 2.0);
      (0, _chai.expect)(center(iv3)).to.equal(12);

      // Zooming in & out once can shift the frame, but doing so repeatedly will
      // not produce any drift or growth/shrinkage.
      var iv4 = iv3.clone();
      for (var i = 0; i < 10; i++) {
        iv4 = _mainUtils2['default'].scaleRange(iv4, 0.5);
        iv4 = _mainUtils2['default'].scaleRange(iv4, 2.0);}

      (0, _chai.expect)(iv4.toString()).to.equal(iv3.toString());});


    it('should preserve centers', function () {
      function checkCenterThroughZoom(origIv) {
        var c = center(origIv);
        // Zoom in then out
        var iv = _mainUtils2['default'].scaleRange(origIv, 0.5);
        (0, _chai.expect)(center(iv)).to.equal(c);
        iv = _mainUtils2['default'].scaleRange(iv, 2.0);
        (0, _chai.expect)(center(iv)).to.equal(c);
        // Zoom out then in
        iv = _mainUtils2['default'].scaleRange(origIv, 2.0);
        (0, _chai.expect)(center(iv)).to.equal(c);
        iv = _mainUtils2['default'].scaleRange(iv, 0.5);
        (0, _chai.expect)(center(iv)).to.equal(c);}


      checkCenterThroughZoom(new _mainInterval2['default'](7, 17));
      checkCenterThroughZoom(new _mainInterval2['default'](8, 18));
      checkCenterThroughZoom(new _mainInterval2['default'](8, 19));
      checkCenterThroughZoom(new _mainInterval2['default'](7, 18));});


    it('should stay positive', function () {
      var iv = new _mainInterval2['default'](5, 25), 
      iv2 = _mainUtils2['default'].scaleRange(iv, 2.0);
      (0, _chai.expect)(iv2.toString()).to.equal('[0, 40]');});});



  describe('formatInterval', function () {
    it('should add commas to numbers', function () {
      (0, _chai.expect)(_mainUtils2['default'].formatInterval(new _mainInterval2['default'](0, 1234))).to.equal('0-1,234');
      (0, _chai.expect)(_mainUtils2['default'].formatInterval(new _mainInterval2['default'](1234, 567890123))).to.equal('1,234-567,890,123');});});



  describe('parseRange', function () {
    var parseRange = _mainUtils2['default'].parseRange;
    it('should parse intervals with and without commas', function () {
      (0, _chai.expect)(parseRange('1-1234')).to.deep.equal({ start: 1, stop: 1234 });
      (0, _chai.expect)(parseRange('1-1,234')).to.deep.equal({ start: 1, stop: 1234 });
      (0, _chai.expect)(parseRange('1-1,234')).to.deep.equal({ start: 1, stop: 1234 });
      (0, _chai.expect)(parseRange('1,234-567,890,123')).to.deep.equal({ start: 1234, stop: 567890123 });});


    it('should parse bare contigs', function () {
      (0, _chai.expect)(parseRange('17:')).to.deep.equal({ contig: '17' });
      (0, _chai.expect)(parseRange('chr17')).to.deep.equal({ contig: 'chr17' });
      (0, _chai.expect)(parseRange('17')).to.deep.equal({ start: 17 }); // this one is ambiguous
    });

    it('should parse contig + location', function () {
      (0, _chai.expect)(parseRange('17:1,234')).to.deep.equal({ contig: '17', start: 1234 });
      (0, _chai.expect)(parseRange('chrM:1,234,567')).to.deep.equal({ contig: 'chrM', start: 1234567 });});


    it('should parse combined locations', function () {
      (0, _chai.expect)(parseRange('17:1,234-5,678')).to.deep.equal(
      { contig: '17', start: 1234, stop: 5678 });});


    it('should return null for invalid ranges', function () {
      (0, _chai.expect)(parseRange('::')).to.be['null'];});});



  it('should flatMap', function () {
    (0, _chai.expect)(_mainUtils2['default'].flatMap([1, 2, 3], function (x) {return [x];})).to.deep.equal([1, 2, 3]);
    (0, _chai.expect)(_mainUtils2['default'].flatMap([1, 2, 3], function (x) {return x % 2 === 0 ? [x, x] : [];})).to.deep.equal([2, 2]);

    (0, _chai.expect)(_mainUtils2['default'].flatMap([[1, 2], [2, 3]], function (a) {return a;})).to.deep.equal([1, 2, 2, 3]);
    (0, _chai.expect)(_mainUtils2['default'].flatMap([[1, 2], [2, 3]], function (a) {return [a];})).to.deep.equal([[1, 2], [2, 3]]);});


  it('should compute percentiles', function () {
    //            75  50   25
    var xs = [7, 6, 5, 4, 3, 2, 1];
    (0, _chai.expect)(_mainUtils2['default'].computePercentile(xs, 50)).to.equal(4); // median
    (0, _chai.expect)(_mainUtils2['default'].computePercentile(xs, 25)).to.equal(2.5);
    (0, _chai.expect)(_mainUtils2['default'].computePercentile(xs, 75)).to.equal(5.5);

    // javascript sorting is lexicographic by default.
    (0, _chai.expect)(_mainUtils2['default'].computePercentile([9, 55, 456, 3210], 100)).to.equal(3210);

    // pathological cases
    (0, _chai.expect)(_mainUtils2['default'].computePercentile([1], 99)).to.equal(1);
    (0, _chai.expect)(_mainUtils2['default'].computePercentile([], 99)).to.equal(0);});});