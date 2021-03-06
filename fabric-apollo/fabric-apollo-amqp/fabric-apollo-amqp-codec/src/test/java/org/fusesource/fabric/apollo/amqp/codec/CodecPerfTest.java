/*
 * Copyright (C) 2010-2011, FuseSource Corp.  All rights reserved
 *
 *    http://fusesource.com
 *
 * The software in this package is published under the terms of the
 * CDDL license, a copy of which has been included with this distribution
 * in the license.txt file
 */

package org.fusesource.fabric.apollo.amqp.codec;

import org.fusesource.fabric.apollo.amqp.codec.api.AnnotatedMessage;
import org.fusesource.fabric.apollo.amqp.codec.api.BareMessage;
import org.fusesource.fabric.apollo.amqp.codec.api.MessageFactory;
import org.fusesource.fabric.apollo.amqp.codec.types.*;
import org.fusesource.hawtbuf.Buffer;
import org.junit.Assert;
import org.junit.Test;

import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.fusesource.fabric.apollo.amqp.codec.TestSupport.encodeDecode;
import static org.fusesource.fabric.apollo.amqp.codec.TestSupport.writeRead;

/**
 *
 */
public class CodecPerfTest {

    interface TestLoop {
        public void loop(Long iteration) throws Exception;
    }

    @Test
    public void messagePerfTest() throws Exception {
        execute(1000000, new TestLoop() {

            public void loop(Long iteration) throws Exception {
                BareMessage message = MessageFactory.createDataMessage(
                        Buffer.ascii("Message iteration " + iteration),
                        new Properties(new MessageIDString(iteration.toString()), Buffer.ascii("user1"), new AMQPString("foo"), null, null, null, null, null, null, new Date())

                );

                AnnotatedMessage annotatedMessage = MessageFactory.createAnnotatedMessage(message, new Header(false, null, null, null, 0L));

                AnnotatedMessage out = encodeDecode(annotatedMessage, false);
            }
        });

    }

    @Test
    public void transferPerfTest() throws Exception {
        execute(7000000, new TestLoop() {
            public void loop(Long iteration) throws Exception {
                Transfer in = new Transfer(0L, iteration + 1, Buffer.ascii(iteration.toString()), 0L);
                Transfer out = writeRead(in, false);
            }
        });

    }

    public void execute(final long max, final TestLoop loop) throws Exception {
        final AtomicLong i = new AtomicLong(0);
        final CountDownLatch latch = new CountDownLatch(1);

        Executors.newSingleThreadExecutor().execute(new Runnable() {
            public void run() {
                System.out.println("Starting loop");
                while (i.incrementAndGet() < max) {
                    try {
                        loop.loop(i.get());
                    } catch (Exception e) {
                        latch.countDown();
                        e.printStackTrace();
                        return;
                    }
                }
                System.out.println("Finished loop");
                latch.countDown();
            }
        });

        int last = 0;
        while (latch.await(1, TimeUnit.SECONDS) == false) {
            last = printMsgsPerSec(i.intValue(), last);
        }
        last = printMsgsPerSec(i.intValue(), last);
        Assert.assertTrue(i.get() == max);
    }

    public int printMsgsPerSec(int current, int last) {
        int sample = current - last;
        System.out.println("msgs/s : " + (sample));
        return last + sample;
    }
}
