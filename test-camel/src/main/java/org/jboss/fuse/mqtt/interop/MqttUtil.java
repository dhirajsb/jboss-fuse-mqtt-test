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
package org.jboss.fuse.mqtt.interop;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.camel.Exchange;

/**
 * @author dbokde
 */
public class MqttUtil {

    public static void publish(Exchange exchange) {
        // split file name to get topic, qos and retain flags
        final String fileName = exchange.getIn().getHeader("CamelFileName", String.class);
        final String[] nameParts = fileName.split("_");
        exchange.setProperty("topicProperty", nameParts[0].replaceAll("emptyLevel", ""));
        exchange.setProperty("qosProperty", nameParts[1]);
        exchange.setProperty("retainProperty", Boolean.parseBoolean(nameParts[2]));
    }

    public static void receive(Exchange exchange) {
        // generate a file name using timestamp
        final DateFormat df = new SimpleDateFormat("yyyyMMdd'T'HHmmssSSSZ");
        String nowAsISO = df.format(new Date());
        exchange.getIn().setHeader("CamelFileName", nowAsISO + ".txt");
    }

}
