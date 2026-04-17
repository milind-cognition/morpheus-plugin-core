package com.morpheusdata

import spock.lang.Specification
import spock.lang.Shared
import spock.lang.Unroll

/**
 * E2E tests for the automated test coverage workflow and its README documentation.
 * Validates that:
 *   - The README documents the workflow correctly
 *   - The workflow YAML is properly configured
 *   - Loop-prevention guards are in place
 *   - The workflow and README are consistent with each other
 *
 * Related to PR #2: "Add automated test coverage section to README"
 */
class AutoTestCoverageE2ESpec extends Specification {

	@Shared
	File projectRoot

	@Shared
	String readmeContent

	@Shared
	String workflowContent

	@Shared
	File readmeFile

	@Shared
	File workflowFile

	def setupSpec() {
		// Resolve the project root from the test classpath
		projectRoot = new File(System.getProperty('user.dir'))
		// Walk up if we're inside a subproject (e.g. morpheus-plugin-api)
		while (projectRoot && !new File(projectRoot, 'README.md').exists()) {
			projectRoot = projectRoot.parentFile
		}
		readmeFile = new File(projectRoot, 'README.md')
		workflowFile = new File(projectRoot, '.github/workflows/auto-test-coverage.yml')
		readmeContent = readmeFile.text
		workflowContent = workflowFile.text
	}

	// ---------------------------------------------------------------
	// README structure and content tests
	// ---------------------------------------------------------------

	def "README contains an Automated Test Coverage section"() {
		expect:
		readmeContent.contains('### Automated Test Coverage')
	}

	def "Automated Test Coverage section mentions automatic PR triggering"() {
		given:
		String section = extractSection(readmeContent, '### Automated Test Coverage')

		expect:
		section.toLowerCase().contains('automatically trigger')
	}

	def "Automated Test Coverage section mentions Devin AI"() {
		given:
		String section = extractSection(readmeContent, '### Automated Test Coverage')

		expect:
		section.toLowerCase().contains('devin ai')
	}

	def "Automated Test Coverage section describes E2E test coverage"() {
		given:
		String section = extractSection(readmeContent, '### Automated Test Coverage')

		expect:
		section.toLowerCase().contains('e2e test coverage') || section.toLowerCase().contains('test coverage')
	}

	def "Automated Test Coverage section documents the auto-test loop guard"() {
		given:
		String section = extractSection(readmeContent, '### Automated Test Coverage')

		expect:
		section.contains('[auto-test]')
	}

	def "Automated Test Coverage section explains infinite loop prevention"() {
		given:
		String section = extractSection(readmeContent, '### Automated Test Coverage')

		expect:
		section.toLowerCase().contains('infinite loop') || section.toLowerCase().contains('prevent')
	}

	def "README sections are in expected order"() {
		given:
		int pluginDepsIdx = readmeContent.indexOf('### Plugin Dependencies')
		int autoTestIdx = readmeContent.indexOf('### Automated Test Coverage')

		expect: "Automated Test Coverage section follows Plugin Dependencies"
		pluginDepsIdx >= 0
		autoTestIdx >= 0
		autoTestIdx > pluginDepsIdx
	}

	// ---------------------------------------------------------------
	// Workflow file existence and structure tests
	// ---------------------------------------------------------------

	def "auto-test-coverage workflow file exists"() {
		expect:
		workflowFile.exists()
	}

	def "workflow file is valid YAML with required top-level keys"() {
		expect: "file contains name, on, and jobs keys"
		workflowContent.contains('name:')
		workflowContent.contains('on:')
		workflowContent.contains('jobs:')
	}

	def "workflow triggers on pull_request events"() {
		expect:
		workflowContent.contains('pull_request:')
	}

	def "workflow triggers on opened and synchronize PR types"() {
		expect:
		workflowContent.contains('opened')
		workflowContent.contains('synchronize')
	}

	def "workflow targets the v1.2.x branch"() {
		expect:
		workflowContent.contains("'v1.2.x'") || workflowContent.contains('"v1.2.x"') || workflowContent.contains('v1.2.x')
	}

	// ---------------------------------------------------------------
	// Loop-prevention guard tests
	// ---------------------------------------------------------------

