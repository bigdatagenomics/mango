'use strict';function _interopRequireDefault(obj) {return obj && obj.__esModule ? obj : { 'default': obj };}var _chai = require(


'chai');var _jbinary = require(

'jbinary');var _jbinary2 = _interopRequireDefault(_jbinary);var _mainRemoteFile = require(

'../main/RemoteFile');var _mainRemoteFile2 = _interopRequireDefault(_mainRemoteFile);

describe('RemoteFile', function () {
  function bufferToText(buf) {
    return new _jbinary2['default'](buf).read('string');}


  it('should fetch a subset of a file', function () {
    var f = new _mainRemoteFile2['default']('/test-data/0to9.txt');
    var promisedData = f.getBytes(4, 5);

    (0, _chai.expect)(f.numNetworkRequests).to.equal(1);
    return promisedData.then(function (buf) {
      (0, _chai.expect)(buf.byteLength).to.equal(5);
      (0, _chai.expect)(bufferToText(buf)).to.equal('45678');});});



  it('should fetch subsets from cache', function () {
    var f = new _mainRemoteFile2['default']('/test-data/0to9.txt');
    return f.getBytes(0, 10).then(function (buf) {
      (0, _chai.expect)(buf.byteLength).to.equal(10);
      (0, _chai.expect)(bufferToText(buf)).to.equal('0123456789');
      (0, _chai.expect)(f.numNetworkRequests).to.equal(1);
      return f.getBytes(4, 5).then(function (buf) {
        (0, _chai.expect)(buf.byteLength).to.equal(5);
        (0, _chai.expect)(bufferToText(buf)).to.equal('45678');
        (0, _chai.expect)(f.numNetworkRequests).to.equal(1); // it was cached
      });});});



  it('should fetch entire files', function () {
    var f = new _mainRemoteFile2['default']('/test-data/0to9.txt');
    return f.getAll().then(function (buf) {
      (0, _chai.expect)(buf.byteLength).to.equal(11);
      (0, _chai.expect)(bufferToText(buf)).to.equal('0123456789\n');
      (0, _chai.expect)(f.numNetworkRequests).to.equal(1);});});



  it('should determine file lengths', function () {
    var f = new _mainRemoteFile2['default']('/test-data/0to9.txt');
    return f.getSize().then(function (size) {
      (0, _chai.expect)(size).to.equal(11);
      // TODO: make sure this was a HEAD request
      (0, _chai.expect)(f.numNetworkRequests).to.equal(1);});});



  it('should get file lengths from full requests', function () {
    var f = new _mainRemoteFile2['default']('/test-data/0to9.txt');
    return f.getAll().then(function (buf) {
      (0, _chai.expect)(f.numNetworkRequests).to.equal(1);
      return f.getSize().then(function (size) {
        (0, _chai.expect)(size).to.equal(11);
        (0, _chai.expect)(f.numNetworkRequests).to.equal(1); // no additional requests
      });});});



  it('should get file lengths from range requests', function () {
    var f = new _mainRemoteFile2['default']('/test-data/0to9.txt');
    return f.getBytes(4, 5).then(function (buf) {
      (0, _chai.expect)(f.numNetworkRequests).to.equal(1);
      return f.getSize().then(function (size) {
        (0, _chai.expect)(size).to.equal(11);
        (0, _chai.expect)(f.numNetworkRequests).to.equal(1); // no additional requests
      });});});



  it('should cache requests for full files', function () {
    var f = new _mainRemoteFile2['default']('/test-data/0to9.txt');
    f.getAll().then(function (buf) {
      (0, _chai.expect)(buf.byteLength).to.equal(11);
      (0, _chai.expect)(bufferToText(buf)).to.equal('0123456789\n');
      (0, _chai.expect)(f.numNetworkRequests).to.equal(1);
      return f.getAll().then(function (buf) {
        (0, _chai.expect)(buf.byteLength).to.equal(11);
        (0, _chai.expect)(bufferToText(buf)).to.equal('0123456789\n');
        (0, _chai.expect)(f.numNetworkRequests).to.equal(1); // still 1
      });});});



  it('should serve range requests from cache after getAll', function () {
    var f = new _mainRemoteFile2['default']('/test-data/0to9.txt');
    return f.getAll().then(function (buf) {
      (0, _chai.expect)(buf.byteLength).to.equal(11);
      (0, _chai.expect)(bufferToText(buf)).to.equal('0123456789\n');
      (0, _chai.expect)(f.numNetworkRequests).to.equal(1);
      return f.getBytes(4, 5).then(function (buf) {
        (0, _chai.expect)(buf.byteLength).to.equal(5);
        (0, _chai.expect)(bufferToText(buf)).to.equal('45678');
        (0, _chai.expect)(f.numNetworkRequests).to.equal(1); // still 1
      });});});



  it('should reject requests to a non-existent file', function () {
    var f = new _mainRemoteFile2['default']('/test-data/nonexistent-file.txt');
    return f.getAll().then(function (buf) {
      throw 'Requests for non-existent files should not succeed';}, 
    function (err) {
      // The majority of the browsers will return 404
      // and a minority (like PhantomJS) will fail fast
      // (more information: https://github.com/ariya/phantomjs/issues/11195)
      (0, _chai.expect)(err).to.match(/404|^Request.*failed/);
      (0, _chai.expect)(err).to.match(/nonexistent/);});});



  it('should truncate requests past EOF', function () {
    var f = new _mainRemoteFile2['default']('/test-data/0to9.txt');
    var promisedData = f.getBytes(4, 100);

    return promisedData.then(function (buf) {
      (0, _chai.expect)(buf.byteLength).to.equal(7);
      (0, _chai.expect)(bufferToText(buf)).to.equal('456789\n');
      (0, _chai.expect)(f.numNetworkRequests).to.equal(1);
      return f.getBytes(6, 90).then(function (buf) {
        (0, _chai.expect)(buf.byteLength).to.equal(5);
        (0, _chai.expect)(bufferToText(buf)).to.equal('6789\n');
        (0, _chai.expect)(f.numNetworkRequests).to.equal(1);});});});




  it('should fetch entire files as strings', function () {
    var f = new _mainRemoteFile2['default']('/test-data/0to9.txt');
    return f.getAllString().then(function (txt) {
      (0, _chai.expect)(txt).to.equal('0123456789\n');});});});