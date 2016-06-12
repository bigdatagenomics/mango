/**
 * The "glue" between TwoBit.js and GenomeTrack.js.
 *
 * GenomeTrack is pure view code -- it renders data which is already in-memory
 * in the browser.
 *
 * TwoBit is purely for data parsing and fetching. It only knows how to return
 * promises for various genome features.
 *
 * This code acts as a bridge between the two. It maintains a local version of
 * the data, fetching remote data and informing the view when it becomes
 * available.
 *
 * 
 */
'use strict';Object.defineProperty(exports, '__esModule', { value: true });function _interopRequireDefault(obj) {return obj && obj.__esModule ? obj : { 'default': obj };}var _q = require(

'q');var _q2 = _interopRequireDefault(_q);var _underscore = require(
'underscore');var _underscore2 = _interopRequireDefault(_underscore);var _backbone = require(
'backbone');var _RemoteRequest = require(

'../RemoteRequest');var _RemoteRequest2 = _interopRequireDefault(_RemoteRequest);var _ContigInterval = require(
'../ContigInterval');var _ContigInterval2 = _interopRequireDefault(_ContigInterval);var _dataSequence = require(
'../data/Sequence');var _dataSequence2 = _interopRequireDefault(_dataSequence);var _SequenceStore = require(
'../SequenceStore');var _SequenceStore2 = _interopRequireDefault(_SequenceStore);var _utils = require(
'../utils');var _utils2 = _interopRequireDefault(_utils);


// Requests for 2bit ranges are expanded to begin & end at multiples of this
// constant. Doing this means that panning typically won't require
// additional network requests.
var BASE_PAIRS_PER_FETCH = 10000;

var MAX_BASE_PAIRS_TO_FETCH = 100000;


// Flow type for export.












// Expand range to begin and end on multiples of BASE_PAIRS_PER_FETCH.
function expandRange(range) {
  var roundDown = function roundDown(x) {return x - x % BASE_PAIRS_PER_FETCH;};
  var newStart = Math.max(0, roundDown(range.start())), 
  newStop = roundDown(range.stop() + BASE_PAIRS_PER_FETCH - 1);

  return new _ContigInterval2['default'](range.contig, newStart, newStop);}



var createFromReferenceUrl = function createFromReferenceUrl(remoteRequest) {
  // Local cache of genomic data.
  var contigList = [];
  var store = new _SequenceStore2['default']();

  // Ranges for which we have complete information -- no need to hit network.
  var coveredRanges = [];

  function fetch(range) {
    var span = range.length();
    if (span > MAX_BASE_PAIRS_TO_FETCH) {
      return _q2['default'].when(); // empty promise
    }

    // TODO: fetch JSON from file
    var letters = "AAAAAAA";

    store.setRange(range, letters);
    o.trigger('newdata', range);}


  function fetch(range) {
    var span = range.length();
    if (span > MAX_BASE_PAIRS_TO_FETCH) {
      return _q2['default'].when(); // empty promise
    }

    console.log('Fetching ' + span + ' base pairs');
    remoteRequest.getFeaturesInRange(range.contig, range.start(), range.stop()).
    then(function (letters) {
      if (!letters) return;
      if (letters.length < range.length()) {
        // Probably at EOF
        range = new _ContigInterval2['default'](range.contig, 
        range.start(), 
        range.start() + letters.length - 1);}

      store.setRange(range, letters);}).
    then(function () {
      o.trigger('newdata', range);}).
    done();}


  function normalizeRange(range) {
    return contigPromise.then(function () {return normalizeRangeSync(range);});}


  // Returns a {"chr12:123" -> "[ATCG]"} mapping for the range.
  function getRange(range) {
    return store.getAsObjects(_ContigInterval2['default'].fromGenomeRange(range));}


  // Returns a string of base pairs for this range.
  function getRangeAsString(range) {
    if (!range) return '';
    return store.getAsString(_ContigInterval2['default'].fromGenomeRange(range));}


  // This either adds or removes a 'chr' as needed.
  function normalizeRangeSync(range) {
    if (contigList.indexOf(range.contig) >= 0) {
      return range;}

    var altContig = _utils2['default'].altContigName(range.contig);
    if (contigList.indexOf(altContig) >= 0) {
      return { 
        contig: altContig, 
        start: range.start, 
        stop: range.stop };}


    return range; // let it fail with the original contig
  }

  // Fetch the contig list immediately.
  var contigList = remoteRequest.getContigList();
  o.trigger('contigs', contigList);

  var o = { 
    // The range here is 0-based, inclusive
    rangeChanged: function rangeChanged(newRange) {
      normalizeRange(newRange).then(function (r) {
        var range = new _ContigInterval2['default'](r.contig, r.start, r.stop);

        // Check if this interval is already in the cache.
        if (range.isCoveredBy(coveredRanges)) {
          return;}


        range = expandRange(range);
        var newRanges = range.complementIntervals(coveredRanges);
        coveredRanges.push(range);
        coveredRanges = _ContigInterval2['default'].coalesce(coveredRanges);var _iteratorNormalCompletion = true;var _didIteratorError = false;var _iteratorError = undefined;try {

          for (var _iterator = newRanges[Symbol.iterator](), _step; !(_iteratorNormalCompletion = (_step = _iterator.next()).done); _iteratorNormalCompletion = true) {var newRange = _step.value;
            fetch(newRange);}} catch (err) {_didIteratorError = true;_iteratorError = err;} finally {try {if (!_iteratorNormalCompletion && _iterator['return']) {_iterator['return']();}} finally {if (_didIteratorError) {throw _iteratorError;}}}}).

      done();}, 

    // The ranges passed to these methods are 0-based
    getRange: getRange, 
    getRangeAsString: getRangeAsString, 
    contigList: (function (_contigList) {function contigList() {return _contigList.apply(this, arguments);}contigList.toString = function () {return _contigList.toString();};return contigList;})(function () {return contigList;}), 
    normalizeRange: normalizeRange, 

    // These are here to make Flow happy.
    on: function on() {}, 
    once: function once() {}, 
    off: function off() {}, 
    trigger: function trigger() {} };

  _underscore2['default'].extend(o, _backbone.Events); // Make this an event emitter

  return o;};


function create(data) {
  var urlPrefix = data.prefix;
  var contigList = data.contigList;

  // verify data was correctly set
  if (!urlPrefix) {
    throw new Error('Missing URL from track: ' + JSON.stringify(data));}

  if (!contigList) {
    throw new Error('Missing Contig List from track: ' + JSON.stringify(data));}


  // create request with url prefix
  var remoteRequest = new _RemoteRequest2['default'](urlPrefix);
  var sequence = new _dataSequence2['default'](remoteRequest, contigList);

  return createFromReferenceUrl(sequence);}


module.exports = { 
  create: create, 
  createFromReferenceUrl: createFromReferenceUrl };