/**
 * Data management for PileupTrack.
 *
 * This class groups paired reads and piles them up.
 *
 * 
 */
'use strict';











// This bundles everything intrinsic to the alignment that we need to display
// it, i.e. everything not dependend on scale/viewport.








// This is typically a read pair, but may be a single read in some situations.
Object.defineProperty(exports, '__esModule', { value: true });var _createClass = (function () {function defineProperties(target, props) {for (var i = 0; i < props.length; i++) {var descriptor = props[i];descriptor.enumerable = descriptor.enumerable || false;descriptor.configurable = true;if ('value' in descriptor) descriptor.writable = true;Object.defineProperty(target, descriptor.key, descriptor);}}return function (Constructor, protoProps, staticProps) {if (protoProps) defineProperties(Constructor.prototype, protoProps);if (staticProps) defineProperties(Constructor, staticProps);return Constructor;};})();function _interopRequireDefault(obj) {return obj && obj.__esModule ? obj : { 'default': obj };}function _classCallCheck(instance, Constructor) {if (!(instance instanceof Constructor)) {throw new TypeError('Cannot call a class as a function');}}var _underscore = require('underscore');var _underscore2 = _interopRequireDefault(_underscore);var _ContigInterval = require('../ContigInterval');var _ContigInterval2 = _interopRequireDefault(_ContigInterval);var _Interval = require('../Interval');var _Interval2 = _interopRequireDefault(_Interval);var _pileuputils = require('./pileuputils');var _utils = require('../utils');var _utils2 = _interopRequireDefault(_utils);







// Insert sizes within this percentile range will be considered "normal".
var MIN_OUTLIER_PERCENTILE = 0.5;
var MAX_OUTLIER_PERCENTILE = 99.5;
var MAX_INSERT_SIZE = 30000; // ignore inserts larger than this in calculations
var MIN_READS_FOR_OUTLIERS = 500; // minimum reads for reliable stats






