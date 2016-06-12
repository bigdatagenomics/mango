/**
 * A simple implementation of Alignment which doesn't require network accesses.
 * 
 */
'use strict';var _createClass = (function () {function defineProperties(target, props) {for (var i = 0; i < props.length; i++) {var descriptor = props[i];descriptor.enumerable = descriptor.enumerable || false;descriptor.configurable = true;if ('value' in descriptor) descriptor.writable = true;Object.defineProperty(target, descriptor.key, descriptor);}}return function (Constructor, protoProps, staticProps) {if (protoProps) defineProperties(Constructor.prototype, protoProps);if (staticProps) defineProperties(Constructor, staticProps);return Constructor;};})();function _classCallCheck(instance, Constructor) {if (!(instance instanceof Constructor)) {throw new TypeError('Cannot call a class as a function');}}




var numAlignments = 1;var 
FakeAlignment /* implements Alignment */ = (function () {









  function FakeAlignment(interval, name, strand, mateProps) {_classCallCheck(this, FakeAlignment);
    this.interval = interval;
    this.ref = interval.contig;
    this.pos = interval.start();
    this.name = name;
    this.strand = strand;
    this.key = 'align:' + numAlignments++;
    this.mateProps = mateProps;
    this.cigarOps = [];}_createClass(FakeAlignment, [{ key: 'getKey', value: 


    function getKey() {return this.key;} }, { key: 'getStrand', value: 
    function getStrand() {return this.strand;} }, { key: 'getQualityScores', value: 
    function getQualityScores() {return [];} }, { key: 'getSequence', value: 
    function getSequence() {return '';} }, { key: 'getInterval', value: 
    function getInterval() {return this.interval;} }, { key: 'getReferenceLength', value: 
    function getReferenceLength() {return this.interval.length();} }, { key: 'getMateProperties', value: 
    function getMateProperties() {return this.mateProps;} }, { key: 'intersects', value: 

    function intersects(interval) {
      return interval.intersects(this.getInterval());} }, { key: 'getInferredInsertSize', value: 

    function getInferredInsertSize() {return 0;} }]);return FakeAlignment;})();


var nameCounter = 1;
function makeReadPair(range1, range2) {
  var name = 'group:' + nameCounter++;
  return [
  new FakeAlignment(range1, name, '+', { ref: range2.contig, pos: range2.start(), strand: '-' }), 
  new FakeAlignment(range2, name, '-', { ref: range1.contig, pos: range1.start(), strand: '+' })];}



function makeRead(range, strand) {
  var name = 'read:' + nameCounter++;
  return new FakeAlignment(range, name, strand);}



function dieFn() {throw 'Should not have called this.';}
var fakeSource = { 
  rangeChanged: dieFn, 
  getRange: function getRange() {return {};}, 
  getRangeAsString: function getRangeAsString() {return '';}, 
  contigList: function contigList() {return [];}, 
  normalizeRange: function normalizeRange() {}, 
  on: dieFn, 
  off: dieFn, 
  once: dieFn, 
  trigger: dieFn };



module.exports = { 
  FakeAlignment: FakeAlignment, 
  makeRead: makeRead, 
  makeReadPair: makeReadPair, 
  fakeSource: fakeSource };