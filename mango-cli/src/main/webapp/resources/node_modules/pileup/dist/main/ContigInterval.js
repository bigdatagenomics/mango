'use strict';var _createClass = (function () {function defineProperties(target, props) {for (var i = 0; i < props.length; i++) {var descriptor = props[i];descriptor.enumerable = descriptor.enumerable || false;descriptor.configurable = true;if ('value' in descriptor) descriptor.writable = true;Object.defineProperty(target, descriptor.key, descriptor);}}return function (Constructor, protoProps, staticProps) {if (protoProps) defineProperties(Constructor.prototype, protoProps);if (staticProps) defineProperties(Constructor, staticProps);return Constructor;};})();function _interopRequireDefault(obj) {return obj && obj.__esModule ? obj : { 'default': obj };}function _classCallCheck(instance, Constructor) {if (!(instance instanceof Constructor)) {throw new TypeError('Cannot call a class as a function');}}var _Interval = require(


'./Interval');var _Interval2 = _interopRequireDefault(_Interval);var _utils = require(
'./utils');

/**
 * Class representing a closed interval on the genome: contig:start-stop.
 *
 * The contig may be either a string ("chr22") or a number (in case the contigs
 * are indexed, for example).
 */var 
ContigInterval = (function () {



  function ContigInterval(contig, start, stop) {_classCallCheck(this, ContigInterval);
    this.contig = contig;
    this.interval = new _Interval2['default'](start, stop);}


  // TODO: make these getter methods & switch to Babel.
  _createClass(ContigInterval, [{ key: 'start', value: function start() {
      return this.interval.start;} }, { key: 'stop', value: 

    function stop() {
      return this.interval.stop;} }, { key: 'length', value: 

    function length() {
      return this.interval.length();} }, { key: 'intersects', value: 


    function intersects(other) {
      return this.contig === other.contig && 
      this.interval.intersects(other.interval);}


    // Like intersects(), but allows 'chr17' vs. '17'-style mismatches.
  }, { key: 'chrIntersects', value: function chrIntersects(other) {
      return this.chrOnContig(other.contig) && 
      this.interval.intersects(other.interval);} }, { key: 'containsInterval', value: 


    function containsInterval(other) {
      return this.contig === other.contig && 
      this.interval.containsInterval(other.interval);} }, { key: 'isAdjacentTo', value: 


    function isAdjacentTo(other) {
      return this.contig === other.contig && (
      this.start() == 1 + other.stop() || 
      this.stop() + 1 == other.start());} }, { key: 'isCoveredBy', value: 


    function isCoveredBy(intervals) {var _this = this;
      var ivs = intervals.filter(function (iv) {return iv.contig === _this.contig;}).
      map(function (iv) {return iv.interval;});
      return this.interval.isCoveredBy(ivs);} }, { key: 'containsLocus', value: 


    function containsLocus(contig, position) {
      return this.contig === contig && 
      this.interval.contains(position);}


    // Like containsLocus, but allows 'chr17' vs '17'-style mismatches
  }, { key: 'chrContainsLocus', value: function chrContainsLocus(contig, position) {
      return this.chrOnContig(contig) && 
      this.interval.contains(position);}


    // Is this read on the given contig? (allowing for chr17 vs 17-style mismatches)
  }, { key: 'chrOnContig', value: function chrOnContig(contig) {
      return this.contig === contig || 
      this.contig === 'chr' + contig || 
      'chr' + this.contig === contig;} }, { key: 'clone', value: 


    function clone() {
      return new ContigInterval(this.contig, this.interval.start, this.interval.stop);}


    /**
     * Similar to Interval.complementIntervals, but only considers those intervals
     * on the same contig as this one.
     */ }, { key: 'complementIntervals', value: 
    function complementIntervals(intervals) {var _this2 = this;
      return this.interval.complementIntervals(
      (0, _utils.flatMap)(intervals, function (ci) {return ci.contig === _this2.contig ? [ci.interval] : [];})).
      map(function (iv) {return new ContigInterval(_this2.contig, iv.start, iv.stop);});}


    /*
    This method doesn't typecheck. See https://github.com/facebook/flow/issues/388
    isAfterInterval(other: ContigInterval): boolean {
      return (this.contig > other.contig ||
              (this.contig === other.contig && this.start() > other.start()));
    }
    */ }, { key: 'toString', value: 

    function toString() {
      return this.contig + ':' + this.start() + '-' + this.stop();} }, { key: 'toGenomeRange', value: 


    function toGenomeRange() {
      if (!(typeof this.contig === 'string' || this.contig instanceof String)) {
        throw 'Cannot convert numeric ContigInterval to GenomeRange';}

      return { 
        contig: this.contig, 
        start: this.start(), 
        stop: this.stop() };}



    // Comparator for use with Array.prototype.sort
  }], [{ key: 'compare', value: function compare(a, b) {
      if (a.contig > b.contig) {
        return -1;} else 
      if (a.contig < b.contig) {
        return +1;} else 
      {
        return a.start() - b.start();}}



    // Sort an array of intervals & coalesce adjacent/overlapping ranges.
    // NB: this may re-order the intervals parameter
  }, { key: 'coalesce', value: function coalesce(intervals) {
      intervals.sort(ContigInterval.compare);

      var rs = [];
      intervals.forEach(function (r) {
        if (rs.length === 0) {
          rs.push(r);
          return;}


        var lastR = rs[rs.length - 1];
        if (r.intersects(lastR) || r.isAdjacentTo(lastR)) {
          lastR = rs[rs.length - 1] = lastR.clone();
          lastR.interval.stop = Math.max(r.interval.stop, lastR.interval.stop);} else 
        {
          rs.push(r);}});



      return rs;} }, { key: 'fromGenomeRange', value: 


    function fromGenomeRange(range) {
      return new ContigInterval(range.contig, range.start, range.stop);} }]);return ContigInterval;})();



module.exports = ContigInterval;