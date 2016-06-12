// Genome ranges are rounded to multiples of this for fetching.













// This reduces network activity while fetching.
// TODO: tune this value
'use strict';function _interopRequireDefault(obj) {return obj && obj.__esModule ? obj : { 'default': obj };}var _underscore = require('underscore');var _underscore2 = _interopRequireDefault(_underscore);var _q = require('q');var _q2 = _interopRequireDefault(_q);var _backbone = require('backbone');var _ContigInterval = require('../ContigInterval');var _ContigInterval2 = _interopRequireDefault(_ContigInterval);var _dataBam = require('../data/bam');var _dataBam2 = _interopRequireDefault(_dataBam);var _RemoteFile = require('../RemoteFile');var _RemoteFile2 = _interopRequireDefault(_RemoteFile);var BASE_PAIRS_PER_FETCH = 100;

function expandRange(range) {
  var roundDown = function roundDown(x) {return x - x % BASE_PAIRS_PER_FETCH;};
  var newStart = Math.max(1, roundDown(range.start())), 
  newStop = roundDown(range.stop() + BASE_PAIRS_PER_FETCH - 1);

  return new _ContigInterval2['default'](range.contig, newStart, newStop);}



function createFromBamFile(remoteSource) {
  var reads = {};

  // Mapping from contig name to canonical contig name.
  var contigNames = {};

  // Ranges for which we have complete information -- no need to hit network.
  var coveredRanges = [];

  function addRead(read) {
    var key = read.getKey();
    if (!reads[key]) {
      reads[key] = read;}}



  function saveContigMapping(header) {
    header.references.forEach(function (ref) {
      var name = ref.name;
      contigNames[name] = name;
      contigNames['chr' + name] = name;
      if (name.slice(0, 3) == 'chr') {
        contigNames[name.slice(3)] = name;}});}




  function fetch(range) {
    var refsPromise = !_underscore2['default'].isEmpty(contigNames) ? _q2['default'].when() : 
    remoteSource.header.then(saveContigMapping);

    // For BAMs without index chunks, we need to fetch the entire BAI file
    // before we can know how large the BAM header is. If the header is
    // pending, it's almost certainly because the BAI file is in flight.
    _q2['default'].when().then(function () {
      if (refsPromise.isPending() && !remoteSource.hasIndexChunks) {
        o.trigger('networkprogress', { 
          status: 'Fetching BAM index -- use index chunks to speed this up' });}}).


    done();

    return refsPromise.then(function () {
      var contigName = contigNames[range.contig];
      var interval = new _ContigInterval2['default'](contigName, range.start, range.stop);

      // Check if this interval is already in the cache.
      // If not, immediately "cover" it to prevent duplicate requests.
      if (interval.isCoveredBy(coveredRanges)) {
        return _q2['default'].when();}


      interval = expandRange(interval);
      var newRanges = interval.complementIntervals(coveredRanges);
      coveredRanges.push(interval);
      coveredRanges = _ContigInterval2['default'].coalesce(coveredRanges);

      return _q2['default'].all(newRanges.map(function (range) {return (
          remoteSource.getAlignmentsInRange(range).
          progress(function (progressEvent) {
            o.trigger('networkprogress', progressEvent);}).

          then(function (reads) {
            reads.forEach(function (read) {return addRead(read);});
            o.trigger('networkdone');
            o.trigger('newdata', range);}));}));});}




  function getAlignmentsInRange(range) {
    if (!range) return [];
    if (_underscore2['default'].isEmpty(contigNames)) return [];

    var canonicalRange = new _ContigInterval2['default'](contigNames[range.contig], 
    range.start(), range.stop());

    return _underscore2['default'].filter(reads, function (read) {return read.intersects(canonicalRange);});}


  var o = { 
    rangeChanged: function rangeChanged(newRange) {
      fetch(newRange).done();}, 

    getAlignmentsInRange: getAlignmentsInRange, 

    // These are here to make Flow happy.
    on: function on() {}, 
    once: function once() {}, 
    off: function off() {}, 
    trigger: function trigger() {} };

  _underscore2['default'].extend(o, _backbone.Events); // Make this an event emitter

  return o;}








function create(spec) {
  var url = spec.url;
  if (!url) {
    throw new Error('Missing URL from track data: ' + JSON.stringify(spec));}

  var indexUrl = spec.indexUrl;
  if (!indexUrl) {
    throw new Error('Missing indexURL from track data: ' + JSON.stringify(spec));}


  // TODO: this is overly repetitive, see flow issue facebook/flow#437
  var bamFile = spec.indexChunks ? 
  new _dataBam2['default'](new _RemoteFile2['default'](url), new _RemoteFile2['default'](indexUrl), spec.indexChunks) : 
  new _dataBam2['default'](new _RemoteFile2['default'](url), new _RemoteFile2['default'](indexUrl));
  return createFromBamFile(bamFile);}


module.exports = { 
  create: create, 
  createFromBamFile: createFromBamFile };