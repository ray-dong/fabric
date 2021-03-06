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
import org.fusesource.fabric.apollo.amqp.codec.api.MessageFactory;
import org.fusesource.fabric.apollo.amqp.codec.api.ValueMessage;
import org.fusesource.fabric.apollo.amqp.codec.marshaller.MessageSupport;
import org.fusesource.fabric.apollo.amqp.codec.types.AMQPString;
import org.fusesource.fabric.apollo.amqp.codec.types.AMQPTransportFrame;
import org.fusesource.fabric.apollo.amqp.codec.types.Begin;
import org.fusesource.fabric.apollo.amqp.codec.types.Transfer;
import org.fusesource.hawtbuf.Buffer;
import org.fusesource.hawtbuf.DataByteArrayInputStream;
import org.fusesource.hawtbuf.DataByteArrayOutputStream;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class FrameTest {

    @Test
    public void testBeginFrame() throws Exception {
        Begin in = new Begin();
        in.setIncomingWindow(10L);
        in.setOutgoingWindow(10L);
        in.setNextOutgoingID(0L);

        Begin out = (Begin)TestSupport.encodeDecode(new AMQPTransportFrame(0, in)).getPerformative();
        assertEquals(in.toString(), out.toString());
    }

    @Test
    public void testTransferFrame() throws Exception {
        ValueMessage message = MessageFactory.createValueMessage(new AMQPString("HelloWorld!"));
        Transfer transfer = new Transfer(0L, 0L, Buffer.ascii("0").buffer());
        AnnotatedMessage annotatedMessage = MessageFactory.createAnnotatedMessage(message);

        AMQPTransportFrame frame = new AMQPTransportFrame(0, transfer, MessageSupport.toBuffer(annotatedMessage));

        AMQPTransportFrame outFrame = TestSupport.encodeDecode(frame);

        Transfer outTransfer = (Transfer)outFrame.getPerformative();

        System.out.printf("Transfer : %s\n", outTransfer);

        AnnotatedMessage msg = MessageSupport.decodeAnnotatedMessage(outFrame.getPayload());

        System.out.printf("Msg : %s\n", msg);
        assertEquals(transfer.toString(), outTransfer.toString());
        assertEquals(annotatedMessage.toString(), msg.toString());
    }

    @Test
    public void createFrameFromHeaderAndBody() throws Exception {
        ValueMessage message = MessageFactory.createValueMessage(new AMQPString("HelloWorld!"));
        Transfer transfer = new Transfer(0L, 0L, Buffer.ascii("0").buffer());
        AnnotatedMessage annotatedMessage = MessageFactory.createAnnotatedMessage(message);
        AMQPTransportFrame inFrame = new AMQPTransportFrame(0, transfer, MessageSupport.toBuffer(annotatedMessage));

        DataByteArrayOutputStream out = new DataByteArrayOutputStream((int)inFrame.getSize());
        inFrame.write(out);
        Buffer buf = out.toBuffer();


        Buffer header = new Buffer(8);

        DataByteArrayInputStream in = new DataByteArrayInputStream(buf);
        in.read(header.data);
        Buffer body = new Buffer(in.available());
        in.read(body.data);

        AMQPTransportFrame outFrame = new AMQPTransportFrame(header, body);

        Transfer outTransfer = (Transfer)outFrame.getPerformative();
        AnnotatedMessage msg = MessageSupport.decodeAnnotatedMessage(outFrame.getPayload());
        assertEquals(transfer.toString(), outTransfer.toString());
        assertEquals(annotatedMessage.toString(), msg.toString());
    }
}
