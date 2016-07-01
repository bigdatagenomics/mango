/**
 * Tools for parsing BAM files.
 * See https://samtools.github.io/hts-specs/SAMv1.pdf
 * 
 */
'use strict';var _createClass = (function () {function defineProperties(target, props) {for (var i = 0; i < props.length; i++) {var descriptor = props[i];descriptor.enumerable = descriptor.enumerable || false;descriptor.configurable = true;if ('value' in descriptor) descriptor.writable = true;Object.defineProperty(target, descriptor.key, descriptor);}}return function (Constructor, protoProps, staticProps) {if (protoProps) defineProperties(Constructor.prototype, protoProps);if (staticProps) defineProperties(Constructor, staticProps);return Constructor;};})();function _interopRequireDefault(obj) {return obj && obj.__esModule ? obj : { 'default': obj };}function _classCallCheck(instance, Constructor) {if (!(instance instanceof Constructor)) {throw new TypeError('Cannot call a class as a function');}}var _jbinary = require(




'jbinary');var _jbinary2 = _interopRequireDefault(_jbinary);var _jdataview = require(
'jdataview');var _jdataview2 = _interopRequireDefault(_jdataview);var _underscore = require(
'underscore');var _underscore2 = _interopRequireDefault(_underscore);var _q = require(
'q');var _q2 = _interopRequireDefault(_q);var _formatsBamTypes = require(

'./formats/bamTypes');var _formatsBamTypes2 = _interopRequireDefault(_formatsBamTypes);var _utils = require(
'../utils');var _utils2 = _interopRequireDefault(_utils);var _bai = require(
'./bai');var _bai2 = _interopRequireDefault(_bai);var _ContigInterval = require(
'../ContigInterval');var _ContigInterval2 = _interopRequireDefault(_ContigInterval);var _VirtualOffset = require(
'./VirtualOffset');var _VirtualOffset2 = _interopRequireDefault(_VirtualOffset);var _SamRead = require(
'./SamRead');var _SamRead2 = _interopRequireDefault(_SamRead);


/**
 * The 'contained' parameter controls whether the alignments must be fully
 * contained within the range, or need only overlap it.
 */
function isAlignmentInRange(read, 
idxRange, 
contained) {
  // TODO: Use cigar.getReferenceLength() instead of l_seq, like htsjdk. 
  var readRange = new _ContigInterval2['default'](read.refID, read.pos, read.pos + read.l_seq - 1);
  if (contained) {
    return idxRange.containsInterval(readRange);} else 
  {
    return readRange.intersects(idxRange);}}




var kMaxFetch = 65536 * 2;

// Read a single alignment
function readAlignment(view, pos, 
offset, refName) {
  var readLength = view.getInt32(pos);
  pos += 4;

  if (pos + readLength > view.byteLength) {
    return null;}


  var readSlice = view.buffer.slice(pos, pos + readLength);

  var read = new _SamRead2['default'](readSlice, offset.clone(), refName);
  return { 
    read: read, 
    readLength: 4 + readLength };}



// This tracks how many bytes were read.
function readAlignmentsToEnd(buffer, 
refName, 
idxRange, 
contained, 
offset, 
blocks, 
alignments) {
  // We use jDataView and ArrayBuffer directly for a speedup over jBinary.
  // This parses reads ~2-3x faster than using ThinAlignment directly.
  var jv = new _jdataview2['default'](buffer, 0, buffer.byteLength, true /* little endian */);
  var shouldAbort = false;
  var pos = 0;
  offset = offset.clone();
  var blockIndex = 0;
  try {
    while (pos < buffer.byteLength) {
      var readData = readAlignment(jv, pos, offset, refName);
      if (!readData) break;var 

      read = readData.read;var readLength = readData.readLength;
      pos += readLength;
      if (isAlignmentInRange(read, idxRange, contained)) {
        alignments.push(read);}


      // Advance the VirtualOffset to reflect the new position
      offset.uoffset += readLength;
      var bufLen = blocks[blockIndex].buffer.byteLength;
      if (offset.uoffset >= bufLen) {
        offset.uoffset -= bufLen;
        offset.coffset += blocks[blockIndex].compressedLength;
        blockIndex++;}


      // Optimization: if the last alignment started after the requested range,
      // then no other chunks can possibly contain matching alignments.
      // TODO: use contigInterval.isAfterInterval when that's possible.
      var range = new _ContigInterval2['default'](read.refID, read.pos, read.pos + 1);
      if (range.contig > idxRange.contig || 
      range.contig == idxRange.contig && range.start() > idxRange.stop()) {
        shouldAbort = true;
        break;}}


    // Code gets here if the compression block ended exactly at the end of
    // an Alignment.
  } catch (e) {
    // Partial record
    if (!(e instanceof RangeError)) {
      throw e;}}



  return { 
    shouldAbort: shouldAbort, 
    nextOffset: offset };}



