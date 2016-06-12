'use strict';function _interopRequireDefault(obj) {return obj && obj.__esModule ? obj : { 'default': obj };}var _chai = require(


'chai');var _mainDataBam = require(

'../../main/data/bam');var _mainDataBam2 = _interopRequireDefault(_mainDataBam);var _mainContigInterval = require(
'../../main/ContigInterval');var _mainContigInterval2 = _interopRequireDefault(_mainContigInterval);var _mainRemoteFile = require(
'../../main/RemoteFile');var _mainRemoteFile2 = _interopRequireDefault(_mainRemoteFile);var _MappedRemoteFile = require(
'../MappedRemoteFile');var _MappedRemoteFile2 = _interopRequireDefault(_MappedRemoteFile);var _mainDataVirtualOffset = require(
'../../main/data/VirtualOffset');var _mainDataVirtualOffset2 = _interopRequireDefault(_mainDataVirtualOffset);

describe('BAM', function () {
  it('should parse BAM files', function () {
    var bamFile = new _mainDataBam2['default'](new _mainRemoteFile2['default']('/test-data/test_input_1_a.bam'));
    return bamFile.readAll().then(function (bamData) {
      var refs = bamData.header.references;
      (0, _chai.expect)(refs).to.have.length(4);
      (0, _chai.expect)(refs[0]).to.contain({ l_ref: 599, name: 'insert' });
      (0, _chai.expect)(refs[3]).to.contain({ l_ref: 4, name: 'ref3' });

      // TODO: test bamData.header.text

      var aligns = bamData.alignments;
      (0, _chai.expect)(aligns).to.have.length(15);

      // The first record in test_input_1_a.sam is:
      // r000 99 insert 50 30 10M = 80 30 ATTTAGCTAC AAAAAAAAAA RG:Z:cow PG:Z:bull
      var r000 = aligns[0].getFull();
      (0, _chai.expect)(r000.read_name).to.equal('r000');
      (0, _chai.expect)(r000.FLAG).to.equal(99);
      (0, _chai.expect)(refs[r000.refID].name).to.equal('insert');
      // .. POS
      (0, _chai.expect)(r000.MAPQ).to.equal(30);
      (0, _chai.expect)(aligns[0].getCigarString()).to.equal('10M');
      // next ref
      // next pos
      (0, _chai.expect)(r000.tlen).to.equal(30);
      (0, _chai.expect)(r000.seq).to.equal('ATTTAGCTAC');
      (0, _chai.expect)(aligns[0].getQualPhred()).to.equal('AAAAAAAAAA');

      var aux = r000.auxiliary;
      (0, _chai.expect)(aux).to.have.length(2);
      (0, _chai.expect)(aux[0]).to.contain({ tag: 'RG', value: 'cow' });
      (0, _chai.expect)(aux[1]).to.contain({ tag: 'PG', value: 'bull' });

      // This one has more interesting auxiliary data:
      // XX:B:S,12561,2,20,112
      aux = aligns[2].getFull().auxiliary;
      (0, _chai.expect)(aux).to.have.length(4);
      (0, _chai.expect)(aux[0]).to.contain({ tag: 'XX' });
      (0, _chai.expect)(aux[0].value.values).to.deep.equal([12561, 2, 20, 112]);
      (0, _chai.expect)(aux[1]).to.contain({ tag: 'YY', value: 100 });
      (0, _chai.expect)(aux[2]).to.contain({ tag: 'RG', value: 'fish' });
      (0, _chai.expect)(aux[3]).to.contain({ tag: 'PG', value: 'colt' });

      // This one has a more interesting Cigar string
      (0, _chai.expect)(aligns[3].getCigarString()).
      to.equal('1S2I6M1P1I1P1I4M2I');

      // - one with a more interesting Phred string
    });});


  // This matches htsjdk's BamFileIndexTest.testSpecificQueries
  it('should find sequences using an index', function () {
    var bam = new _mainDataBam2['default'](new _mainRemoteFile2['default']('/test-data/index_test.bam'), 
    new _mainRemoteFile2['default']('/test-data/index_test.bam.bai'));

    // TODO: run these in parallel
    var range = new _mainContigInterval2['default']('chrM', 10400, 10600);
    return bam.getAlignmentsInRange(range, true).then(function (alignments) {
      (0, _chai.expect)(alignments).to.have.length(1);
      (0, _chai.expect)(alignments[0].toString()).to.equal('chrM:10427-10477');
      return bam.getAlignmentsInRange(range, false).then(function (alignments) {
        (0, _chai.expect)(alignments).to.have.length(2);
        (0, _chai.expect)(alignments[0].toString()).to.equal('chrM:10388-10438');
        (0, _chai.expect)(alignments[1].toString()).to.equal('chrM:10427-10477');

        // These values match IGV
        (0, _chai.expect)(alignments[0].getStrand()).to.equal('+');
        (0, _chai.expect)(alignments[1].getStrand()).to.equal('-');});});});




  it('should fetch alignments from chr18', function () {
    var bam = new _mainDataBam2['default'](new _mainRemoteFile2['default']('/test-data/index_test.bam'), 
    new _mainRemoteFile2['default']('/test-data/index_test.bam.bai'));
    var range = new _mainContigInterval2['default']('chr18', 3627238, 6992285);

    /* Grabbed from IntelliJ & htsjdk using this code fragment:
     String x = "";
     for (int i = 0; i < records.size(); i++) {
         SAMRecord r = records.get(i);
         x = x + r.mReferenceName + ":" + r.mAlignmentStart + "-" + r.mAlignmentEnd + "\n";
     }
     x = x;
     */

    return bam.getAlignmentsInRange(range).then(function (reads) {
      // Note: htsjdk returns contig names like 'chr18', not 18.
      (0, _chai.expect)(reads).to.have.length(14);
      (0, _chai.expect)(reads.map(function (r) {return r.toString();})).to.deep.equal([
      'chr18:3653516-3653566', 
      'chr18:3653591-3653641', 
      'chr18:4215486-4215536', 
      'chr18:4215629-4215679', 
      'chr18:4782331-4782381', 
      'chr18:4782490-4782540', 
      'chr18:5383914-5383964', 
      'chr18:5384093-5384143', 
      'chr18:5904078-5904128', 
      'chr18:5904241-5904291', 
      'chr18:6412181-6412231', 
      'chr18:6412353-6412403', 
      'chr18:6953238-6953288', 
      'chr18:6953412-6953462']);});});




  it('should fetch alignments across a chunk boundary', function () {
    var bam = new _mainDataBam2['default'](new _mainRemoteFile2['default']('/test-data/index_test.bam'), 
    new _mainRemoteFile2['default']('/test-data/index_test.bam.bai'));
    var range = new _mainContigInterval2['default']('chr1', 90002285, 116992285);
    return bam.getAlignmentsInRange(range).then(function (reads) {
      (0, _chai.expect)(reads).to.have.length(92);
      (0, _chai.expect)(reads.slice(0, 5).map(function (r) {return r.toString();})).to.deep.equal([
      'chr1:90071452-90071502', 
      'chr1:90071609-90071659', 
      'chr1:90622416-90622466', 
      'chr1:90622572-90622622', 
      'chr1:91182945-91182995']);


      (0, _chai.expect)(reads.slice(-5).map(function (r) {return r.toString();})).to.deep.equal([
      'chr1:115379485-115379535', 
      'chr1:116045704-116045754', 
      'chr1:116045758-116045808', 
      'chr1:116563764-116563814', 
      'chr1:116563944-116563994']);


      // See "should fetch an alignment at a specific offset", below.
      (0, _chai.expect)(reads.slice(-1)[0].offset.toString()).to.equal('28269:2247');});});



  it('should fetch an alignment at a specific offset', function () {
    // This virtual offset matches the one above.
    // This verifies that alignments are tagged with the correct offset.
    var bam = new _mainDataBam2['default'](new _mainRemoteFile2['default']('/test-data/index_test.bam'));
    return bam.readAtOffset(new _mainDataVirtualOffset2['default'](28269, 2247)).then(function (read) {
      (0, _chai.expect)(read.toString()).to.equal('chr1:116563944-116563994');});});



  it('should fetch alignments in a wide interval', function () {
    var bam = new _mainDataBam2['default'](new _mainRemoteFile2['default']('/test-data/index_test.bam'), 
    new _mainRemoteFile2['default']('/test-data/index_test.bam.bai'));
    var range = new _mainContigInterval2['default']('chr20', 1, 412345678);
    return bam.getAlignmentsInRange(range).then(function (reads) {
      // This count matches what you get if you run:
      // samtools view test/data/index_test.bam | cut -f3 | grep 'chr20' | wc -l
      (0, _chai.expect)(reads).to.have.length(228);});});



  it('should fetch from a large, dense BAM file', function () {
    this.timeout(5000);

    // See test/data/README.md for details on where these files came from.
    var remoteBAI = new _MappedRemoteFile2['default']('/test-data/dream.synth3.bam.bai.mapped', 
    [[8054040, 8242920]]), 
    remoteBAM = new _MappedRemoteFile2['default']('/test-data/dream.synth3.bam.mapped', 
    [[0, 69453], [163622109888, 163622739903]]);

    var bam = new _mainDataBam2['default'](remoteBAM, remoteBAI, { 
      // "chunks" is usually an array; here we take advantage of the
      // Object-like nature of JavaScript arrays to create a sparse array.
      "chunks": { "19": [8054040, 8242920] }, 
      "minBlockIndex": 69454 });


    var range = new _mainContigInterval2['default']('chr20', 31511349, 31514172);

    return bam.getAlignmentsInRange(range).then(function (reads) {
      (0, _chai.expect)(reads).to.have.length(1114);
      (0, _chai.expect)(reads[0].toString()).to.equal('20:31511251-31511351');
      (0, _chai.expect)(reads[1113].toString()).to.equal('20:31514171-31514271');});});



  // Regression test for https://github.com/hammerlab/pileup.js/issues/88
  it('should fetch reads at EOF', function () {
    var bamFile = new _mainRemoteFile2['default']('/test-data/synth3.normal.17.7500000-7515000.bam'), 
    baiFile = new _mainRemoteFile2['default']('/test-data/synth3.normal.17.7500000-7515000.bam.bai'), 
    bam = new _mainDataBam2['default'](bamFile, baiFile);

    var range = new _mainContigInterval2['default']('chr17', 7514800, 7515100);
    return bam.getAlignmentsInRange(range).then(function (reads) {
      // TODO: samtools says 128. Figure out why there's a difference.
      (0, _chai.expect)(reads).to.have.length(130);});});



  // Regression test for https://github.com/hammerlab/pileup.js/issues/172
  // TODO: find a simpler BAM which exercises this code path.
  it('should progress through the chunk list', function () {
    var bamFile = new _MappedRemoteFile2['default']('/test-data/small-chunks.bam.mapped', [[0, 65535], [6536374255, 6536458689], [6536533365, 6536613506], [6536709837, 6536795141]]), 
    baiFile = new _MappedRemoteFile2['default']('/test-data/small-chunks.bam.bai.mapped', [[6942576, 7102568]]), 
    chunks = { 'chunks': { '19': [6942576, 7102568] }, 'minBlockIndex': 65536 }, 
    bam = new _mainDataBam2['default'](bamFile, baiFile, chunks);

    var range = new _mainContigInterval2['default']('chr20', 2684600, 2684800);
    return bam.getAlignmentsInRange(range).then(function (reads) {
      (0, _chai.expect)(reads).to.have.length(7);});});



  it('should fire progress events', function () {
    var bamFile = new _MappedRemoteFile2['default']('/test-data/small-chunks.bam.mapped', [[0, 65535], [6536374255, 6536458689], [6536533365, 6536613506], [6536709837, 6536795141]]), 
    baiFile = new _MappedRemoteFile2['default']('/test-data/small-chunks.bam.bai.mapped', [[6942576, 7102568]]), 
    chunks = { 'chunks': { '19': [6942576, 7102568] }, 'minBlockIndex': 65536 }, 
    bam = new _mainDataBam2['default'](bamFile, baiFile, chunks);

    var range = new _mainContigInterval2['default']('chr20', 2684600, 2684800);
    var progressEvents = [];
    return bam.getAlignmentsInRange(range).progress(function (event) {
      progressEvents.push(event);}).
    then(function (reads) {
      (0, _chai.expect)(progressEvents).to.deep.equal([
      { status: 'Fetching BAM header' }, 
      { status: 'Fetching BAM index' }, 
      { numRequests: 1 }, 
      { numRequests: 2 }, 
      { numRequests: 3 }, 
      { numRequests: 4 }, 
      { numRequests: 5 }]);});});});