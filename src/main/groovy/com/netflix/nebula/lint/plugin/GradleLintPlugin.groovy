package com.netflix.nebula.lint.plugin

import org.codenarc.analyzer.StringSourceAnalyzer
import org.codenarc.rule.Rule
import org.codenarc.rule.Violation
import org.codenarc.ruleregistry.RuleRegistryInitializer
import org.codenarc.ruleset.CompositeRuleSet
import org.codenarc.ruleset.RuleSet
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.internal.service.ServiceRegistry
import org.gradle.logging.StyledTextOutput
import org.gradle.logging.StyledTextOutputFactory

import java.util.concurrent.atomic.AtomicInteger

class GradleLintPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        def lintExt = project.extensions.create('lint', GradleLintExtension)
        project.tasks.create('autoCorrectLint', GradleLintCorrectionTask)

        project.afterEvaluate {
            def textOutputFactory = (project.services as ServiceRegistry).get(StyledTextOutputFactory)
            def textOutput = textOutputFactory.create('lint')

            def registry = new LintRuleRegistry(getClass().classLoader)
            def ruleSet = configureRuleSet(lintExt.rules.collect { registry.findRule(it) }.findAll { it != null })
            def violations = new StringSourceAnalyzer(project.buildFile.text).analyze(ruleSet).violations

            project.gradle.taskGraph.whenReady { TaskExecutionGraph graph ->
                def latch = new AtomicInteger(graph.allTasks.size())
                graph.afterTask { task, taskState ->
                    if(latch.decrementAndGet() == 0) {
                        def buildFilePath = relPath(project.rootDir, project.buildFile).path
                        def totalBySeverity = violations.countBy { it.rule.priority <= 3 ? 'warning' : 'error' }

                        if(!violations.isEmpty()) {
                            textOutput.withStyle(StyledTextOutput.Style.UserInput).text('\nThis build contains lint violations. ')
                            textOutput.println('A complete listing of the violations follows. ')

                            if(totalBySeverity.error) {
                                textOutput.text('Because some were serious, the overall build status has been changed to ')
                                    .withStyle(StyledTextOutput.Style.Failure).println("FAILED\n")
                            }
                            else {
                                textOutput.println('Because none were serious, the build\'s overall status was unaffected.\n')
                            }
                        }

                        violations.eachWithIndex { Violation v, Integer i ->
                            def severity = v.rule.priority <= 3 ? 'warning' : 'error'

                            textOutput.withStyle(StyledTextOutput.Style.Failure).text(severity.padRight(10))
                            textOutput.text(v.rule.name.padRight(25))
                            textOutput.withStyle(StyledTextOutput.Style.Description).println(v.message)

                            textOutput.withStyle(StyledTextOutput.Style.UserInput).println(buildFilePath + ':' + v.lineNumber)
                            textOutput.text(v.sourceLine)
                            textOutput.println('\n') // extra space between violations
                        }

                        if(!violations.isEmpty()) {
                            textOutput.withStyle(StyledTextOutput.Style.Failure)
                                    .text("\u2716 ${buildFilePath}: ${violations.size()} problem${violations.isEmpty() ? '' : 's'} (${totalBySeverity.error ?: 0} errors, ${totalBySeverity.warning ?: 0} warnings)".toString())
                            textOutput.println()

                            if(totalBySeverity.error)
                                throw new LintCheckFailedException() // fail the whole build
                        }
                    }
                }
            }
        }
    }

    protected static File relPath(File root, File f) {
        new File(root.toURI().relativize(f.toURI()).toString())
    }

    protected static RuleSet configureRuleSet(List<Rule> rules) {
        new RuleRegistryInitializer().initializeRuleRegistry()

        def ruleSet = new CompositeRuleSet()
        rules.each { ruleSet.addRule(it) }
        ruleSet
    }
}