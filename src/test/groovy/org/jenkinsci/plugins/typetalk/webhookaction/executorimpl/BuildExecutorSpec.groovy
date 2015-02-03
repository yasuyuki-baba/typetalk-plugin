package org.jenkinsci.plugins.typetalk.webhookaction.executorimpl

import hudson.Launcher
import hudson.model.*
import hudson.security.ACL
import hudson.security.AuthorizationStrategy
import hudson.security.Permission
import hudson.util.OneShotEvent
import jenkins.model.JenkinsLocationConfiguration
import org.acegisecurity.Authentication
import org.acegisecurity.context.SecurityContextHolder
import org.acegisecurity.providers.TestingAuthenticationToken
import org.jenkinsci.plugins.typetalk.webhookaction.WebhookExecutor
import org.jenkinsci.plugins.typetalk.webhookaction.WebhookRequest
import org.junit.Rule
import org.jvnet.hudson.test.JenkinsRule
import org.jvnet.hudson.test.TestBuilder
import org.kohsuke.stapler.StaplerResponse
import spock.lang.Specification
import spock.lang.Unroll

import javax.servlet.http.HttpServletResponse

class BuildExecutorSpec extends Specification {

    @Rule JenkinsRule j

    def req = Mock(WebhookRequest)
    def res = Mock(StaplerResponse)

    AbstractProject project
    def buildStarted = new OneShotEvent()

    WebhookExecutor executor

    def setup() {
        res.writer >> new PrintWriter(new StringWriter())
    }

    def "execute : project is not found"() {
        setup:
        setUpRootUrl()
        executor = new BuildExecutor(req, res, "typetalk-plugin", [])

        when:
        executor.execute()

        then:
        1 * res.setStatus(HttpServletResponse.SC_OK)
        !isBuilt()
   }

    def "execute : no parameter"() {
        setup:
        setUpRootUrl()
        setUpProject([])
        executor = new BuildExecutor(req, res, "typetalk-plugin", [])

        when:
        executor.execute()
        buildStarted.block()

        then:
        1 * res.setStatus(HttpServletResponse.SC_OK)
        isBuilt()
    }

    def "execute : parameter without key"() {
        setup:
        setUpRootUrl()
        setUpProject([
            new StringParameterDefinition("version", null, null)
        ])
        executor = new BuildExecutor(req, res, "typetalk-plugin", ["1.0.0"])

        when:
        executor.execute()
        buildStarted.block()

        then:
        1 * res.setStatus(HttpServletResponse.SC_OK)
        getBuildParameter("version") == "1.0.0"
    }

    def "execute : single parameter"() {
        setup:
        setUpRootUrl()
        setUpProject([
            new StringParameterDefinition("version", null, null)
        ])
        executor = new BuildExecutor(req, res, "typetalk-plugin", ["version=1.0.0"])

        when:
        executor.execute()
        buildStarted.block()

        then:
        1 * res.setStatus(HttpServletResponse.SC_OK)
        getBuildParameter("version") == "1.0.0"
    }

    def "execute : multiple parameters"() {
        setup:
        setUpRootUrl()
        setUpProject([
            new StringParameterDefinition("version", null, null),
            new StringParameterDefinition("env", null, null)
        ])
        executor = new BuildExecutor(req, res, "typetalk-plugin", ["version=1.0.0", "env=stage"])

        when:
        executor.execute()
        buildStarted.block()

        then:
        1 * res.setStatus(HttpServletResponse.SC_OK)
        getBuildParameter("version") == "1.0.0"
        getBuildParameter("env") == "stage"
    }

    def "execute : default parameter"() {
        setup:
        setUpRootUrl()
        setUpProject([
            new StringParameterDefinition("version", "1.0.0-SNAPSHOT", null)
        ])
        executor = new BuildExecutor(req, res, "typetalk-plugin", [])

        when:
        executor.execute()
        buildStarted.block()

        then:
        1 * res.setStatus(HttpServletResponse.SC_OK)
        getBuildParameter("version") == "1.0.0-SNAPSHOT"
    }

    def "execute : illegal parameter format"() {
        setup:
        setUpRootUrl()
        setUpProject([
            new StringParameterDefinition("version", null, null),
            new StringParameterDefinition("env", null, null)
        ])
        executor = new BuildExecutor(req, res, "typetalk-plugin", ["version:1.0.0", "env:stage"])

        when:
        executor.execute()
        buildStarted.block()

        then:
        1 * res.setStatus(HttpServletResponse.SC_OK)
        getBuildParameter("version") == null
        getBuildParameter("env") == null
    }

    def "execute : not set without key when multiple parameters are defined"() {
        setup:
        setUpRootUrl()
        setUpProject([
            new StringParameterDefinition("version", null, null),
            new StringParameterDefinition("env", null, null)
        ])
        executor = new BuildExecutor(req, res, "typetalk-plugin", ["1.0.0"])

        when:
        executor.execute()
        buildStarted.block()

        then:
        1 * res.setStatus(HttpServletResponse.SC_OK)
        getBuildParameter("version") == null
        getBuildParameter("env") == null
    }

    @Unroll
    def "execute : #username"() {
        setup:
        setUpRootUrl()
        setUpProject([])
        executor = new BuildExecutor(req, res, "typetalk-plugin", [])

        def old = ACL.impersonate(new TestingAuthenticationToken(username, null, null))
        setUpAuthorizationStrategy()

        when:
        executor.execute()
        if (isBuilt) buildStarted.block()

        then:
        1 * res.setStatus(HttpServletResponse.SC_OK)
        isBuilt() == isBuilt

        cleanup:
        SecurityContextHolder.setContext(old);

        where:
        username         || isBuilt
        "authorized"     || true
        "not authorized" || false
    }

    // --- helper method ---

    def setUpRootUrl() {
        JenkinsLocationConfiguration.get().url = "http://localhost:8080/"
    }

    def setUpProject(spds) {
        project = j.createFreeStyleProject("typetalk-plugin")

        project.quietPeriod = 0
        project.addProperty(new ParametersDefinitionProperty(spds))
        project.buildersList.add(new TestBuilder() {
            @Override
            boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                buildStarted.signal()
                true
            }
        })
    }

    def isBuilt() {
        project?.lastBuild != null
    }

    def getBuildParameter(key) {
        project.lastBuild.getAction(ParametersAction.class).getParameter(key).value
    }

    def setUpAuthorizationStrategy() {
        j.jenkins.setAuthorizationStrategy(new AuthorizationStrategy() {
            @Override
            ACL getRootACL() {
                new ACL() {
                    @Override
                    boolean hasPermission(Authentication a, Permission permission) {
                        a.name == "authorized"
                    }
                }
            }

            @Override
            Collection<String> getGroups() {
                Collections.emptyList()
            }
        })
    }

}
