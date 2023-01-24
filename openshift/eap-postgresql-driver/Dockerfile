FROM quay.io/centos/centos:stream9-minimal

USER root

# install postgresql jdbc driver
RUN microdnf install postgresql-jdbc -y \
    && microdnf clean all \
    && rm -fr /var/cache/yum/*

# Copy add-on details
COPY install.sh \
     drivers.env \
     /extensions/
COPY modules /extensions/modules/
RUN cp /usr/share/java/postgresql-jdbc.jar /extensions/modules/system/layers/openshift/org/postgresql/main/

LABEL name="PostgreSQL JDBC Driver Extension Image for JBoss EAP 7"