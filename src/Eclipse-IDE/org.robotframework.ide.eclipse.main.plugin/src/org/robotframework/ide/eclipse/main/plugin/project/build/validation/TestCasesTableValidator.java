/*
 * Copyright 2015 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.robotframework.ide.eclipse.main.plugin.project.build.validation;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newHashSet;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.text.Position;
import org.robotframework.ide.core.testData.model.table.RobotExecutableRow;
import org.robotframework.ide.core.testData.model.table.RobotExecutableRow.ExecutionLineDescriptor;
import org.robotframework.ide.core.testData.model.table.TestCaseTable;
import org.robotframework.ide.core.testData.model.table.testCases.TestCase;
import org.robotframework.ide.core.testData.text.read.recognizer.RobotToken;
import org.robotframework.ide.eclipse.main.plugin.model.RobotCasesSection;
import org.robotframework.ide.eclipse.main.plugin.model.RobotKeywordDefinition;
import org.robotframework.ide.eclipse.main.plugin.model.RobotSuiteFile;
import org.robotframework.ide.eclipse.main.plugin.model.RobotVariable;
import org.robotframework.ide.eclipse.main.plugin.model.locators.ContinueDecision;
import org.robotframework.ide.eclipse.main.plugin.model.locators.KeywordDefinitionLocator;
import org.robotframework.ide.eclipse.main.plugin.model.locators.KeywordDefinitionLocator.KeywordDetector;
import org.robotframework.ide.eclipse.main.plugin.model.locators.VariableDefinitionLocator;
import org.robotframework.ide.eclipse.main.plugin.model.locators.VariableDefinitionLocator.VariableDetector;
import org.robotframework.ide.eclipse.main.plugin.project.build.ProblemPosition;
import org.robotframework.ide.eclipse.main.plugin.project.build.ProblemsReportingStrategy;
import org.robotframework.ide.eclipse.main.plugin.project.build.RobotArtifactsValidator.ModelUnitValidator;
import org.robotframework.ide.eclipse.main.plugin.project.build.RobotProblem;
import org.robotframework.ide.eclipse.main.plugin.project.build.causes.IProblemCause;
import org.robotframework.ide.eclipse.main.plugin.project.build.causes.KeywordsProblem;
import org.robotframework.ide.eclipse.main.plugin.project.build.causes.TestCasesProblem;
import org.robotframework.ide.eclipse.main.plugin.project.build.causes.VariablesProblem;
import org.robotframework.ide.eclipse.main.plugin.project.library.KeywordSpecification;
import org.robotframework.ide.eclipse.main.plugin.project.library.LibrarySpecification;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Range;

class TestCasesTableValidator implements ModelUnitValidator {

    private static final String VARIABLE_PATTERN = "[@$&%]\\{[^\\{]+\\}";


    private final Optional<RobotCasesSection> testCaseSection;

    private final ProblemsReportingStrategy reporter = new ProblemsReportingStrategy();

    TestCasesTableValidator(final Optional<RobotCasesSection> section) {
        this.testCaseSection = section;
    }

    @Override
    public void validate(final IProgressMonitor monitor) throws CoreException {
        if (!testCaseSection.isPresent()) {
            return;
        }
        final RobotSuiteFile suiteModel = testCaseSection.get().getSuiteFile();
        final TestCaseTable casesTable = (TestCaseTable) testCaseSection.get().getLinkedElement();
        final List<TestCase> cases = casesTable.getTestCases();

        reportEmptyCases(suiteModel.getFile(), cases);
        reportDuplicatedCases(suiteModel.getFile(), cases);
        reportUnkownKeywords(suiteModel, reporter, findExecutableRows(cases));
        reportUnknownVariables(suiteModel, cases);
    }

    private void reportEmptyCases(final IFile file, final List<TestCase> cases) {
        for (final TestCase testCase : cases) {
            final RobotToken caseName = testCase.getTestName();
            final IProblemCause cause = TestCasesProblem.EMPTY_CASE;
            reportEmptyExecutableRows(file, reporter, caseName, testCase.getTestExecutionRows(), cause);
        }
    }

    static void reportEmptyExecutableRows(final IFile file, final ProblemsReportingStrategy reporter,
            final RobotToken def, final List<? extends RobotExecutableRow<?>> executables,
            final IProblemCause causeToReport) {
        if (executables.isEmpty()) {
            final String name = def.getText().toString();

            final RobotProblem problem = RobotProblem.causedBy(causeToReport).formatMessageWith(name);
            final ProblemPosition position = new ProblemPosition(def.getLineNumber(),
                    Range.closed(def.getStartOffset(), def.getStartOffset() + name.length()));
            final Map<String, Object> arguments = new HashMap<>();
            arguments.put("name", name);
            reporter.handleProblem(problem, file, position, arguments);
        }
    }

    private void reportDuplicatedCases(final IFile file, final List<TestCase> cases) {
        final Set<String> duplicatedNames = newHashSet();

        for (final TestCase case1 : cases) {
            for (final TestCase case2 : cases) {
                if (case1 != case2) {
                    final String case1Name = case1.getTestName().getText().toString();
                    final String case2Name = case2.getTestName().getText().toString();

                    if (case1Name.equalsIgnoreCase(case2Name)) {
                        duplicatedNames.add(case1Name.toLowerCase());
                    }
                }
            }
        }

        for (final TestCase testCase : cases) {
            final RobotToken caseName = testCase.getTestName();
            final String name = caseName.getText().toString();

            if (duplicatedNames.contains(name.toLowerCase())) {
                final RobotProblem problem = RobotProblem.causedBy(TestCasesProblem.DUPLICATED_CASE)
                        .formatMessageWith(name);
                final ProblemPosition position = new ProblemPosition(caseName.getLineNumber(),
                        Range.closed(caseName.getStartOffset(), caseName.getStartOffset() + name.length()));
                final Map<String, Object> additionalArguments = Maps.newHashMap();
                additionalArguments.put("name", name);
                reporter.handleProblem(problem, file, position, additionalArguments);
            }
        }

    }

    private static List<RobotExecutableRow<?>> findExecutableRows(final List<TestCase> cases) {
        final List<RobotExecutableRow<?>> executables = newArrayList();
        for (final TestCase testCase : cases) {
            executables.addAll(testCase.getTestExecutionRows());
        }
        return executables;
    }

    static void reportUnkownKeywords(final RobotSuiteFile robotSuiteFile, final ProblemsReportingStrategy reporter,
            final List<RobotExecutableRow<?>> executables) {
        final Set<String> names = collectAccessibleKeywordNames(robotSuiteFile);

        for (final RobotExecutableRow<?> executable : executables) {
            final RobotToken keywordName = executable.buildLineDescription().getFirstAction();
            if (keywordName == null || keywordName.getText().toString().trim().startsWith("#")) {
                continue;
            }
            final String name = keywordName.getText().toString();
            if (!names.contains(name.toLowerCase())) {
                final RobotProblem problem = RobotProblem.causedBy(KeywordsProblem.UNKNOWN_KEYWORD)
                        .formatMessageWith(name);
                final ProblemPosition position = new ProblemPosition(keywordName.getLineNumber(),
                        Range.closed(keywordName.getStartOffset(), keywordName.getStartOffset() + name.length()));
                final Map<String, Object> additionalArguments = Maps.newHashMap();
                additionalArguments.put("name", name);
                reporter.handleProblem(problem, robotSuiteFile.getFile(), position, additionalArguments);
            }
        }
    }

    private static Set<String> collectAccessibleKeywordNames(final RobotSuiteFile robotSuiteFile) {
        final Set<String> names = new HashSet<>();
        new KeywordDefinitionLocator(robotSuiteFile, false).locateKeywordDefinition(new KeywordDetector() {

            @Override
            public ContinueDecision libraryKeywordDetected(final LibrarySpecification libSpec,
                    final KeywordSpecification kwSpec) {
                names.add(kwSpec.getName().toLowerCase());
                return ContinueDecision.CONTINUE;
            }

            @Override
            public ContinueDecision keywordDetected(final RobotSuiteFile file, final RobotKeywordDefinition keyword) {
                names.add(keyword.getName().toLowerCase());
                return ContinueDecision.CONTINUE;
            }
        });
        return names;
    }

    private void reportUnknownVariables(final RobotSuiteFile suiteModel,
            final List<TestCase> cases) {        
        final ImmutableSet<String> variables = collectAccessibleVariables(suiteModel);

        for (final TestCase testCase : cases) {
            reportUnknownVariables(suiteModel.getFile(), reporter, testCase.getTestExecutionRows(), variables);
        }
    }

    static ImmutableSet<String> collectAccessibleVariables(final RobotSuiteFile suiteModel) {
        final Builder<String> setBuilder = ImmutableSet.builder();
        new VariableDefinitionLocator(suiteModel).locateVariableDefinition(new VariableDetector() {
            @Override
            public ContinueDecision variableDetected(final RobotSuiteFile file, final RobotVariable variable) {
                setBuilder.add((variable.getPrefix() + variable.getName() + variable.getSuffix()).toLowerCase());
                return ContinueDecision.CONTINUE;
            }

            @Override
            public ContinueDecision globalVariableDetected(final String name, final Object value) {
                setBuilder.add(name.toLowerCase());
                return ContinueDecision.CONTINUE;
            }
        });
        return setBuilder.build();
    }

    static void reportUnknownVariables(final IFile file, final ProblemsReportingStrategy reporter,
            final List<? extends RobotExecutableRow<?>> executables, final ImmutableSet<String> variables) {
        final Set<String> definedVariables = newHashSet(variables);

        for (final RobotExecutableRow<?> row : executables) {
            final ExecutionLineDescriptor lineDescription = row.buildLineDescription();
            definedVariables.addAll(extractVariableNames(lineDescription.getAssignments()));

            final RobotToken token = lineDescription.getFirstAction();

            final List<VariableInsideCell> usedParameters = extractVariables(lineDescription.getParameters());
            for (final VariableInsideCell usedParameter : usedParameters) {
                if (!definedVariables.contains(usedParameter.name.toLowerCase())) {
                    final RobotProblem problem = RobotProblem.causedBy(VariablesProblem.UNDECLARED_VARIABLE_USE)
                            .formatMessageWith(usedParameter.name);
                    final ProblemPosition position = new ProblemPosition(token.getLineNumber(),
                            Range.closed(usedParameter.position.offset,
                                    usedParameter.position.offset + usedParameter.position.length));
                    final Map<String, Object> additionalArguments = Maps.newHashMap();
                    additionalArguments.put("name", usedParameter.name);
                    reporter.handleProblem(problem, file, position, additionalArguments);
                }
            }
        }
    }

    @VisibleForTesting
    static List<String> extractVariableNames(final List<RobotToken> assignments) {
        return newArrayList(
                Iterables.transform(extractVariables(assignments), new Function<VariableInsideCell, String>() {
                    @Override
                    public String apply(final VariableInsideCell var) {
                        return var.name.toLowerCase();
                    }
                }));
    }

    private static List<VariableInsideCell> extractVariables(final List<RobotToken> tokens) {
        final List<VariableInsideCell> vars = newArrayList();

        for (final RobotToken token : tokens) {
            vars.addAll(extractVariables(token.getText().toString(), token.getStartOffset()));
        }
        return vars;
    }

    private static List<VariableInsideCell> extractVariables(final String content, final int cellOffset) {
        final List<VariableInsideCell> variables = newArrayList();
        final Matcher matcher = Pattern.compile(VARIABLE_PATTERN).matcher(content);
        
        while (matcher.find()) {
            final String name = matcher.group(0);
            final Position position = new Position(cellOffset + matcher.start(), name.length());
            variables.add(new VariableInsideCell(name, position));
        }
        return variables;
    }

    private static class VariableInsideCell {

        private final String name;

        private final Position position;

        VariableInsideCell(final String name, final Position position) {
            this.name = name;
            this.position = position;
        }
    }
}