// Fetch alignments from the remote source at the locations specified by Chunks.
// This can potentially result in many network requests.
// The returned promise is fulfilled once it can be proved that no more
// alignments need to be fetched.
function fetchAlignments(remoteFile, 
refName, 
idxRange, 
contained, 
chunks) {

  var numRequests = 0, 
  alignments = [], 
  deferred = _q2['default'].defer();

  function fetch(chunks) {
    if (chunks.length === 0) {
      deferred.resolve(alignments);
      return;}


    // Never fetch more than 128k at a time -- this reduces contention on the
    // main thread and can avoid sending unnecessary bytes over the network.
    var chunk = chunks[0], 
    chunk_beg = chunk.chunk_beg.coffset, 
    chunk_end = chunk.chunk_end.coffset;
    var bytesToFetch = Math.min(kMaxFetch, chunk_end + 65536 - chunk_beg);
    remoteFile.getBytes(chunk_beg, bytesToFetch).then(function (buffer) {
      numRequests++;
      deferred.notify({ numRequests: numRequests });
      var cacheKey = { 
        filename: remoteFile.url, 
        initialOffset: chunk_beg };

      var blocks = _utils2['default'].inflateConcatenatedGzip(buffer, chunk_end - chunk_beg, cacheKey);

      // If the chunk hasn't been exhausted, resume it at an appropriate place.
      // The last block needs to be re-read, since it may not have been exhausted.
      var lastBlock = blocks[blocks.length - 1], 
      lastByte = chunk_beg + lastBlock.offset - 1, 
      newChunk = null;
      if (blocks.length > 1 && lastByte < chunk_end) {
        newChunk = { 
          chunk_beg: new _VirtualOffset2['default'](lastByte + 1, 0), 
          chunk_end: chunk.chunk_end };}



      var buffers = blocks.map(function (x) {return x.buffer;});
      buffers[0] = buffers[0].slice(chunk.chunk_beg.uoffset);
      var decomp = _utils2['default'].concatArrayBuffers(buffers);
      if (decomp.byteLength > 0) {var _readAlignmentsToEnd = 

        readAlignmentsToEnd(decomp, refName, idxRange, contained, 
        chunk.chunk_beg, blocks, alignments);var shouldAbort = _readAlignmentsToEnd.shouldAbort;var nextOffset = _readAlignmentsToEnd.nextOffset;
        if (shouldAbort) {
          deferred.resolve(alignments);
          return;}

        if (newChunk) {
          newChunk.chunk_beg = nextOffset;}} else 

      {
        newChunk = null; // This is most likely EOF
      }

      fetch((newChunk ? [newChunk] : []).concat(_underscore2['default'].rest(chunks)));});}



  fetch(chunks);
  return deferred.promise;}var 



