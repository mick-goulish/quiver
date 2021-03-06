/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

package net.ssorj.quiver;

import java.io.*;
import java.lang.*;
import java.util.*;
import javax.jms.*;
import javax.naming.*;

public class QuiverArrowJms {
    public static void main(String[] args) {
        try {
            doMain(args);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    public static void doMain(String[] args) throws Exception {
        String outputDir = args[0];
        String mode = args[1];
        String operation = args[2];
        String path = args[5];
        int messages = Integer.parseInt(args[6]);
        int bytes = Integer.parseInt(args[7]);

        if (!mode.equals("client")) {
            throw new RuntimeException("This impl supports client mode only");
        }

        String cfPrefix = System.getProperty("arrow.jms.cf.prefix");
        String cfName = System.getProperty("arrow.jms.cf.name");
        String cfUrl = System.getProperty("arrow.jms.cf.url");

        assert cfPrefix != null;
        assert cfName != null;
        assert cfUrl != null;
        
        Hashtable<Object, Object> env = new Hashtable<Object, Object>();
        env.put(cfPrefix + "." + cfName, cfUrl);
        env.put("queue.queueLookup", path);

        Context context = new InitialContext(env);;
        ConnectionFactory factory = (ConnectionFactory) context.lookup(cfName);
        Destination queue = (Destination) context.lookup("queueLookup");

        Client client = new Client(outputDir, factory, queue, operation,
                                   messages, bytes);
        
        client.run();
    }
}

class Client {
    protected final String outputDir;
    protected final ConnectionFactory factory;
    protected final Destination queue;
    protected final String operation;
    protected final int messages;
    protected final int bytes;

    protected int sent;
    protected int received;
    
    Client(String outputDir, ConnectionFactory factory, Destination queue,
           String operation, int messages, int bytes) {
        this.outputDir = outputDir;
        this.factory = factory;
        this.queue = queue;
        this.operation = operation;
        this.messages = messages;
        this.bytes = bytes;

        this.sent = 0;
        this.received = 0;
    }

    void run() {
        try {
            Connection conn = this.factory.createConnection();
            conn.start();

            Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            if (this.operation.equals("send")) {
                this.sendMessages(session);
            } else if (this.operation.equals("receive")) {
                this.receiveMessages(session);
            } else {
                throw new java.lang.IllegalStateException();
            }

            conn.close();
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
    }

    private static PrintWriter getOutputWriter() {
        return new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out)));
    }

    void sendMessages(Session session) throws JMSException {
        PrintWriter out = getOutputWriter();
        MessageProducer producer = session.createProducer(this.queue);
        producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
        producer.setDisableMessageTimestamp(true);
        
        byte[] body = new byte[this.bytes];
        Arrays.fill(body, (byte) 120);
        
        while (this.sent < this.messages) {
            BytesMessage message = session.createBytesMessage();
            long stime = System.currentTimeMillis();

            message.writeBytes(body);
            message.setLongProperty("SendTime", stime);
            
            producer.send(message);

            out.printf("%s,%d\n", message.getJMSMessageID(), stime);

            this.sent += 1;
        }

        out.flush();
    }

    void receiveMessages(Session session) throws JMSException {
        PrintWriter out = getOutputWriter();
        MessageConsumer consumer = session.createConsumer(this.queue);

        while (this.received < this.messages) {
            BytesMessage message = (BytesMessage) consumer.receive();

            if (message == null) {
                throw new RuntimeException("Null receive");
            }

            String id = message.getJMSMessageID();
            long stime = message.getLongProperty("SendTime");
            long rtime = System.currentTimeMillis();

            out.printf("%s,%d,%d\n", id, stime, rtime);
            
            this.received += 1;
        }

        out.flush();
    }
}
