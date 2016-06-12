/**
 * Data management for CoverageTrack.
 *
 * This class tracks counts and mismatches at each locus.
 *
 * 
 */
'use strict';Object.defineProperty(exports, '__esModule', { value: true });var _createClass = (function () {function defineProperties(target, props) {for (var i = 0; i < props.length; i++) {var descriptor = props[i];descriptor.enumerable = descriptor.enumerable || false;descriptor.configurable = true;if ('value' in descriptor) descriptor.writable = true;Object.defineProperty(target, descriptor.key, descriptor);}}return function (Constructor, protoProps, staticProps) {if (protoProps) defineProperties(Constructor.prototype, protoProps);if (staticProps) defineProperties(Constructor, staticProps);return Constructor;};})();function _interopRequireDefault(obj) {return obj && obj.__esModule ? obj : { 'default': obj };}function _classCallCheck(instance, Constructor) {if (!(instance instanceof Constructor)) {throw new TypeError('Cannot call a class as a function');}}var _pileuputils = require(







'./pileuputils');var _utils = require(
'../utils');var _utils2 = _interopRequireDefault(_utils);





// what does the reference have here?


// This class provides data management for the visualization, grouping paired
// reads and managing the pileup.
var CoverageCache = (function () {







  function CoverageCache(referenceSource) {_classCallCheck(this, CoverageCache);
    this.reads = {};
    this.refToCounts = {};
    this.refToMaxCoverage = {};
    this.referenceSource = referenceSource;}


  // Load a new read into the visualization cache.
  // Calling this multiple times with the same read is a no-op.
  _createClass(CoverageCache, [{ key: 'addAlignment', value: function addAlignment(read) {
      var key = read.getKey();
      if (key in this.reads) return; // we've already seen this one.
      this.reads[key] = read;

      var opInfo = (0, _pileuputils.getOpInfo)(read, this.referenceSource);

      this.addReadToCoverage(read, opInfo);}


    // Updates reference mismatch information for previously-loaded reads.
  }, { key: 'updateMismatches', value: function updateMismatches(range) {
      var ref = this._canonicalRef(range.contig);
      this.refToCounts[ref] = {}; // TODO: could be more efficient
      this.refToMaxCoverage[ref] = 0;
      for (var k in this.reads) {
        var read = this.reads[k];
        if (read.getInterval().chrOnContig(range.contig)) {
          var opInfo = (0, _pileuputils.getOpInfo)(read, this.referenceSource);
          this.addReadToCoverage(read, opInfo);}}} }, { key: 'addReadToCoverage', value: 




    function addReadToCoverage(read, opInfo) {
      // Add coverage/mismatch information
      var ref = this._canonicalRef(read.ref);
      if (!(ref in this.refToCounts)) {
        this.refToCounts[ref] = {};
        this.refToMaxCoverage[ref] = 0;}

      var counts = this.refToCounts[ref], 
      max = this.refToMaxCoverage[ref], 
      range = read.getInterval(), 
      start = range.start(), 
      stop = range.stop();
      for (var pos = start; pos <= stop; pos++) {
        var c = counts[pos];
        if (!c) {
          counts[pos] = c = { count: 0 };}

        c.count += 1;
        if (c.count > max) max = c.count;}var _iteratorNormalCompletion = true;var _didIteratorError = false;var _iteratorError = undefined;try {

        for (var _iterator = opInfo.mismatches[Symbol.iterator](), _step; !(_iteratorNormalCompletion = (_step = _iterator.next()).done); _iteratorNormalCompletion = true) {var mm = _step.value;
          var bin = counts[mm.pos];
          var mismatches;
          if (bin.mismatches) {
            mismatches = bin.mismatches;} else 
          {
            mismatches = bin.mismatches = {};
            bin.ref = this.referenceSource.getRangeAsString({ 
              contig: ref, start: mm.pos, stop: mm.pos });}

          var c = mismatches[mm.basePair] || 0;
          mismatches[mm.basePair] = 1 + c;}} catch (err) {_didIteratorError = true;_iteratorError = err;} finally {try {if (!_iteratorNormalCompletion && _iterator['return']) {_iterator['return']();}} finally {if (_didIteratorError) {throw _iteratorError;}}}


      this.refToMaxCoverage[ref] = max;} }, { key: 'maxCoverageForRef', value: 


    function maxCoverageForRef(ref) {
      return this.refToMaxCoverage[ref] || 
      this.refToMaxCoverage[_utils2['default'].altContigName(ref)] || 
      0;} }, { key: 'binsForRef', value: 


    function binsForRef(ref) {
      return this.refToCounts[ref] || 
      this.refToCounts[_utils2['default'].altContigName(ref)] || 
      {};}


    // Returns whichever form of the ref ("chr17", "17") has been seen.
  }, { key: '_canonicalRef', value: function _canonicalRef(ref) {
      if (this.refToCounts[ref]) return ref;
      var alt = _utils2['default'].altContigName(ref);
      if (this.refToCounts[alt]) return alt;
      return ref;} }]);return CoverageCache;})();



module.exports = CoverageCache; // These properties will only be present when there are mismatches.
// maps groupKey to VisualGroup
// ref --> position --> BinSummary