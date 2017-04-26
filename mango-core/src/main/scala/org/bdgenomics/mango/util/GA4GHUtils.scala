package org.bdgenomics.mango.core.util

import com.google.protobuf.Message
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json.{ compact, render }
import org.bdgenomics.mango.layout.GenotypeJson
import ga4gh.Variants.Variant

import scala.collection.JavaConverters._
import com.google.protobuf.util.JsonFormat.Printer

case class SearchVariantsRequestGA4GH(variantSetId: String,
                                      start: String,
                                      end: String,
                                      pageSize: String,
                                      pageToken: String,
                                      referenceName: String,
                                      callSetIds: Array[String] = new Array[String](0)) {

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

/*
object GA4GHutils {


}

*/ 