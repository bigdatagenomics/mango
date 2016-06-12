/**
 * This class parses and represents a single read in a SAM/BAM file.
 *
 * This is used instead of parsing directly via jBinary in order to:
 * - Make the parsing lazy (for significant performance wins)
 * - Make the resulting object more precisely typed.
 *
 * Parsing reads using SamRead is ~2-3x faster than using jBinary and
 * ThinAlignment directly.
 *
 * 
 */
'use strict';var _createClass = (function () {function defineProperties(target, props) {for (var i = 0; i < props.length; i++) {var descriptor = props[i];descriptor.enumerable = descriptor.enumerable || false;descriptor.configurable = true;if ('value' in descriptor) descriptor.writable = true;Object.defineProperty(target, descriptor.key, descriptor);}}return function (Constructor, protoProps, staticProps) {if (protoProps) defineProperties(Constructor.prototype, protoProps);if (staticProps) defineProperties(Constructor, staticProps);return Constructor;};})();function _interopRequireDefault(obj) {return obj && obj.__esModule ? obj : { 'default': obj };}function _classCallCheck(instance, Constructor) {if (!(instance instanceof Constructor)) {throw new TypeError('Cannot call a class as a function');}}var _jdataview = require(




'jdataview');var _jdataview2 = _interopRequireDefault(_jdataview);var _jbinary = require(
'jbinary');var _jbinary2 = _interopRequireDefault(_jbinary);var _underscore = require(
'underscore');var _underscore2 = _interopRequireDefault(_underscore);var _formatsBamTypes = require(
'./formats/bamTypes');var _formatsBamTypes2 = _interopRequireDefault(_formatsBamTypes);var _ContigInterval = require(
'../ContigInterval');var _ContigInterval2 = _interopRequireDefault(_ContigInterval);

// TODO: Make more extensive use of the jBinary specs.


var CIGAR_OPS = ['M', 'I', 'D', 'N', 'S', 'H', 'P', '=', 'X'];

var SEQUENCE_VALUES = ['=', 'A', 'C', 'M', 'G', 'R', 'S', 'V', 
'T', 'W', 'Y', 'H', 'K', 'D', 'B', 'N'];


function strandFlagToString(reverseStrand) {
  return reverseStrand ? '-' : '+';}var 



