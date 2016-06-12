'use strict';function _interopRequireDefault(obj) {return obj && obj.__esModule ? obj : { 'default': obj };}var _chai = require(





'chai');var _mainRemoteFile = require(

'../../main/RemoteFile');var _mainRemoteFile2 = _interopRequireDefault(_mainRemoteFile);var _mainDataBam = require(
'../../main/data/bam');var _mainDataBam2 = _interopRequireDefault(_mainDataBam);var _mainContigInterval = require(
'../../main/ContigInterval');var _mainContigInterval2 = _interopRequireDefault(_mainContigInterval);

describe('SamRead', function () {

  function getSamArray(url) {
    return new _mainDataBam2['default'](new _mainRemoteFile2['default'](url)).readAll().then(function (d) {return d.alignments;});}


  var testReads = getSamArray('/test-data/test_input_1_a.bam');

  // This is more of a test for the test than for SamRead.
  it('should pull records from a BAM file', function () {
    return testReads.then(function (reads) {
      (0, _chai.expect)(reads).to.have.length(15);});});



  it('should parse BAM records', function () {
    return testReads.then(function (reads) {
      // The first record in test_input_1_a.sam is:
      // r000 99 insert 50 30 10M = 80 30 ATTTAGCTAC AAAAAAAAAA RG:Z:cow PG:Z:bull
      var read = reads[0];
      (0, _chai.expect)(read.name).to.equal('r000');
      (0, _chai.expect)(read.refID).to.equal(0);
      (0, _chai.expect)(read.ref).to.equal('insert');
      (0, _chai.expect)(read.pos).to.equal(49); // 0-based
      (0, _chai.expect)(read.l_seq).to.equal(10);
      (0, _chai.expect)(read.toString()).to.equal('insert:50-59');
      (0, _chai.expect)(read.cigarOps).to.deep.equal([{ op: 'M', length: 10 }]);
      (0, _chai.expect)(read.getStrand()).to.equal('+');

      (0, _chai.expect)(read.getMateProperties()).to.deep.equal({ 
        ref: 'insert', // same as read.ref
        pos: 79, 
        strand: '-' });


      // This one has a more interesting Cigar string
      (0, _chai.expect)(reads[3].cigarOps).to.deep.equal([
      { op: 'S', length: 1 }, 
      { op: 'I', length: 2 }, 
      { op: 'M', length: 6 }, 
      { op: 'P', length: 1 }, 
      { op: 'I', length: 1 }, 
      { op: 'P', length: 1 }, 
      { op: 'I', length: 1 }, 
      { op: 'M', length: 4 }, 
      { op: 'I', length: 2 }]);});});




  it('should read thick records', function () {
    return testReads.then(function (reads) {
      // This mirrors the "BAM > should parse BAM files" test.
      var r000 = reads[0].getFull();
      (0, _chai.expect)(r000.read_name).to.equal('r000');
      (0, _chai.expect)(r000.FLAG).to.equal(99);
      (0, _chai.expect)(reads[0].ref).to.equal('insert');
      // .. POS
      (0, _chai.expect)(r000.MAPQ).to.equal(30);
      (0, _chai.expect)(reads[0].getCigarString()).to.equal('10M');
      (0, _chai.expect)(r000.next_pos).to.equal(79);
      (0, _chai.expect)(r000.next_refID).to.equal(0);

      (0, _chai.expect)(r000.tlen).to.equal(30);
      (0, _chai.expect)(r000.seq).to.equal('ATTTAGCTAC');
      (0, _chai.expect)(reads[0].getSequence()).to.equal('ATTTAGCTAC');
      (0, _chai.expect)(reads[0].getQualPhred()).to.equal('AAAAAAAAAA');

      var aux = r000.auxiliary;
      (0, _chai.expect)(aux).to.have.length(2);
      (0, _chai.expect)(aux[0]).to.contain({ tag: 'RG', value: 'cow' });
      (0, _chai.expect)(aux[1]).to.contain({ tag: 'PG', value: 'bull' });

      (0, _chai.expect)(reads).to.have.length(15);
      (0, _chai.expect)(reads[14].refID).to.equal(-1); // unmapped read
      (0, _chai.expect)(reads[14].ref).to.equal('');});});



  it('should find record intersections', function () {
    return testReads.then(function (reads) {
      var read = reads[0];
      // toString() produces a 1-based result, but ContigInterval is 0-based.
      (0, _chai.expect)(read.toString()).to.equal('insert:50-59');
      (0, _chai.expect)(read.intersects(new _mainContigInterval2['default']('insert', 40, 49))).to.be['true'];
      (0, _chai.expect)(read.intersects(new _mainContigInterval2['default']('insert', 40, 48))).to.be['false'];
      (0, _chai.expect)(read.intersects(new _mainContigInterval2['default']('0', 40, 55))).to.be['false'];
      (0, _chai.expect)(read.intersects(new _mainContigInterval2['default']('insert', 58, 60))).to.be['true'];
      (0, _chai.expect)(read.intersects(new _mainContigInterval2['default']('insert', 59, 60))).to.be['false'];});});});