// This class provides data management for the visualization, grouping paired
// reads and managing the pileup.
var PileupCache = (function () {







  function PileupCache(referenceSource, viewAsPairs) {_classCallCheck(this, PileupCache);
    this.groups = {};
    this.refToPileup = {};
    this.referenceSource = referenceSource;
    this.viewAsPairs = viewAsPairs;
    this._insertStats = null;}

























































































































































































  // Helper method for addRead.
  // Given 1-2 intervals, compute their span and insert (interval between them).
  // For one interval, these are both trivial.
  // TODO: what this calls an "insert" is not what most people mean by that.
  // read name would make a good key, but paired reads from different contigs
  // shouldn't be grouped visually. Hence we use read name + contig.
  _createClass(PileupCache, [{ key: 'groupKey', value: function groupKey(read) {if (this.viewAsPairs) {return read.name + ':' + read.ref;} else {return read.getKey();}} // Load a new read into the visualization cache.
    // Calling this multiple times with the same read is a no-op.
  }, { key: 'addAlignment', value: function addAlignment(read) {this._insertStats = null; // invalidate
      var key = this.groupKey(read), range = read.getInterval();if (!(key in this.groups)) {this.groups[key] = { key: key, row: -1, // TBD
          insert: null, // TBD
          span: range, alignments: [] };}var group = this.groups[key];if (_underscore2['default'].find(group.alignments, function (a) {return a.read == read;})) {return; // we've already got it.
      }var opInfo = (0, _pileuputils.getOpInfo)(read, this.referenceSource);var visualAlignment = { read: read, strand: read.getStrand(), refLength: range.length(), ops: opInfo.ops, mismatches: opInfo.mismatches };group.alignments.push(visualAlignment);var mateInterval = null;if (group.alignments.length == 1) {// This is the first read in the group. Infer its span from its mate properties.
        // TODO: if the mate Alignment is also available, it would be better to use that.
        if (this.viewAsPairs) {var mateProps = read.getMateProperties();var intervals = [range];if (mateProps && mateProps.ref && mateProps.ref == read.ref) {mateInterval = new _ContigInterval2['default'](mateProps.ref, mateProps.pos, mateProps.pos + range.length());intervals.push(mateInterval);}group = _underscore2['default'].extend(group, spanAndInsert(intervals));} else {group.span = range;}if (!(read.ref in this.refToPileup)) {this.refToPileup[read.ref] = [];}var pileup = this.refToPileup[read.ref];group.row = (0, _pileuputils.addToPileup)(group.span.interval, pileup);} else if (group.alignments.length == 2) {// Refine the connector
        mateInterval = group.alignments[0].read.getInterval();var _spanAndInsert = spanAndInsert([range, mateInterval]);var span = _spanAndInsert.span;var insert = _spanAndInsert.insert;group.insert = insert;if (insert) {group.span = span;}} else {// this must be a chimeric read.
      }} // Updates reference mismatch information for previously-loaded reads.
  }, { key: 'updateMismatches', value: function updateMismatches(range) {for (var k in this.groups) {var reads = this.groups[k].alignments;var _iteratorNormalCompletion = true;var _didIteratorError = false;var _iteratorError = undefined;try {for (var _iterator = reads[Symbol.iterator](), _step; !(_iteratorNormalCompletion = (_step = _iterator.next()).done); _iteratorNormalCompletion = true) {var vRead = _step.value;var read = vRead.read;if (read.getInterval().chrIntersects(range)) {var opInfo = (0, _pileuputils.getOpInfo)(read, this.referenceSource);vRead.mismatches = opInfo.mismatches;}}} catch (err) {_didIteratorError = true;_iteratorError = err;} finally {try {if (!_iteratorNormalCompletion && _iterator['return']) {_iterator['return']();}} finally {if (_didIteratorError) {throw _iteratorError;}}}}} }, { key: 'pileupForRef', value: function pileupForRef(ref) {if (ref in this.refToPileup) {return this.refToPileup[ref];} else {var alt = _utils2['default'].altContigName(ref);if (alt in this.refToPileup) {return this.refToPileup[alt];} else {return [];}}} // How many rows tall is the pileup for a given ref? This is related to the
    // maximum read depth. This is 'chr'-agnostic.
  }, { key: 'pileupHeightForRef', value: function pileupHeightForRef(ref) {var pileup = this.pileupForRef(ref);return pileup ? pileup.length : 0;} // Find groups overlapping the range. This is 'chr'-agnostic.
  }, { key: 'getGroupsOverlapping', value: function getGroupsOverlapping(range) {// TODO: speed this up using an interval tree
      return _underscore2['default'].filter(this.groups, function (group) {return group.span.chrIntersects(range);});} // Determine the number of groups at a locus.
    // Like getGroupsOverlapping(range).length > 0, but more efficient.
  }, { key: 'anyGroupsOverlapping', value: function anyGroupsOverlapping(range) {for (var k in this.groups) {var group = this.groups[k];if (group.span.chrIntersects(range)) {return true;}}return false;} // Re-sort the pileup so that reads overlapping the locus are on top.
  }, { key: 'sortReadsAt', value: function sortReadsAt(contig, position) {// Strategy: For each pileup row, determine whether it overlaps the locus.
      // Then sort the array indices to get a permutation.
      // Build a new pileup by permuting the old pileup
      // Update all the `row` properties of the relevant visual groups
      var pileup = this.pileupForRef(contig); // Find the groups for which an alignment overlaps the locus.
      var groups = _underscore2['default'].filter(this.groups, function (group) {return _underscore2['default'].any(group.alignments, function (a) {return a.read.getInterval().chrContainsLocus(contig, position);});}); // For each row, find the left-most point (for sorting).
      var rowsOverlapping = _underscore2['default'].mapObject(_underscore2['default'].groupBy(groups, function (g) {return g.row;}), function (gs) {return _underscore2['default'].min(gs, function (g) {return g.span.start();}).span.start();}); // Sort groups by whether they overlap, then by their start.
      // TODO: is there an easier way to construct the forward map directly?
      var permutation = _underscore2['default'].sortBy(_underscore2['default'].range(0, pileup.length), function (idx) {return rowsOverlapping[idx] || Infinity;});var oldToNew = [];permutation.forEach(function (oldIndex, newIndex) {oldToNew[oldIndex] = newIndex;});var newPileup = _underscore2['default'].range(0, pileup.length).map(function (i) {return pileup[oldToNew[i]];});var normRef = contig in this.refToPileup ? contig : _utils2['default'].altContigName(contig);this.refToPileup[normRef] = newPileup;_underscore2['default'].each(this.groups, function (g) {if (g.span.chrOnContig(contig)) {g.row = oldToNew[g.row];}});} }, { key: 'getInsertStats', value: function getInsertStats() {if (this._insertStats) return this._insertStats;var inserts = _underscore2['default'].map(this.groups, function (g) {return g.alignments[0].read.getInferredInsertSize();}).filter(function (x) {return x < MAX_INSERT_SIZE;});var insertStats = inserts.length >= MIN_READS_FOR_OUTLIERS ? { minOutlierSize: _utils2['default'].computePercentile(inserts, MIN_OUTLIER_PERCENTILE), maxOutlierSize: _utils2['default'].computePercentile(inserts, MAX_OUTLIER_PERCENTILE) } : { minOutlierSize: 0, maxOutlierSize: Infinity };this._insertStats = insertStats;return insertStats;} }]);return PileupCache;})();function spanAndInsert(_x) {var _again = true;_function: while (_again) {var intervals = _x;_again = false;if (intervals.length == 1) {return { insert: null, span: intervals[0] };} else if (intervals.length != 2) {throw 'Called spanAndInsert with ' + intervals.length + ' \notin [1, 2]';}if (!intervals[0].chrOnContig(intervals[1].contig)) {_x = [intervals[0]];_again = true;continue _function;}var iv1 = intervals[0].interval, iv2 = intervals[1].interval, insert = iv1.start < iv2.start ? new _Interval2['default'](iv1.stop, iv2.start) : new _Interval2['default'](iv2.stop, iv1.start);var span = _Interval2['default'].boundingInterval([iv1, iv2]);return { insert: insert, span: new _ContigInterval2['default'](intervals[0].contig, span.start, span.stop) };}}module.exports = PileupCache; // span on the reference (accounting for indels)
// pileup row.
// tip-to-tip span for the read group
// interval for the connector, if applicable.
// maps groupKey to VisualGroup