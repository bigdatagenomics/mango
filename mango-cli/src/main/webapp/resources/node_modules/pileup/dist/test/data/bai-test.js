'use strict';function _interopRequireDefault(obj) {return obj && obj.__esModule ? obj : { 'default': obj };}var _chai = require(


'chai');var _jbinary = require(

'jbinary');var _jbinary2 = _interopRequireDefault(_jbinary);var _mainDataBai = require(

'../../main/data/bai');var _mainDataBai2 = _interopRequireDefault(_mainDataBai);var _mainDataFormatsBamTypes = require(
'../../main/data/formats/bamTypes');var _mainDataFormatsBamTypes2 = _interopRequireDefault(_mainDataFormatsBamTypes);var _mainContigInterval = require(
'../../main/ContigInterval');var _mainContigInterval2 = _interopRequireDefault(_mainContigInterval);var _mainRemoteFile = require(
'../../main/RemoteFile');var _mainRemoteFile2 = _interopRequireDefault(_mainRemoteFile);var _RecordedRemoteFile = require(
'../RecordedRemoteFile');var _RecordedRemoteFile2 = _interopRequireDefault(_RecordedRemoteFile);

function chunkToString(chunk) {
  return chunk.chunk_beg + '-' + chunk.chunk_end;}


describe('BAI', function () {
  it('should parse virtual offsets', function () {
    var u8 = new Uint8Array([201, 121, 79, 100, 96, 92, 1, 0]);
    var vo = new _jbinary2['default'](u8, _mainDataFormatsBamTypes2['default'].TYPE_SET).read('VirtualOffset');
    // (expected values from dalliance)
    (0, _chai.expect)(vo.uoffset).to.equal(31177);
    (0, _chai.expect)(vo.coffset).to.equal(5844788303);});


  it('should parse virtual offsets near 2^32', function () {
    // The low 32 bits of these virtual offsets are in [2^31, 2^32], which
    // could cause sign propagation bugs with incorrect implementations.
    var u8 = new Uint8Array([218, 128, 112, 239, 7, 0, 0, 0]);
    var vo = new _jbinary2['default'](u8, _mainDataFormatsBamTypes2['default'].TYPE_SET).read('VirtualOffset');
    (0, _chai.expect)(vo.toString()).to.equal('520048:32986');

    u8 = new Uint8Array([230, 129, 112, 239, 7, 0, 0, 0]);
    vo = new _jbinary2['default'](u8, _mainDataFormatsBamTypes2['default'].TYPE_SET).read('VirtualOffset');
    (0, _chai.expect)(vo.toString()).to.equal('520048:33254');});


  // This matches htsjdk's BamFileIndexTest.testSpecificQueries
  it('should parse large BAI files', function () {
    var bai = new _mainDataBai2['default'](new _mainRemoteFile2['default']('/test-data/index_test.bam.bai'));

    // contig 0 = chrM
    var range = new _mainContigInterval2['default'](0, 10400, 10600);
    return bai.getChunksForInterval(range).then(function (chunks) {
      (0, _chai.expect)(chunks).to.have.length(1);
      (0, _chai.expect)(chunkToString(chunks[0])).to.equal('0:8384-0:11328');});});



  it('should use index chunks', function () {
    var remoteFile = new _RecordedRemoteFile2['default']('/test-data/index_test.bam.bai');
    var bai = new _mainDataBai2['default'](remoteFile, 
    { 
      'chunks': [[8, 144], [144, 13776]], 
      'minBlockIndex': 65536 });


    // contig 0 = chrM
    var range = new _mainContigInterval2['default'](0, 10400, 10600);
    return bai.getChunksForInterval(range).then(function (chunks) {
      (0, _chai.expect)(chunks).to.have.length(1);
      (0, _chai.expect)(chunkToString(chunks[0])).to.equal('0:8384-0:11328');

      var requests = remoteFile.requests;
      (0, _chai.expect)(requests).to.have.length(1);
      (0, _chai.expect)(requests[0].toString()).to.equal('[8, 144]');});});



  it('should compute index chunks', function () {
    var bai = new _mainDataBai2['default'](new _mainRemoteFile2['default']('/test-data/index_test.bam.bai'));
    return bai.immediate.then(function (imm) {
      var chunks = imm.indexChunks;

      // This is the output from bai-indexer
      (0, _chai.expect)(chunks).to.deep.equal({ 
        "chunks": [
        [8, 144], 
        [144, 131776], 
        [131776, 260416], 
        [260416, 366024], 
        [366024, 467112], 
        [467112, 562584], 
        [562584, 653312], 
        [653312, 737240], 
        [737240, 814944], 
        [814944, 887920], 
        [887920, 959912], 
        [959912, 1031312], 
        [1031312, 1101520], 
        [1101520, 1161136], 
        [1161136, 1216816], 
        [1216816, 1269104], 
        [1269104, 1315856], 
        [1315856, 1357584], 
        [1357584, 1397960], 
        [1397960, 1431392], 
        [1431392, 1464656], 
        [1464656, 1489016], 
        [1489016, 1514576], 
        [1514576, 1592728], 
        [1592728, 1621248], 
        [1621248, 1621256], 
        [1621256, 1621264], 
        [1621264, 1621272], 
        [1621272, 1621280], 
        [1621280, 1621288], 
        [1621288, 1621296], 
        [1621296, 1621304], 
        [1621304, 1621312], 
        [1621312, 1621320], 
        [1621320, 1621328], 
        [1621328, 1621336], 
        [1621336, 1621344], 
        [1621344, 1621352], 
        [1621352, 1621360], 
        [1621360, 1621368], 
        [1621368, 1621376], 
        [1621376, 1621384], 
        [1621384, 1621392], 
        [1621392, 1621400], 
        [1621400, 1621408]], 

        "minBlockIndex": 65536 });});});




  it('should index a small BAI file', function () {
    var bai = new _mainDataBai2['default'](new _mainRemoteFile2['default']('/test-data/test_input_1_b.bam.bai'));
    return bai.immediate.then(function (imm) {
      var chunks = imm.indexChunks;

      // This is the output from bai-indexer
      (0, _chai.expect)(chunks).to.deep.equal({ 
        "chunks": [
        [8, 16], 
        [16, 96], 
        [96, 176], 
        [176, 184]], 

        "minBlockIndex": 224 });});});});