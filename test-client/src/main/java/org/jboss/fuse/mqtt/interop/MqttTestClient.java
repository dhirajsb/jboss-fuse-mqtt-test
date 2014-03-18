package org.jboss.fuse.mqtt.interop;
/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.fusesource.mqtt.client.BlockingConnection;
import org.fusesource.mqtt.client.MQTT;
import org.fusesource.mqtt.client.Message;
import org.fusesource.mqtt.client.QoS;
import org.fusesource.mqtt.client.Topic;
import org.fusesource.mqtt.client.Tracer;
import org.fusesource.mqtt.codec.MQTTFrame;

/**
 * @author dbokde
 */
public class MqttTestClient {

    public static final Log LOG = LogFactory.getLog(MqttTestClient.class);

    private static final String DEFAULT_HOST = "localhost";
    private static final String DEFAULT_PORT = "1883";
    private static final String MYCLIENTID = "myclientid";
    private static final String MYCLIENTID2 = "myclientid2";

    public static final  String[] CLIENT_IDS = {MYCLIENTID, MYCLIENTID2};
    public static final String[] TOPICS = { "TopicA", "TopicA/B", "Topic/C", "TopicA/C", "/TopicA" };
    public static final String[] WILD_TOPICS = { "TopicA/+", "+/C", "#", "/#", "/+", "+/+", "TopicA/#" };

    public static final String NO_SUBSCRIBE_TOPIC = "nosubscribe";

    private static final byte[] EMPTY_MESSAGE = "".getBytes();
    private static final byte[] QOS0_MESSAGE = "qos 0".getBytes();
    private static final byte[] QOS1_MESSAGE = "qos 1".getBytes();
    private static final byte[] QOS2_MESSAGE = "qos 2".getBytes();
    private static final int RECEIVE_TIMEOUT = 3000;

    private MQTT mqtt;

