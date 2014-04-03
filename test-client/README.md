test-client
====================

MQTT test client using Fuse mqtt-client

To run the test client use the maven Java exec plugin using the command below, which will print the list of options supported by the client. The host and port default to localhost:1883. 

mvn exec:java -Dexec.mainClass=org.jboss.fuse.mqtt.interop.MqttTestClient -Dexec.args="--help"
