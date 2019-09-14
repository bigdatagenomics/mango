FROM maven:3.5-jdk-8-alpine AS build
WORKDIR /app
COPY mango-cli /app/mango-cli
COPY mango-core /app/mango-core
COPY mango-assembly /app/mango-assembly
COPY pom.xml /app/

RUN mvn package -DskipTests

FROM bde2020/spark-submit

MAINTAINER Roel van den Berg <roeland.van.den.berg@ordina.nl>
MAINTAINER Eelco Eggen <eelco.eggen@ordina.nl>

WORKDIR /mango
COPY --from=build /app/mango-assembly/target/mango-assembly-0.0.2-SNAPSHOT.jar /mango/mango-assembly/target/
COPY bin /mango/bin

COPY ./data /data
RUN chmod 755 /data/*

RUN apk add --no-cache bash
ENV SPARK_HOME /spark

ENTRYPOINT ["/mango/bin/mango-submit", \
  "--conf", "spark.kryoserializer.buffer.max=512m", \
  "--master", "spark://spark-master:7077", \
  "--", \
  "/data/S288C_reference_sequence_R64-2-1_20150113.fasta", \
  "-discover"]
