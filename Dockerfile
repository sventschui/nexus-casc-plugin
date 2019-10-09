FROM maven:3.6-jdk-8-slim as build

WORKDIR /

ADD pom.xml ./

RUN mvn clean compile

ADD src ./src

RUN mvn verify -T1C

FROM sonatype/nexus3:3.18.1

ARG NEXUS_CASC_VERSION=3.18.1-01
ENV NEXUS_CASC_VERSION=$NEXUS_CASC_VERSION

USER 0

RUN echo "reference\:file\:nexus-casc-plugin-${NEXUS_CASC_VERSION}.jar = 199" >> /opt/sonatype/nexus/etc/karaf/startup.properties

COPY --from=build --chown=root:root /target/nexus-casc-plugin-${NEXUS_CASC_VERSION}.jar /opt/sonatype/nexus/system/nexus-casc-plugin-${NEXUS_CASC_VERSION}.jar

COPY default-nexus.yml /opt/nexus.yml

ENV NEXUS_CASC_CONFIG=/opt/nexus.yml

USER nexus
