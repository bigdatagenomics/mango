'use strict';var _get = function get(_x, _x2, _x3) {var _again = true;_function: while (_again) {var object = _x, property = _x2, receiver = _x3;_again = false;if (object === null) object = Function.prototype;var desc = Object.getOwnPropertyDescriptor(object, property);if (desc === undefined) {var parent = Object.getPrototypeOf(object);if (parent === null) {return undefined;} else {_x = parent;_x2 = property;_x3 = receiver;_again = true;desc = parent = undefined;continue _function;}} else if ('value' in desc) {return desc.value;} else {var getter = desc.get;if (getter === undefined) {return undefined;}return getter.call(receiver);}}};function _interopRequireDefault(obj) {return obj && obj.__esModule ? obj : { 'default': obj };}function _classCallCheck(instance, Constructor) {if (!(instance instanceof Constructor)) {throw new TypeError('Cannot call a class as a function');}}function _inherits(subClass, superClass) {if (typeof superClass !== 'function' && superClass !== null) {throw new TypeError('Super expression must either be null or a function, not ' + typeof superClass);}subClass.prototype = Object.create(superClass && superClass.prototype, { constructor: { value: subClass, enumerable: false, writable: true, configurable: true } });if (superClass) Object.setPrototypeOf ? Object.setPrototypeOf(subClass, superClass) : subClass.__proto__ = superClass;}var _underscore = require(


'underscore');var _underscore2 = _interopRequireDefault(_underscore);var _Interval2 = require(
'../Interval');var _Interval3 = _interopRequireDefault(_Interval2);var 

CodingInterval = (function (_Interval) {_inherits(CodingInterval, _Interval);

  function CodingInterval(start, stop, isCoding) {_classCallCheck(this, CodingInterval);
    _get(Object.getPrototypeOf(CodingInterval.prototype), 'constructor', this).call(this, start, stop);
    this.isCoding = isCoding;}



  /**
   * Split exons which cross the coding/non-coding boundary into purely coding &
   * non-coding parts.
   */return CodingInterval;})(_Interval3['default']);
function splitCodingExons(exons, 
codingRegion) {
  return _underscore2['default'].flatten(exons.map(function (exon) {
    // Special case: the coding region is entirely contained by this exon.
    if (exon.containsInterval(codingRegion)) {
      // split into three parts.
      return [
      new CodingInterval(exon.start, codingRegion.start - 1, false), 
      new CodingInterval(codingRegion.start, codingRegion.stop, true), 
      new CodingInterval(codingRegion.stop + 1, exon.stop, false)].
      filter(function (interval) {return interval.start <= interval.stop;});}


    var startIsCoding = codingRegion.contains(exon.start), 
    stopIsCoding = codingRegion.contains(exon.stop);
    if (startIsCoding == stopIsCoding) {
      return [new CodingInterval(exon.start, exon.stop, startIsCoding)];} else 
    if (startIsCoding) {
      return [
      new CodingInterval(exon.start, codingRegion.stop, true), 
      new CodingInterval(codingRegion.stop + 1, exon.stop, false)];} else 

    {
      return [
      new CodingInterval(exon.start, codingRegion.start - 1, false), 
      new CodingInterval(codingRegion.start, exon.stop, true)];}}));}





module.exports = { splitCodingExons: splitCodingExons, CodingInterval: CodingInterval };