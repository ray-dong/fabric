/*
 * Copyright (C) 2011, FuseSource Corp.  All rights reserved.
 * http://fusesource.com
 *
 * The software in this package is published under the terms of the
 * CDDL license a copy of which has been included with this distribution
 * in the license.txt file.
 */
package org.fusesource.fabric.camel.c24io.old;

import java.util.List;

import biz.c24.testtransactions.Transactions;
import org.apache.camel.test.CamelTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.fusesource.fabric.camel.c24io.C24IOSink;
import org.fusesource.fabric.camel.c24io.C24IOSource;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version $Revision$
 */
public class ReformatViaProcessorsTest extends CamelTestSupport {
    public void testC24() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(1);

        resultEndpoint.assertIsSatisfied(10000L);

        List<Exchange> list = resultEndpoint.getReceivedExchanges();
        Exchange exchange = list.get(0);
        Message in = exchange.getIn();

        String text = in.getBody(String.class);
        log.info("Received: " + text);
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("file:src/test/data?noop=true").
                        process(C24IOSource.c24Source(Transactions.class)).
                        process(C24IOSink.c24Sink().tagValuePair()).
                        to("mock:result");
            }
        };
    }
}