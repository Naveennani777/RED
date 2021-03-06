/*
 * Copyright 2015 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.robotframework.ide.eclipse.main.plugin.debug.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.debug.core.model.IValue;

/**
 * @author mmarzec
 *
 */
public abstract class RobotDebugValue extends RobotDebugElement implements IValue {

    private String value;
    
    protected RobotDebugValue(final RobotDebugTarget target, final String value) {
        super(target);
        this.value = value;
    }

    public static RobotDebugValue createFromValue(final RobotDebugVariable parent, final Object value) {
        if (value instanceof List<?>) {
            return createFromList(parent, (List<?>) value);

        } else if (value instanceof Map<?, ?>) {
            return createFromMap(parent, (Map<?, ?>) value);

        } else {
            return new RobotDebugValueOfScalar(parent.getDebugTarget(), value.toString());
        }
    }

    private static RobotDebugValue createFromList(final RobotDebugVariable parent, final List<?> list) {
        final List<RobotDebugVariable> nestedVariables = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            nestedVariables.add(new RobotDebugVariable(parent, "[" + i + "]", list.get(i)));
        }
        return new RobotDebugValueOfList(parent.getDebugTarget(), nestedVariables);
    }

    private static RobotDebugValue createFromMap(final RobotDebugVariable parent, final Map<?, ?> map) {
        final List<RobotDebugVariable> nestedVariables = new ArrayList<>();
        for (final Entry<?, ?> entry : map.entrySet()) {
            nestedVariables.add(new RobotDebugVariable(parent, entry.getKey().toString(), entry.getValue()));
        }
        return new RobotDebugValueOfDictionary(parent.getDebugTarget(), nestedVariables);
    }

    @Override
    public String getReferenceTypeName() {
        try {
            Integer.parseInt(value);
            return "integer";
        } catch (final NumberFormatException e) {
            return "text";
        }
    }

    public boolean supportsModification() {
        return false;
    }

    @Override
    public String getValueString() {
        return value;
    }

    void setValue(final String value) {
        this.value = value;
    }

    public abstract String getDetailedValue();

    @Override
    public boolean isAllocated() {
        return true;
    }

    @Override
    public boolean hasVariables() {
        return false;
    }

    @Override
    public RobotDebugVariable[] getVariables() {
        return new RobotDebugVariable[0];
    }
}
