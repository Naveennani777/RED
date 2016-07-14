/*
 * Copyright 2016 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.robotframework.ide.eclipse.main.plugin.tableeditor.cases;

import org.eclipse.nebula.widgets.nattable.data.IRowDataProvider;
import org.robotframework.ide.eclipse.main.plugin.RedPlugin;
import org.robotframework.ide.eclipse.main.plugin.model.RobotCase;
import org.robotframework.ide.eclipse.main.plugin.model.RobotCasesSection;
import org.robotframework.ide.eclipse.main.plugin.model.RobotElement;
import org.robotframework.ide.eclipse.main.plugin.model.RobotKeywordCall;
import org.robotframework.ide.eclipse.main.plugin.tableeditor.AddingToken;
import org.robotframework.ide.eclipse.main.plugin.tableeditor.RobotEditorCommandsStack;
import org.robotframework.ide.eclipse.main.plugin.tableeditor.cases.CasesMatchesCollection.CasesFilter;
import org.robotframework.red.nattable.IFilteringDataProvider;

import ca.odell.glazedlists.FilterList;
import ca.odell.glazedlists.GlazedLists;
import ca.odell.glazedlists.SortedList;
import ca.odell.glazedlists.TreeList;
import ca.odell.glazedlists.matchers.Matcher;

public class CasesDataProvider implements IFilteringDataProvider, IRowDataProvider<Object> {

    private final AddingToken casesAddingToken = new AddingToken(null, CasesAdderState.CASE);

    private RobotCasesSection section;

    private SortedList<Object> casesSortedList;
    private FilterList<Object> filterList;
    private TreeList<Object> cases;
    
    private final CasesColumnsPropertyAccessor propertyAccessor;
    
    private final CasesElementsTreeFormat casesTreeFormat;

    private CasesFilter filter;
    
    CasesDataProvider(final RobotEditorCommandsStack commandsStack,
            final RobotCasesSection section) {
        this.section = section;
        this.propertyAccessor = new CasesColumnsPropertyAccessor(commandsStack, countColumnsNumber());
        this.casesTreeFormat = new CasesElementsTreeFormat();
        createFrom(section);
    }
    
    void setInput(final RobotCasesSection section) {
        propertyAccessor.setColumnCount(countColumnsNumber());
        this.section = section;
        createFrom(section);
    }

    private int countColumnsNumber() {
        return calculateLongestArgumentsLength() + 2; // case name + args + comment
    }

    private int calculateLongestArgumentsLength() {
        int max = RedPlugin.getDefault().getPreferences().getMimalNumberOfArgumentColumns();
        if (cases != null) {
            for (final Object element : cases) {
                if (element instanceof RobotKeywordCall) {
                    max = Math.max(max, ((RobotKeywordCall) element).getArguments().size());
                }
            }
        }
        return max;
    }

    private void createFrom(final RobotCasesSection section) {
        if (cases == null) {
            casesSortedList = new SortedList<>(GlazedLists.<Object> eventListOf(), null);
            filterList = new FilterList<>(casesSortedList);
            cases = new TreeList<>(filterList, casesTreeFormat, TreeList.nodesStartExpanded());
        }
        if (section != null) {
            casesSortedList.clear();
            
            for (final RobotCase robotCase : section.getChildren()) {
                casesSortedList.add(robotCase);
                casesSortedList.addAll(robotCase.getChildren());
                casesSortedList.add(new AddingToken(robotCase, CasesAdderState.CALL));
            }
        }

    }
    
    SortedList<Object> getSortedList() {
        return casesSortedList;
    }
    
    TreeList<Object> getTreeList() {
        return cases;
    }

    RobotCasesSection getInput() {
        return section;
    }

    CasesElementsTreeFormat getCasesTreeFormat() {
        return casesTreeFormat;
    }

    CasesColumnsPropertyAccessor getPropertyAccessor() {
        return propertyAccessor;
    }
    
    @Override
    public int getColumnCount() {
        return propertyAccessor.getColumnCount();
    }

    @Override
    public int getRowCount() {
        if (section != null) {
            final int addingTokens = isFilterSet() ? 0 : 1;
            return cases.size() + addingTokens;
        }
        return 0;
    }

    @Override
    public Object getDataValue(final int column, final int row) {
        if (section != null) {
            final Object element = getRowObject(row);
            if (element instanceof RobotElement) {
                return propertyAccessor.getDataValue(element, column);
            } else if (element instanceof AddingToken && column == 0) {
                return ((AddingToken) element).getLabel();
            }
        }
        return "";
    }
    
    @Override
    public void setDataValue(final int columnIndex, final int rowIndex, final Object newValue) {
        if (newValue instanceof RobotElement) {
            return;
        }
        final Object element = getRowObject(rowIndex);
        if (element instanceof RobotElement) {
            propertyAccessor.setDataValue(element, columnIndex, newValue);
        }
    }

    @Override
    public Object getRowObject(final int rowIndex) {
        if (rowIndex < cases.size()) {
            return cases.get(rowIndex);
        } else if (rowIndex == cases.size()) {
            return casesAddingToken;
        }
        return null;
    }

    @Override
    public int indexOfRowObject(final Object rowObject) {
        if (section != null) {
            return cases.indexOf(rowObject);
        } else if (rowObject == casesAddingToken) {
            return cases.size();
        }
        return -1;
    }

    @Override
    public boolean isFilterSet() {
        return filter != null;
    }

    void setFilter(final CasesFilter filter) {
        this.filter = filter;

        if (filter == null) {
            filterList.setMatcher(null);
        } else {
            filterList.setMatcher(new Matcher<Object>() {
                @Override
                public boolean matches(final Object item) {
                    return filter.isMatching(item);
                }
            });
        }
    }

    boolean isProvided(final RobotElement element) {
        return cases.contains(element);
    }
}