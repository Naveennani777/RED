/*
 * Copyright 2017 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.rf.ide.core.execution.server.response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.codehaus.jackson.map.ObjectMapper;

import com.google.common.collect.ImmutableMap;

public final class ResumeExecution implements ServerResponse {

    @Override
    public String toMessage() {
        try {
            final List<Object> arguments = new ArrayList<>();
            final Map<String, Object> value = ImmutableMap.of("resume", arguments);

            return new ObjectMapper().writeValueAsString(value);
        } catch (final IOException e) {
            throw new ResponseException("Unable to serialize resume response arguments to json", e);
        }
    }

}
