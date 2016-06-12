'use strict';function _interopRequireDefault(obj) {return obj && obj.__esModule ? obj : { 'default': obj };}var _chai = require(


'chai');var _jbinary = require(

'jbinary');var _jbinary2 = _interopRequireDefault(_jbinary);var _q = require(
'q');var _q2 = _interopRequireDefault(_q);var _MappedRemoteFile = require(

'./MappedRemoteFile');var _MappedRemoteFile2 = _interopRequireDefault(_MappedRemoteFile);

describe('MappedRemoteFile', function () {
  function bufferToText(buf) {
    return new _jbinary2['default'](buf).read('string');}


  it('should serve requests through the map', function () {
    var remoteFile = new _MappedRemoteFile2['default']('/test-data/0to9.txt', [
    [0, 2], // 0,1,2
    [12345678, 12345680], // 3,4,5
    [9876543210, 9876543214] // 6,7,8,9,\n
    ]);

    var promises = [
    remoteFile.getBytes(0, 3).then(function (buf) {
      (0, _chai.expect)(bufferToText(buf)).to.equal('012');}), 


    remoteFile.getBytes(12345678, 2).then(function (buf) {
      (0, _chai.expect)(bufferToText(buf)).to.equal('34');}), 


    remoteFile.getBytes(9876543211, 3).then(function (buf) {
      (0, _chai.expect)(bufferToText(buf)).to.equal('789');}), 


    remoteFile.getBytes(9876543211, 10).then(function (buf) {
      throw 'Requests for unmapped ranges should fail';}, 
    function (err) {
      (0, _chai.expect)(err).to.match(/is not mapped/);}), 


    remoteFile.getBytes(23456789, 1).then(function (buf) {
      throw 'Requests for unmapped ranges should fail';}, 
    function (err) {
      (0, _chai.expect)(err).to.match(/is not mapped/);})];



    return _q2['default'].all(promises);});


  it('should forget file length', function () {
    var remoteFile = new _MappedRemoteFile2['default']('/test-data/0to9.txt', [
    [0, 2], // 0,1,2
    [12345673, 12345690] // 3456789\n
    ]);

    return remoteFile.getBytes(0, 3).then(function (buf) {
      (0, _chai.expect)(bufferToText(buf)).to.equal('012');
      // This second read would fail if the file remembered its length.
      return remoteFile.getBytes(12345673, 8).then(function (buf) {
        (0, _chai.expect)(bufferToText(buf)).to.equal('3456789\n');});});});});