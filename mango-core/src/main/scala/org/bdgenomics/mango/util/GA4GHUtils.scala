package org.bdgenomics.mango.core.util

case class SearchVariantsRequestGA4GH(variantSetId: String,
                                      start: String,
                                      end: String,
                                      pageSize: String,
                                      pageToken: String,
                                      referenceName: String,
                                      callSetIds: Array[String] = new Array[String](0),
                                      binning: String = "1") {
  def this(variantSetId: String,
           start: String,
           end: String,
           pageSize: String,
           pageToken: String,
           referenceName: String,
           callSetIds: Array[String]) = {
    this(variantSetId: String,
      start: String,
      end: String,
      pageSize: String,
      pageToken: String,
      referenceName: String,
      callSetIds: Array[String],
      "1")
  }
}

case class SearchVariantsRequestGA4GHBinning(variantSetId: String,
                                             start: String,
                                             end: String,
                                             pageSize: String,
                                             pageToken: String,
                                             referenceName: String,
                                             callSetIds: Array[String] = new Array[String](0),
                                             binning: String = "1") {
  def this(variantSetId: String,
           start: String,
           end: String,
           pageSize: String,
           pageToken: String,
           referenceName: String,
           callSetIds: Array[String]) = {
    this(variantSetId: String,
      start: String,
      end: String,
      pageSize: String,
      pageToken: String,
      referenceName: String,
      callSetIds: Array[String], "1")
  }
}

// see proto defintiion: https://github.com/ga4gh/ga4gh-schemas/blob/master/src/main/proto/ga4gh/read_service.proto#L117
case class SearchReadsRequestGA4GH(read_groups_id: Array[String] = new Array[String](0),
                                   reference_id: String,
                                   start: String,
                                   end: String)