    public static void main(String[] args) {
        Options options = new Options();
        options.addOption("help", false, "print this message").
            addOption("trace", false, "enable packet tracing").
            addOption("host", true, "MQTT broker host, default localhost").
            addOption("port", true, "MQTT broker port, default 1883").
            addOption("user", true, "user name").
            addOption("password", true, "user password").
            addOption("zero_length_clientid", false, "run zero length client id test").
            addOption("dollar_topics", false, "run zero length client id test").
            addOption("subscribe_failure", false, "run subscribe failure test").
            addOption("iterations", true, "test suite iterations");
        CommandLineParser parser = new GnuParser();
        final CommandLine commandLine;
        try {
            commandLine = parser.parse(options, args);
            if (commandLine.hasOption("help")) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printUsage(new PrintWriter(System.out), 80, "java " + MqttTestClient.class.getName(), options);
                return;
            }
            new MqttTestClient().run(commandLine);
        } catch (ParseException e) {
            LOG.error("Error parsing command line: " + e.getMessage());
        }
    }

    private void run(CommandLine commandLine) {
        final String host = commandLine.getOptionValue("host", DEFAULT_HOST);
        final int port = Integer.valueOf(commandLine.getOptionValue("port", DEFAULT_PORT));
        LOG.info("Test broker at " + host + ":" + port);
        final String user = commandLine.getOptionValue("user");
        final String password = commandLine.getOptionValue("password");

        // connect
        mqtt = new MQTT();
        mqtt.setVersion("3.1.1");
        if (commandLine.hasOption("trace")) {
            mqtt.setTracer(new Tracer() {
                @Override
                public void onSend(MQTTFrame frame) {
                    LOG.info("Client sending " + frame);
                }

                @Override
                public void onReceive(MQTTFrame frame) {
                    LOG.info("Client received " + frame);
                }
            });
        }
        boolean succeded = true;
        try {
            mqtt.setHost(host, port);
            if (user != null && password != null) {
                mqtt.setUserName(user);
                mqtt.setPassword(password);
            }

            // run the various tests
            MqttTest tests[] = new MqttTest[] {
                new Basic(),
                new RetainedMessage(),
                new OfflineMessageQueueing(),
                new WillMessage(),
                new OverlappingSubscriptions(),
                new KeepAlive(),
                new RedeliveryOnReconnect(),
                commandLine.hasOption("zero_length_clientid") ? new ZeroLengthClientId() : null,
                commandLine.hasOption("dollar_topics") ? new DollarTopics() : null,
                commandLine.hasOption("subscribe_failure")? new SubscribeFailure() : null
                };

            int iterations = Integer.parseInt(commandLine.getOptionValue("iterations", "1"));
            LOG.info("Running test suite " + iterations + " times");
            for (int i = 0; i < iterations; i++) {
                cleanup();

                if (iterations != 1) {
                    LOG.info("Test suite iteration " + i);
                }
                for (MqttTest test : tests) {
                    if (test != null) {
                        LOG.info("Running " + getTestName(test.getClass().getName()) + " test...");
                        succeded &= test.run();
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("Error in test client " + e.getMessage(), e);
            succeded = false;
        } finally {
            if (succeded) {
                LOG.info("Test suite succeeded");
            } else {
                LOG.error("Test suite failed!");
            }
        }
    }

    private void cleanup() throws Exception {
        // clean sessions
        for (String clientId : CLIENT_IDS) {
            MQTT mqtt1 = new MQTT(mqtt);

            mqtt1.setClientId(clientId);
            final BlockingConnection connection = mqtt1.blockingConnection();
            try {
                connection.connect();
                // clean all topics
                Topic topics[] = new Topic[TOPICS.length];
                for (int i = 0; i < topics.length; i++) {
                    topics[i] = new Topic(TOPICS[i], QoS.EXACTLY_ONCE);
                }
                connection.subscribe(topics);
                receiveMessages(connection);
                connection.unsubscribe(TOPICS);
            } finally {
                safeDisconnect(connection);
            }
        }

        // clean retained messages
        MQTT mqtt1 = new MQTT(mqtt);
        mqtt1.setClientId("clean retained");
        final BlockingConnection connection = mqtt1.blockingConnection();
        try {
            connection.connect();
            connection.subscribe(new Topic[] {new Topic("#", QoS.AT_MOST_ONCE)} );
            List<Message> msgs = new ArrayList<Message>();
            Message msg;
            while ((msg = connection.receive(1000, TimeUnit.MILLISECONDS)) != null) {
                msgs.add(msg);
            }
            for (Message m : msgs) {
                connection.publish(m.getTopic(), "".getBytes(), QoS.AT_MOST_ONCE, true);
            }
        } finally {
            safeDisconnect(connection);
        }
    }

    private class Basic implements MqttTest {
        @Override
            public boolean run() {
            boolean succeeded = true;

            final BlockingConnection connection = getBlockingConnection(MYCLIENTID, true);
            try {
                connection.connect();
                connection.subscribe(new Topic[]{new Topic(TOPICS[0], QoS.EXACTLY_ONCE)});

                connection.publish(TOPICS[0], QOS0_MESSAGE, QoS.AT_MOST_ONCE, false);
                connection.publish(TOPICS[0], QOS1_MESSAGE, QoS.AT_LEAST_ONCE, false);
                connection.publish(TOPICS[0], QOS2_MESSAGE, QoS.EXACTLY_ONCE, false);

                succeeded = receiveExpectedMessages(connection, 3);
                connection.disconnect();

/* ignored, since its ends up testing client code anyway
                connection.connect();
                try {
                    connection.connect();
                    LOG.error("Duplicate connect must be rejected");
                    succeeded = false;
                } catch (Exception expected) {
                }
                connection.disconnect();
*/
            } catch (Exception e) {
                LOG.error(e.getMessage(), e);
                succeeded = false;
            } finally {
                logResult(connection, succeeded);
            }
            return succeeded;
        }
    }

    private class RetainedMessage implements MqttTest {
        @Override
        public boolean run() {
            boolean succeeded = true;

            BlockingConnection connection = getBlockingConnection(MYCLIENTID, true);
            try {
                connection.connect();

                connection.publish(TOPICS[1], QOS0_MESSAGE, QoS.AT_MOST_ONCE, true);
                connection.publish(TOPICS[2], QOS1_MESSAGE, QoS.AT_LEAST_ONCE, true);
                connection.publish(TOPICS[3], QOS2_MESSAGE, QoS.EXACTLY_ONCE, true);

                connection.subscribe(new Topic[]{new Topic(WILD_TOPICS[5], QoS.EXACTLY_ONCE)});
                succeeded = receiveExpectedMessages(connection, 3);
                connection.disconnect();

                // clear retained messages
                connection = getBlockingConnection(MYCLIENTID, true);
                connection.connect();
                connection.publish(TOPICS[1], EMPTY_MESSAGE, QoS.AT_MOST_ONCE, true);
                connection.publish(TOPICS[2], EMPTY_MESSAGE, QoS.AT_LEAST_ONCE, true);
                connection.publish(TOPICS[3], EMPTY_MESSAGE, QoS.EXACTLY_ONCE, true);

                connection.subscribe(new Topic[]{new Topic(WILD_TOPICS[5], QoS.EXACTLY_ONCE)});
                succeeded = receiveExpectedMessages(connection, 0);
                connection.disconnect();

            } catch (Exception e) {
                LOG.error(e.getMessage(), e);
                succeeded = false;
            } finally {
                logResult(connection, succeeded);
            }
            return succeeded;
        }
    }

    private class OfflineMessageQueueing implements MqttTest {
        @Override
        public boolean run() {
            boolean succeeded = true;

            BlockingConnection connection = getBlockingConnection(MYCLIENTID, false);
            BlockingConnection connection2 = getBlockingConnection(MYCLIENTID2, true);
            try {
                connection.connect();
                connection.subscribe(new Topic[] { new Topic( WILD_TOPICS[5], QoS.EXACTLY_ONCE)});
                connection.disconnect();

                connection2.connect();
                connection2.publish(TOPICS[1], QOS0_MESSAGE, QoS.AT_MOST_ONCE, false);
                connection2.publish(TOPICS[2], QOS1_MESSAGE, QoS.AT_LEAST_ONCE, false);
                connection2.publish(TOPICS[3], QOS2_MESSAGE, QoS.EXACTLY_ONCE, false);
                connection2.disconnect();

                connection = getBlockingConnection(MYCLIENTID, false);
                connection.connect();
                final int count = receiveMessages(connection);
                if (count < 2 || count > 3) {
                    LOG.error("Excepted 2 or 3 messages, received " + count);
                    succeeded = false;
                } else {
                    LOG.info("This server " + (count == 3 ? "is" : "is not")
                        + " queueing Qos0 messages for offline clients");
                }
            } catch (Exception e) {
                LOG.error(e.getMessage(), e);
                succeeded = false;
            } finally {
                safeDisconnect(connection2);
                logResult(connection, succeeded);
            }
            return succeeded;
        }
    }

    private class WillMessage implements MqttTest {
        @Override
        public boolean run() {
            boolean succeeded = true;
            MQTT mqtt1 = new MQTT(mqtt);
            mqtt1.setClientId(MYCLIENTID);
            mqtt1.setWillTopic(TOPICS[2]);
            mqtt1.setWillMessage("client not disconnected");
            mqtt1.setWillQos(QoS.EXACTLY_ONCE);
            mqtt1.setKeepAlive((short) 2);

            final BlockingConnection connection = mqtt1.blockingConnection();
            final BlockingConnection connection1 = getBlockingConnection(MYCLIENTID2, false);
            try {
                connection.connect();

                connection1.connect();
                connection1.subscribe(new Topic[] { new Topic(TOPICS[2], QoS.EXACTLY_ONCE)});

                connection.kill();
                Thread.sleep(5000);
                succeeded = receiveExpectedMessages(connection1, 1);

            } catch (Exception e) {
                LOG.error(e.getMessage(), e);
                succeeded = false;
            } finally {
                safeDisconnect(connection1);
                logResult(connection, succeeded);
            }

            return succeeded;
        }
    }

    private class OverlappingSubscriptions implements MqttTest {
        @Override
        public boolean run() {
            boolean succeeded = true;
            BlockingConnection connection = getBlockingConnection(MYCLIENTID, true);
            try {
                connection.connect();
                connection.subscribe(new Topic[] { new Topic(WILD_TOPICS[6], QoS.EXACTLY_ONCE), new Topic(WILD_TOPICS[0], QoS.AT_LEAST_ONCE)});

                connection.publish(TOPICS[3], QOS2_MESSAGE, QoS.EXACTLY_ONCE, false);
                final int count = receiveMessages(connection);
                if (count < 1 || count > 2) {
                    LOG.error("Excepted 1 or 2 messages, received " + count);
                    succeeded = false;
                } else {
                    LOG.info("This server is publishing one message " +
                        (count == 1 ? " for all" : "per each") + " matching overlapping subscriptions");
                }
                connection.disconnect();
            } catch (Exception e) {
                LOG.error(e.getMessage(), e);
                succeeded = false;
            } finally {
                logResult(connection, succeeded);
            }
            return succeeded;
        }
    }

    private class KeepAlive implements MqttTest {
        @Override
        public boolean run() {
            boolean succeeded = true;
            MQTT mqtt1 = new MQTT(mqtt);
            mqtt1.setClientId(MYCLIENTID);
            mqtt1.setWillTopic(TOPICS[4]);
            mqtt1.setWillMessage("keepalive expiry");
            mqtt1.setWillQos(QoS.EXACTLY_ONCE);
            mqtt1.setKeepAlive((short) 5);
            final BlockingConnection connection = mqtt1.blockingConnection();

            final BlockingConnection connection1 = getBlockingConnection(MYCLIENTID2, true);
            try {
                connection.connect();
                connection.suspend();

                connection1.connect();
                connection1.subscribe(new Topic[]{ new Topic(TOPICS[4], QoS.EXACTLY_ONCE)});

                Thread.sleep(15 * 1000);
                succeeded = receiveExpectedMessages(connection1, 1);
            } catch (Exception e) {
                LOG.error(e.getMessage(), e);
                succeeded = false;
            } finally {
                safeDisconnect(connection1);
                logResult(connection, succeeded);
            }
            return succeeded;
        }
    }

    private class RedeliveryOnReconnect implements MqttTest {
        @Override
        public boolean run() {
            boolean succeeded = true;
            BlockingConnection connection = getBlockingConnection(MYCLIENTID, false);
            try {
                connection.connect();
                connection.subscribe(new Topic[]{new Topic(WILD_TOPICS[6], QoS.EXACTLY_ONCE)});
                connection.publish(TOPICS[1], QOS1_MESSAGE, QoS.AT_LEAST_ONCE, false);
                connection.publish(TOPICS[3], QOS1_MESSAGE, QoS.EXACTLY_ONCE, false);
                connection.disconnect();

                connection = getBlockingConnection(MYCLIENTID, false);
                connection.connect();
                succeeded = receiveExpectedMessages(connection, 2);
            } catch (Exception e) {
                LOG.error(e.getMessage(), e);
                succeeded = false;
            } finally {
                logResult(connection, succeeded);
            }
            return succeeded;
        }
    }

    private class ZeroLengthClientId implements MqttTest {
        @Override
        public boolean run() {
            boolean succeeded = true;
            BlockingConnection connection = null;
            try {
                try {
                    connection = getBlockingConnection("", false);
                    connection.connect();
                    LOG.error("Empty client id with false clean session MUST throw an exception");
                    succeeded = false;
                } catch (Exception expected) {}

                connection = getBlockingConnection("", true);
                connection.connect();
            } catch (Exception e) {
                LOG.error(e.getMessage(), e);
                succeeded = false;
            } finally {
                logResult(connection, succeeded);
            }
            return succeeded;
        }
    }

    private class DollarTopics implements MqttTest {
        @Override
        public boolean run() {
            boolean succeeded = true;
            BlockingConnection connection = getBlockingConnection(MYCLIENTID, true);
            try {
                connection.connect();
                connection.subscribe(new Topic[]{ new Topic(WILD_TOPICS[5], QoS.EXACTLY_ONCE)});
                receiveMessages(connection);

                connection.publish("$" + TOPICS[1], EMPTY_MESSAGE, QoS.AT_LEAST_ONCE, false);
                succeeded = receiveExpectedMessages(connection, 0);
            } catch (Exception e) {
                LOG.error(e.getMessage(), e);
                succeeded = false;
            } finally {
                logResult(connection, succeeded);
            }
            return succeeded;
        }
    }

    private class SubscribeFailure implements MqttTest {
        @Override
        public boolean run() {
            boolean succeeded = true;
            BlockingConnection connection = getBlockingConnection(MYCLIENTID, true);
            try {
                connection.connect();
                try {
                    final byte[] subscribe = connection.subscribe(new Topic[]{new Topic(NO_SUBSCRIBE_TOPIC, QoS.AT_LEAST_ONCE)});
                    if (subscribe[0] != (byte)0x80) {
                        LOG.error("Expected return code 0x80 when subscribing to topic " + NO_SUBSCRIBE_TOPIC);
                        succeeded = false;
                    }
                } catch (Exception expected) {}
            } catch (Exception e) {
                LOG.error(e.getMessage(), e);
                succeeded = false;
            } finally {
                logResult(connection, succeeded);
            }
            return succeeded;
        }
    }

    public static interface MqttTest {
        boolean run();
    }

    private BlockingConnection getBlockingConnection(String clientId, boolean cleanSession) {
        MQTT mqtt1 = new MQTT(mqtt);
        mqtt1.setCleanSession(cleanSession);
        mqtt1.setClientId(clientId);
        mqtt1.setKeepAlive((short) 5);

        return mqtt1.blockingConnection();
    }

    private boolean receiveExpectedMessages(BlockingConnection connection, int expected) throws Exception {
        int count = receiveMessages(connection);

        if (count != expected) {
            LOG.error("Expected " + expected + " messages, received " + count);
            return false;
        }
        return true;
    }

    private int receiveMessages(BlockingConnection connection) throws Exception {
        int count = 0;
        Message msg;
        while ((msg = connection.receive(RECEIVE_TIMEOUT, TimeUnit.MILLISECONDS)) != null) {
            msg.ack();
            count++;
        }
        return count;
    }

    private void logResult(BlockingConnection connection, boolean succeeded) {
        safeDisconnect(connection);
        final StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        final String name = stackTrace[3].getClassName();
        StringBuffer buffer = getTestName(name);
        if (succeeded) {
            LOG.info(buffer.toString() + " test succeeded");
        } else {
            LOG.error(buffer.toString() + " test failed!");
        }
    }

    private StringBuffer getTestName(String className) {
        StringBuffer buffer = new StringBuffer();
        boolean first = true;
        for (char c : className.substring(className.lastIndexOf('$') + 1).toCharArray()) {
            if (Character.isUpperCase(c)) {
                if (!first) {
                    buffer.append(' ');
                    buffer.append(Character.toLowerCase(c));
                } else {
                    buffer.append(c);
                    first = false;
                }
            } else {
                buffer.append(c);
            }
        }
        return buffer;
    }

    private void safeDisconnect(BlockingConnection connection) {
        if (connection.isConnected()) {
            try {
                receiveMessages(connection);
                connection.disconnect();
                while(connection.isConnected()) {
                    Thread.sleep(1000);
                }
            } catch (Exception ignore) {}
        }
    }

}
