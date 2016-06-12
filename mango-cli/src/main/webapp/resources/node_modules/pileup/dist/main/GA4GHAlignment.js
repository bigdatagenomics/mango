/**
 * This serves as a bridge between org.ga4gh.GAReadAlignment and the
 * pileup.js Alignment type.
 * 
 */
'use strict';var _createClass = (function () {function defineProperties(target, props) {for (var i = 0; i < props.length; i++) {var descriptor = props[i];descriptor.enumerable = descriptor.enumerable || false;descriptor.configurable = true;if ('value' in descriptor) descriptor.writable = true;Object.defineProperty(target, descriptor.key, descriptor);}}return function (Constructor, protoProps, staticProps) {if (protoProps) defineProperties(Constructor.prototype, protoProps);if (staticProps) defineProperties(Constructor, staticProps);return Constructor;};})();function _interopRequireDefault(obj) {return obj && obj.__esModule ? obj : { 'default': obj };}function _classCallCheck(instance, Constructor) {if (!(instance instanceof Constructor)) {throw new TypeError('Cannot call a class as a function');}}var _ContigInterval = require(



'./ContigInterval');var _ContigInterval2 = _interopRequireDefault(_ContigInterval);var _dataSamRead = require(
'./data/SamRead');var _dataSamRead2 = _interopRequireDefault(_dataSamRead);

// See https://github.com/ga4gh/schemas/blob/v0.5.1/src/main/resources/avro/common.avdl
var OP_MAP = { 
  ALIGNMENT_MATCH: 'M', 
  INSERT: 'I', 
  DELETE: 'D', 
  SKIP: 'N', 
  CLIP_SOFT: 'S', 
  CLIP_HARD: 'H', 
  PAD: 'P', 
  SEQUENCE_MATCH: '=', 
  SEQUENCE_MISMATCH: 'X' };


/**
 * This class acts as a bridge between org.ga4gh.GAReadAlignment and the
 * pileup.js Alignment type.
 */var 
GA4GHAlignment /* implements Alignment */ = (function () {







  // alignment follows org.ga4gh.GAReadAlignment
  // https://github.com/ga4gh/schemas/blob/v0.5.1/src/main/resources/avro/reads.avdl
  function GA4GHAlignment(alignment) {_classCallCheck(this, GA4GHAlignment);
    this.alignment = alignment;
    this.pos = alignment.alignment.position.position;
    this.ref = alignment.alignment.position.referenceName;
    this.name = alignment.fragmentName;

    this.cigarOps = alignment.alignment.cigar.map(
    function (_ref) {var operation = _ref.operation;var length = _ref.operationLength;return { op: OP_MAP[operation], length: length };});
    this._interval = new _ContigInterval2['default'](this.ref, 
    this.pos, 
    this.pos + this.getReferenceLength() - 1);}_createClass(GA4GHAlignment, [{ key: 'getKey', value: 


    function getKey() {
      return GA4GHAlignment.keyFromGA4GHResponse(this.alignment);} }, { key: 'getStrand', value: 


    function getStrand() {
      return this.alignment.alignment.position.reverseStrand ? '-' : '+';} }, { key: 'getQualityScores', value: 


    function getQualityScores() {
      return this.alignment.alignedQuality;} }, { key: 'getSequence', value: 


    function getSequence() {
      return this.alignment.alignedSequence;} }, { key: 'getInterval', value: 


    function getInterval() {
      return this._interval;} }, { key: 'intersects', value: 


    function intersects(interval) {
      return interval.intersects(this.getInterval());} }, { key: 'getReferenceLength', value: 


    function getReferenceLength() {
      return _dataSamRead2['default'].referenceLengthFromOps(this.cigarOps);} }, { key: 'getMateProperties', value: 


    function getMateProperties() {
      var next = this.alignment.nextMatePosition;
      return next && { 
        ref: next.referenceName, 
        pos: next.position, 
        strand: next.reverseStrand ? '-' : '+' };} }, { key: 'getInferredInsertSize', value: 



    function getInferredInsertSize() {
      // TODO: SAM/BAM writes this explicitly. Does GA4GH really not?
      var m = this.getMateProperties();
      if (m && m.ref == this.ref) {
        var start1 = this._interval.start(), 
        stop1 = this._interval.stop(), 
        start2 = m.pos, 
        stop2 = start2 + this.getSequence().length;
        return Math.max(stop1, stop2) - Math.min(start1, start2);} else 
      {
        return 0;}}



    // This is exposed as a static method to facilitate an optimization in GA4GHDataSource.
  }], [{ key: 'keyFromGA4GHResponse', value: function keyFromGA4GHResponse(alignment) {
      // this.alignment.id would be appealing here, but it's not actually unique!
      return alignment.fragmentName + ':' + alignment.readNumber;} }]);return GA4GHAlignment;})();



module.exports = GA4GHAlignment;