Bam = (function () {





  function Bam(remoteFile, 
  remoteIndexFile, 
  indexChunks) {var _this = this;_classCallCheck(this, Bam);
    this.remoteFile = remoteFile;
    this.index = remoteIndexFile ? new _bai2['default'](remoteIndexFile, indexChunks) : null;
    this.hasIndexChunks = !!indexChunks;

    var sizePromise = this.index ? this.index.getHeaderSize() : _q2['default'].when(2 * 65535);
    this.header = sizePromise.then(function (size) {
      var def = _q2['default'].defer();
      // This happens in the next event loop to give listeners a chance to register.
      _q2['default'].when().then(function () {def.notify({ status: 'Fetching BAM header' });});
      _utils2['default'].pipePromise(
      def, 
      _this.remoteFile.getBytes(0, size).then(function (buf) {
        var decomp = _utils2['default'].inflateGzip(buf);
        var jb = new _jbinary2['default'](decomp, _formatsBamTypes2['default'].TYPE_SET);
        return jb.read('BamHeader');}));

      return def.promise;});

    this.header.done();}


  /**
   * Reads the entire BAM file from the remote source and parses it.
   * Since BAM files can be enormous (hundreds of GB), this is only recommended
   * for small test inputs.
   */_createClass(Bam, [{ key: 'readAll', value: 
    function readAll() {
      return this.remoteFile.getAll().then(function (buf) {
        var decomp = _utils2['default'].inflateGzip(buf);
        var jb = new _jbinary2['default'](decomp, _formatsBamTypes2['default'].TYPE_SET);
        var o = jb.read('BamFile');
        // Do some mild re-shaping.
        var vo = new _VirtualOffset2['default'](0, 0);
        var slice = function slice(u8) {
          return u8.buffer.slice(u8.byteOffset, u8.byteOffset + u8.byteLength - 1);};

        o.alignments = o.alignments.map(function (x) {
          var r = new _SamRead2['default'](slice(x.contents), vo, '');
          if (r.refID != -1) {
            r.ref = o.header.references[r.refID].name;}

          return r;});

        return o;});}



    /**
     * Fetch a single read at the given VirtualOffset.
     * This is insanely inefficient and should not be used outside of testing.
     */ }, { key: 'readAtOffset', value: 
    function readAtOffset(offset) {var _this2 = this;
      return this.remoteFile.getBytes(offset.coffset, kMaxFetch).then(function (gzip) {
        var buf = _utils2['default'].inflateGzip(gzip);
        var jv = new _jdataview2['default'](buf, 0, buf.byteLength, true /* little endian */);
        var readData = readAlignment(jv, offset.uoffset, offset, '');
        if (!readData) {
          throw 'Unable to read alignment at ' + offset + ' in ' + _this2.remoteFile.url;} else 
        {
          // Attach the human-readable ref name
          var read = readData.read;
          return _this2.header.then(function (header) {
            read.ref = header.references[read.refID].name;
            return read;});}});}





    /**
     * Map a contig name to a contig index and canonical name.
     */ }, { key: 'getContigIndex', value: 
    function getContigIndex(contigName) {
      return this.header.then(function (header) {
        for (var i = 0; i < header.references.length; i++) {
          var name = header.references[i].name;
          if (name == contigName || 
          name == 'chr' + contigName || 
          'chr' + name == contigName) {
            return { idx: i, name: name };}}


        throw 'Invalid contig name: ' + contigName;});}



    /**
     * Fetch all the alignments which overlap a range.
     * The 'contained' parameter controls whether the alignments must be fully
     * contained within the range, or need only overlap it.
     */ }, { key: 'getAlignmentsInRange', value: 
    function getAlignmentsInRange(range, opt_contained) {var _this3 = this;
      var contained = opt_contained || false;
      if (!this.index) {
        throw 'Range searches are only supported on BAMs with BAI indices.';}

      var index = this.index;

      return this.getContigIndex(range.contig).then(function (_ref) {var idx = _ref.idx;var name = _ref.name;
        var def = _q2['default'].defer();
        // This happens in the next event loop to give listeners a chance to register.
        _q2['default'].when().then(function () {def.notify({ status: 'Fetching BAM index' });});

        var idxRange = new _ContigInterval2['default'](idx, range.start(), range.stop());

        _utils2['default'].pipePromise(
        def, 
        index.getChunksForInterval(idxRange).then(function (chunks) {
          return fetchAlignments(_this3.remoteFile, name, idxRange, contained, chunks);}));

        return def.promise;});} }]);return Bam;})();





module.exports = Bam;