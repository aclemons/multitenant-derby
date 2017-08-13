multi-tenant-derby
==================

Playing around with massive numbers of derby databases.

        $ export MAVEN_OPTS="-server -Xmx10G"
        $ mvn clean install jetty:run -Dderby.system.home="$(pwd)/target/classes"
        $ for i in $(seq 1 4000) ; do printf "curl -v -H \"tenant: tenant%s\" http://localhost:8080/\0" "$i" ; done | parallel -0

