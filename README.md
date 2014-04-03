jboss-fuse-mqtt-test
====================

MQTT Interoperability tests for JBoss Fuse 6.1 for the Eclipse MQTT Interop Event

This project consists of the following:

1. A JBoss A-MQ 6.1 config file activemq.xml.
2. Module test-client, which contains a plain Java client based on the Fuse mqtt-client library. 
3. Module test-camel, which contains a simple Apache Camel client to demonstrate the ease of use in implementing MQTT publishers and subscribers. 

To setup a test Broker download an early release of JBoss A-MQ 6.1 from https://repository.jboss.org/nexus/content/groups/ea/org/jboss/amq/jboss-a-mq/6.1.0.redhat-376/. Copy the activemq.xml file to the etc directory (might want to backup the original) and start the container using the command bin/amq. 
The config file sets up the Broker to only support the topic names used by the test client, and explicitly disables the topic "nosubscribe" for the subscription failure test. 