SamRead /* implements Alignment */ = (function () {















  /**
   * @param buffer contains the raw bytes of the serialized BAM read. It must
   *     contain at least one full read (but may contain more).
   * @param offset records where this alignment is located in the BAM file. It's
   *     useful as a unique ID for alignments.
   * @param ref is the human-readable name of the reference/contig (the binary
   *     encoding only contains an ID).
   */
  function SamRead(buffer, offset, ref) {_classCallCheck(this, SamRead);
    this.buffer = buffer;
    this.offset = offset;

    // Go ahead and parse a few fields immediately.
    var jv = this._getJDataView();
    this.refID = jv.getInt32(0);
    this.ref = ref;
    this.pos = jv.getInt32(4);
    this.l_seq = jv.getInt32(16);
    this.cigarOps = this._getCigarOps();
    this.name = this._getName();}









































































































































































  // Convert a structured Cigar object into the string format we all love.
  _createClass(SamRead, [{ key: 'toString', value: function toString() {var stop = this.pos + this.l_seq;return this.ref + ':' + (1 + this.pos) + '-' + stop;} }, { key: '_getJDataView', value: function _getJDataView() {var b = this.buffer;return new _jdataview2['default'](b, 0, b.byteLength, true /* little endian */);} /**
                                                                                                                                                                                                                                                                                                                                  * Returns an identifier which is unique within the BAM file.
                                                                                                                                                                                                                                                                                                                                  */ }, { key: 'getKey', value: function getKey() {return this.offset.toString();} }, { key: '_getName', value: function _getName() {var jv = this._getJDataView();var l_read_name = jv.getUint8(8);jv.seek(32); // the read-name starts at byte 32
      return jv.getString(l_read_name - 1); // ignore null-terminator
    } }, { key: 'getFlag', value: function getFlag() {return this._getJDataView().getUint16(14);} }, { key: 'getInferredInsertSize', value: function getInferredInsertSize() {return this._getJDataView().getUint16(28);} }, { key: 'getStrand', value: function getStrand() {return strandFlagToString(this.getFlag() & _formatsBamTypes2['default'].Flags.READ_STRAND);} // TODO: get rid of this; move all methods into SamRead.
  }, { key: 'getFull', value: function getFull() {if (this._full) return this._full;var jb = new _jbinary2['default'](this.buffer, _formatsBamTypes2['default'].TYPE_SET);var full = jb.read(_formatsBamTypes2['default'].ThickAlignment, 0);this._full = full;return full;} }, { key: 'getInterval', value: function getInterval() {if (this._interval) return this._interval; // use the cache
      var interval = new _ContigInterval2['default'](this.ref, this.pos, this.pos + this.getReferenceLength() - 1);return interval;} }, { key: 'intersects', value: function intersects(interval) {return interval.intersects(this.getInterval());} }, { key: '_getCigarOps', value: function _getCigarOps() {var jv = this._getJDataView(), l_read_name = jv.getUint8(8), n_cigar_op = jv.getUint16(12), pos = 32 + l_read_name, cigar_ops = new Array(n_cigar_op);for (var i = 0; i < n_cigar_op; i++) {var v = jv.getUint32(pos + 4 * i);cigar_ops[i] = { op: CIGAR_OPS[v & 0xf], length: v >> 4 };}return cigar_ops;} /**
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           * Returns per-base quality scores from 0-255.
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           */ }, { key: 'getQualityScores', value: function getQualityScores() {var jv = this._getJDataView(), l_read_name = jv.getUint8(8), n_cigar_op = jv.getUint16(12), l_seq = jv.getInt32(16), pos = 32 + l_read_name + 4 * n_cigar_op + Math.ceil(l_seq / 2);return jv.getBytes(l_seq, pos, true, /* little endian */true /* toArray */);} }, { key: 'getCigarString', value: function getCigarString() {return makeCigarString(this.getFull().cigar);} }, { key: 'getQualPhred', value: function getQualPhred() {return makeAsciiPhred(this.getQualityScores());} }, { key: 'getSequence', value: function getSequence() {if (this._seq) return this._seq;var jv = this._getJDataView(), l_read_name = jv.getUint8(8), n_cigar_op = jv.getUint16(12), l_seq = jv.getInt32(16), pos = 32 + l_read_name + 4 * n_cigar_op, basePairs = new Array(l_seq), numBytes = Math.ceil(l_seq / 2);for (var i = 0; i < numBytes; i++) {var b = jv.getUint8(pos + i);basePairs[2 * i] = SEQUENCE_VALUES[b >> 4];if (2 * i + 1 < l_seq) {basePairs[2 * i + 1] = SEQUENCE_VALUES[b & 0xf];}}var seq = basePairs.join('');this._seq = seq;return seq;} // Returns the length of the alignment from first aligned read to last aligned read.
  }, { key: 'getReferenceLength', value: function getReferenceLength() {return SamRead.referenceLengthFromOps(this.cigarOps);} }, { key: 'getMateProperties', value: function getMateProperties() {var jv = this._getJDataView(), flag = jv.getUint16(14);if (!(flag & _formatsBamTypes2['default'].Flags.READ_PAIRED)) return null;var nextRefId = jv.getInt32(20), nextPos = jv.getInt32(24), nextStrand = strandFlagToString(flag & _formatsBamTypes2['default'].Flags.MATE_STRAND);return { // If the mate is on another contig, there's no easy way to get its string name.
        ref: nextRefId == this.refID ? this.ref : null, pos: nextPos, strand: nextStrand };} }, { key: 'debugString', value: function debugString() {var f = this.getFull();return 'Name: ' + this.name + '\nFLAG: ' + this.getFlag() + '\nPosition: ' + this.getInterval() + '\nCIGAR: ' + this.getCigarString() + '\nSequence: ' + f.seq + '\nQuality:  ' + this.getQualPhred() + '\nTags: ' + JSON.stringify(f.auxiliary, null, '  ') + '\n    ';} }], [{ key: 'referenceLengthFromOps', value: function referenceLengthFromOps(ops) {var refLength = 0;ops.forEach(function (_ref) {var op = _ref.op;var length = _ref.length;switch (op) {case 'M':case 'D':case 'N':case '=':case 'X':refLength += length;}});return refLength;} }]);return SamRead;})();function makeCigarString(cigarOps) {return cigarOps.map(function (_ref2) {var op = _ref2.op;var length = _ref2.length;return length + op;}).join('');} // Convert an array of Phred scores to a printable string.
function makeAsciiPhred(qualities) {if (qualities.length === 0) return '';if (_underscore2['default'].every(qualities, function (x) {return x == 255;})) return '*';return qualities.map(function (q) {return String.fromCharCode(33 + q);}).join('');}module.exports = SamRead; // cached values