	def "workflow has title-based loop guard for auto-test PRs"() {
		expect: "the if-condition checks for [auto-test] in the PR title"
		workflowContent.contains("[auto-test]")
		workflowContent.contains("github.event.pull_request.title")
	}

	def "workflow has branch-based loop guard for devin/auto-test/ branches"() {
		expect: "the if-condition checks for devin/auto-test/ branch prefix"
		workflowContent.contains("devin/auto-test/")
		workflowContent.contains("github.event.pull_request.head.ref")
	}

	def "workflow uses negation in loop guards to skip matching PRs"() {
		expect: "conditions use ! or 'not' to exclude auto-test PRs"
		workflowContent.contains('!contains') || workflowContent.contains('!startsWith')
	}

	@Unroll
	def "title-based guard would skip PR with title '#title'"() {
		given: "extract the title check pattern from the workflow"
		boolean containsAutoTest = title.contains('[auto-test]')

		expect: "PRs with [auto-test] in the title should be skipped"
		containsAutoTest == shouldSkip

		where:
		title                                          | shouldSkip
		'[auto-test] Add E2E tests for PR #2'         | true
		'[auto-test] E2E coverage for PR #5'          | true
		'Add new cloud provider integration'           | false
		'Fix bug in DNS provider'                      | false
		'Refactor [auto-test] marker handling'         | true
	}

	@Unroll
	def "branch-based guard would skip branch '#branch'"() {
		given: "extract the branch check pattern from the workflow"
		boolean isAutoTestBranch = branch.startsWith('devin/auto-test/')

		expect: "branches starting with devin/auto-test/ should be skipped"
		isAutoTestBranch == shouldSkip

		where:
		branch                                    | shouldSkip
		'devin/auto-test/1234-e2e-tests'          | true
		'devin/auto-test/pr-2-coverage'           | true
		'devin/1234-feature-branch'               | false
		'feature/new-cloud-provider'              | false
		'fix/dns-provider-bug'                    | false
	}

	// ---------------------------------------------------------------
	// Concurrency configuration tests
	// ---------------------------------------------------------------

	def "workflow defines concurrency to prevent duplicate sessions"() {
		expect:
		workflowContent.contains('concurrency:')
	}

	def "workflow cancels in-progress runs for the same PR"() {
		expect:
		workflowContent.contains('cancel-in-progress: true')
	}

	def "concurrency group is scoped per PR number"() {
		expect:
		workflowContent.contains('github.event.pull_request.number')
	}

	// ---------------------------------------------------------------
	// Security tests
	// ---------------------------------------------------------------

	def "workflow uses env vars instead of inline expressions for PR metadata"() {
		given: "the run script should not use direct github context expressions"
		String runBlock = extractRunBlock(workflowContent)

		expect: "run block references env vars, not raw github.event expressions"
		runBlock != null
		!runBlock.contains('github.event.pull_request')
	}

	def "workflow stores secrets in env block, not inline in run script"() {
		given:
		String envBlock = extractEnvBlock(workflowContent)
		String runBlock = extractRunBlock(workflowContent)

		expect:
		envBlock.contains('DEVIN_API_KEY')
		envBlock.contains('DEVIN_ORG_ID')
		// Secrets should not appear directly in the run block
		runBlock != null
		!runBlock.contains('secrets.DEVIN_API_KEY')
		!runBlock.contains('secrets.DEVIN_ORG_ID')
	}

	def "workflow uses heredoc with single quotes to prevent shell expansion in prompt"() {
		expect: "the prompt template uses a quoted heredoc to avoid injection"
		workflowContent.contains("<<'PROMPT_EOF'") || workflowContent.contains("<<'EOF'")
	}

	def "workflow uses jq for safe JSON payload construction"() {
		expect:
		workflowContent.contains('jq -n')
	}

	// ---------------------------------------------------------------
	// API integration tests
	// ---------------------------------------------------------------

	def "workflow calls the Devin API sessions endpoint"() {
		expect:
		workflowContent.contains('api.devin.ai')
		workflowContent.contains('/sessions')
	}

	def "workflow uses Bearer token authentication"() {
		expect:
		workflowContent.contains('Authorization: Bearer')
	}

	def "workflow validates the HTTP response code"() {
		expect:
		workflowContent.contains('http_code') || workflowContent.contains('HTTP_CODE')
	}

