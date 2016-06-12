/**
 * A store for sequences.
 *
 * This is used to store and retrieve reference data.
 *
 * 
 */
'use strict';var _createClass = (function () {function defineProperties(target, props) {for (var i = 0; i < props.length; i++) {var descriptor = props[i];descriptor.enumerable = descriptor.enumerable || false;descriptor.configurable = true;if ('value' in descriptor) descriptor.writable = true;Object.defineProperty(target, descriptor.key, descriptor);}}return function (Constructor, protoProps, staticProps) {if (protoProps) defineProperties(Constructor.prototype, protoProps);if (staticProps) defineProperties(Constructor, staticProps);return Constructor;};})();function _interopRequireDefault(obj) {return obj && obj.__esModule ? obj : { 'default': obj };}function _classCallCheck(instance, Constructor) {if (!(instance instanceof Constructor)) {throw new TypeError('Cannot call a class as a function');}}var _underscore = require(




'underscore');var _underscore2 = _interopRequireDefault(_underscore);var _utils = require(

'./utils');

// Store sequences of this many base pairs together in a single string.
var CHUNK_SIZE = 1000;








// This class stores sequences efficiently.
var SequenceStore = (function () {



  function SequenceStore() {_classCallCheck(this, SequenceStore);
    this.contigMap = {};}


  /**
   * Set a range of the genome to the particular sequence.
   * This overwrites any existing data.
   */_createClass(SequenceStore, [{ key: 'setRange', value: 
    function setRange(range, sequence) {
      if (range.length() === 0) return;
      if (range.length() != sequence.length) {
        throw 'setRange length mismatch';}


      var seqs = this._getSequences(range.contig);
      if (!seqs) {
        seqs = this.contigMap[range.contig] = {};}var _iteratorNormalCompletion = true;var _didIteratorError = false;var _iteratorError = undefined;try {


        for (var _iterator = this._chunkForInterval(range.interval)[Symbol.iterator](), _step; !(_iteratorNormalCompletion = (_step = _iterator.next()).done); _iteratorNormalCompletion = true) {var chunk = _step.value;
          var pos = chunk.chunkStart + chunk.offset - range.start();
          this._setChunk(seqs, chunk, sequence.slice(pos, pos + chunk.length));}} catch (err) {_didIteratorError = true;_iteratorError = err;} finally {try {if (!_iteratorNormalCompletion && _iterator['return']) {_iterator['return']();}} finally {if (_didIteratorError) {throw _iteratorError;}}}}



    /**
     * Retrieve a range of sequence data.
     * If any portions are unknown, they will be set to '.'.
     */ }, { key: 'getAsString', value: 
    function getAsString(range) {
      var seqs = this._getSequences(range.contig);
      if (!seqs) {
        return '.'.repeat(range.length());}


      var chunks = this._chunkForInterval(range.interval);
      var result = '';var _iteratorNormalCompletion2 = true;var _didIteratorError2 = false;var _iteratorError2 = undefined;try {
        for (var _iterator2 = chunks[Symbol.iterator](), _step2; !(_iteratorNormalCompletion2 = (_step2 = _iterator2.next()).done); _iteratorNormalCompletion2 = true) {var chunk = _step2.value;
          var seq = seqs[chunk.chunkStart];
          if (!seq) {
            result += '.'.repeat(chunk.length);} else 
          if (chunk.offset === 0 && chunk.length == seq.length) {
            result += seq;} else 
          {
            result += seq.slice(chunk.offset, chunk.offset + chunk.length);}}} catch (err) {_didIteratorError2 = true;_iteratorError2 = err;} finally {try {if (!_iteratorNormalCompletion2 && _iterator2['return']) {_iterator2['return']();}} finally {if (_didIteratorError2) {throw _iteratorError2;}}}


      return result;}


    /**
     * Like getAsString(), but returns a less efficient object representation:
     * {'chr1:10': 'A', 'chr1:11': 'C', ...}
     */ }, { key: 'getAsObjects', value: 
    function getAsObjects(range) {
      return _underscore2['default'].object(_underscore2['default'].map(this.getAsString(range), 
      function (base, i) {return [range.contig + ':' + (range.start() + i), 
        base == '.' ? null : base];}));}


    // Retrieve a chunk from the sequence map.
  }, { key: '_getChunk', value: function _getChunk(seqs, start) {
      return seqs[start] || '.'.repeat(CHUNK_SIZE);}


    // Split an interval into chunks which align with the store.
  }, { key: '_chunkForInterval', value: function _chunkForInterval(range) {
      var offset = range.start % CHUNK_SIZE, 
      chunkStart = range.start - offset;
      var chunks = [{ 
        chunkStart: chunkStart, 
        offset: offset, 
        length: Math.min(CHUNK_SIZE - offset, range.length()) }];

      chunkStart += CHUNK_SIZE;
      for (; chunkStart <= range.stop; chunkStart += CHUNK_SIZE) {
        chunks.push({ 
          chunkStart: chunkStart, 
          offset: 0, 
          length: Math.min(CHUNK_SIZE, range.stop - chunkStart + 1) });}


      return chunks;}


    // Set a (subset of a) chunk to the given sequence.
  }, { key: '_setChunk', value: function _setChunk(seqs, chunk, sequence) {
      // First: the easy case. Total replacement.
      if (chunk.offset === 0 && sequence.length == CHUNK_SIZE) {
        seqs[chunk.chunkStart] = sequence;
        return;}


      // We need to merge the new sequence with the old.
      var oldChunk = this._getChunk(seqs, chunk.chunkStart);
      seqs[chunk.chunkStart] = oldChunk.slice(0, chunk.offset) + 
      sequence + 
      oldChunk.slice(chunk.offset + sequence.length);}


    // Get the sequences for a contig, allowing chr- mismatches.
  }, { key: '_getSequences', value: function _getSequences(contig) {
      return this.contigMap[contig] || 
      this.contigMap[(0, _utils.altContigName)(contig)] || 
      null;} }]);return SequenceStore;})();



module.exports = SequenceStore; // contig --> start of chunk --> sequence of chunk