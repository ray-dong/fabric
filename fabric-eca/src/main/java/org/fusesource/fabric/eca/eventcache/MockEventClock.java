/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2011 FuseSource Corporation, a Progress Software company. All rights reserved.
 *
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License("CDDL") (the "License").
 * You may not use this file except in compliance with the License. You can obtain
 * a copy of the License at http://www.opensource.org/licenses/CDDL-1.0.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at resources/META-INF/LICENSE.txt.
 *
 */
package org.fusesource.fabric.eca.eventcache;

import java.util.concurrent.TimeUnit;

public class MockEventClock implements EventClock {

    private long currentTime;

    public long currentTimeMillis() {
        return currentTime;
    }

    public void setCurrentTime(int value, TimeUnit unit) {
        currentTime = TimeUnit.MILLISECONDS.convert(value, unit);
    }

    public void advanceClock(int value, TimeUnit unit) {
        currentTime += TimeUnit.MILLISECONDS.convert(value, unit);
    }

    public void retreatClock(int value, TimeUnit unit) {
        currentTime -= TimeUnit.MILLISECONDS.convert(value, unit);
    }
}