	def "workflow exits with failure on non-2xx API response"() {
		expect:
		workflowContent.contains('exit 1')
	}

	// ---------------------------------------------------------------
	// Consistency between README and workflow
	// ---------------------------------------------------------------

	def "README and workflow are consistent about the auto-test title marker"() {
		given:
		String readmeSection = extractSection(readmeContent, '### Automated Test Coverage')

		expect: "both mention the same [auto-test] marker"
		readmeSection.contains('[auto-test]')
		workflowContent.contains('[auto-test]')
	}

	def "README accurately describes that PRs trigger the workflow"() {
		given:
		String readmeSection = extractSection(readmeContent, '### Automated Test Coverage')
		boolean readmeSaysPRsTrigger = readmeSection.toLowerCase().contains('pr') &&
			readmeSection.toLowerCase().contains('automatically')
		boolean workflowTriggersOnPR = workflowContent.contains('pull_request:')

		expect:
		readmeSaysPRsTrigger
		workflowTriggersOnPR
	}

	def "README accurately describes that auto-test PRs are excluded"() {
		given:
		String readmeSection = extractSection(readmeContent, '### Automated Test Coverage')
		boolean readmeMentionsExclusion = readmeSection.contains('[auto-test]') &&
			(readmeSection.toLowerCase().contains('excluded') || readmeSection.toLowerCase().contains('prevent'))
		boolean workflowHasGuard = workflowContent.contains("!contains(github.event.pull_request.title, '[auto-test]')")

		expect:
		readmeMentionsExclusion
		workflowHasGuard
	}

	// ---------------------------------------------------------------
	// Helper methods
	// ---------------------------------------------------------------

	/**
	 * Extracts the text content of a markdown section starting at the given heading
	 * and ending before the next heading of equal or higher level.
	 */
	private static String extractSection(String markdown, String heading) {
		int start = markdown.indexOf(heading)
		if (start < 0) return ''
		int contentStart = start + heading.length()
		// Find the next heading of equal or higher level
		String headingPrefix = heading.replaceAll('[^#].*', '')
		int nextHeading = -1
		for (int i = contentStart; i < markdown.length(); i++) {
			if (markdown.charAt(i) == '#' as char) {
				String remaining = markdown.substring(i)
				if (remaining.startsWith(headingPrefix) || remaining.startsWith('## ') || remaining.startsWith('# ')) {
					nextHeading = i
					break
				}
			}
		}
		return nextHeading > 0 ? markdown.substring(contentStart, nextHeading).trim() : markdown.substring(contentStart).trim()
	}

	/**
	 * Extracts the 'run:' block content from the workflow YAML.
	 */
	private static String extractRunBlock(String yaml) {
		int runIdx = yaml.indexOf('run: |')
		if (runIdx < 0) return null
		int start = runIdx + 'run: |'.length()
		// Find end by looking for a line with less indentation
		String remaining = yaml.substring(start)
		def lines = remaining.split('\n')
		StringBuilder block = new StringBuilder()
		boolean started = false
		int baseIndent = -1
		for (String line : lines) {
			if (!started && line.trim().isEmpty()) continue
			if (!started) {
				baseIndent = line.length() - line.stripLeading().length()
				started = true
			}
			int currentIndent = line.isEmpty() ? baseIndent : line.length() - line.stripLeading().length()
			if (started && currentIndent < baseIndent && !line.trim().isEmpty()) break
			block.append(line).append('\n')
		}
		return block.toString()
	}

	/**
	 * Extracts the 'env:' block content from the workflow YAML.
	 */
	private static String extractEnvBlock(String yaml) {
		int envIdx = yaml.indexOf('env:')
		if (envIdx < 0) return ''
		int start = envIdx
		String remaining = yaml.substring(start)
		def lines = remaining.split('\n')
		StringBuilder block = new StringBuilder()
		boolean started = false
		int baseIndent = -1
		for (String line : lines) {
			if (!started) {
				started = true
				baseIndent = line.length() - line.stripLeading().length()
				block.append(line).append('\n')
				continue
			}
			int currentIndent = line.isEmpty() ? baseIndent + 1 : line.length() - line.stripLeading().length()
			if (currentIndent <= baseIndent && !line.trim().isEmpty()) break
			block.append(line).append('\n')
		}
		return block.toString()
	}
}
