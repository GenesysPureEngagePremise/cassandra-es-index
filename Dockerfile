FROM alpine/git as clone

WORKDIR /

RUN git clone https://github.com/GenesysPureEngagePremise/cassandra-es-index.git

FROM maven:3.5-jdk-8-alpine as build

WORKDIR /cassandra-es-index

COPY --from=clone /cassandra-es-index /cassandra-es-index
RUN mvn package

FROM cassandra:3.11 as cassandra

COPY --from=build /cassandra-es-index/target/distribution/lib4cassandra/* /usr/share/cassandra/lib/

VOLUME /var/lib/cassandra

# 7000: intra-node communication
# 7001: TLS intra-node communication
# 7199: JMX
# 9042: CQL
# 9160: thrift service
EXPOSE 7000 7001 7199 9042 9160
CMD ["cassandra", "-f"]
