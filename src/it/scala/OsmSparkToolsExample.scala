import java.util.Properties

import org.akashihi.osm.spark.OsmSource.OsmSource
import org.akashihi.osm.spark._
import org.akashihi.osm.spark.geometry.ResolveMultipolygon
import org.apache.spark.sql._
import org.apache.spark.sql.functions._
import org.apache.spark.storage.StorageLevel

object OsmSparkToolsExample {

  def merge(sources: String, output: String, spark: SparkSession): Unit = {
    val osmFiles = sources.split(",").map(filename => spark.read
      .option("threads", 6)
      .option("partitions", 8)
      .format(OsmSource.OSM_SOURCE_NAME)
      .load(filename).drop("INFO")
      .persist(StorageLevel.MEMORY_AND_DISK))

    println("Input files bboxes:")
    osmFiles.map(BoundBox.findBBox).foreach(println)

    val merged = Merge(osmFiles).persist(StorageLevel.MEMORY_AND_DISK)
    println(s"Merged osm data bbox: ${BoundBox.findBBox(merged)}")

    merged.write.mode(SaveMode.Overwrite).parquet(output)
  }

  def findBoundary(admin_level: String, ref: String, source: String, output: String, spark: SparkSession): Unit = {
    val osm = spark.read
      .parquet(source)
      .persist(StorageLevel.MEMORY_AND_DISK)

    val zabovreskyBoundary = osm.filter(col("TYPE") === OsmEntity.RELATION)
      //.filter(lower(col("TAG")("boundary")) === "administrative" && col("TAG")("admin_level") === admin_level && col("TAG")("ref") === ref)
      .filter(lower(col("TAG")("leisure")) === "park" && col("TAG")("wikidata") === "Q16956611")
      .cache()

    ResolveMultipolygon(zabovreskyBoundary, osm)
  }

  def extract(source: String, output: String, spark: SparkSession): Unit = {
    val osm = spark.read
      .parquet(source)
      .persist(StorageLevel.MEMORY_AND_DISK)

    val extracted = Extract(osm, 16.578809, 49.212551, 16.595750, 49.205591, Extract.ParentRelations, spark)
      .persist(StorageLevel.MEMORY_AND_DISK)
    println(s"Extracted osm data bbox: ${BoundBox.findBBox(extracted)}")

    extracted.write.mode(SaveMode.Overwrite).parquet(output)
  }

  def writeOsmosis(spark: SparkSession): Unit = {
    val db = new Properties()
    db.put("user", "osmium")
    db.put("password", "osmium")
    db.put("url", "jdbc:postgresql://localhost:5432/osmium")

    val osm = spark.read
      .parquet("/tmp/zabovrezsky")
      .persist(StorageLevel.MEMORY_AND_DISK)

    WriteOsmosis(osm, db)
  }

  def main(args: Array[String]): Unit = {
    val spark = SparkSession
      .builder()
      .appName("OsmSparkTools")
      .config("spark.master", "local[12]")
      .config("spark.executor.memory", "8gb")
      .getOrCreate()

    //merge(args(0), "/tmp/czech_cities", spark)
    findBoundary("10", "610470", "/tmp/czech_cities", "/tmp/boundary", spark)
    //extract("/tmp/czech_cities", "/tmp/zabovrezsky", spark)

    //writeOsmosis(spark)
  }
}
