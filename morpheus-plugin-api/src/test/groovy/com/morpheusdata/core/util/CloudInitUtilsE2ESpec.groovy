package com.morpheusdata.core.util

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.model.ComputeServer
import com.morpheusdata.model.OsType
import com.morpheusdata.model.PlatformType
import com.morpheusdata.model.TaskResult
import com.morpheusdata.model.VirtualImage
import io.reactivex.rxjava3.core.Single
import spock.lang.Specification
import spock.lang.Unroll

/**
 * End-to-end style tests for {@link CloudInitUtils}.
 *
 * Complements the unit-level CloudInitUtilsSpec (PR #10) by:
 * - Using real domain objects (VirtualImage, OsType, ComputeServer) instead of mocks
 * - Asserting exact shell command strings passed to executeCommandOnServer
 * - Verifying credential propagation and interaction counts
 * - Exercising every PlatformType enum value
 * - Testing sequential disable-then-restore workflows
 */
class CloudInitUtilsE2ESpec extends Specification {

	static final String DISABLE_COMMAND =
		"sudo rm -f /etc/cloud/cloud.cfg.d/99-manual-cache.cfg; " +
		"sudo mv /etc/machine-id /var/tmp/machine-id-old ; sync; sync;"

	static final String RESTORE_COMMAND =
		"sudo bash -c \"echo 'manual_cache_clean: True' >> " +
		"/etc/cloud/cloud.cfg.d/99-manual-cache.cfg\"; " +
		"sudo mv /var/lib/cloud/instance-back /var/lib/cloud/instance; " +
		"sudo cat /var/tmp/machine-id-old > /etc/machine-id ; " +
		"sudo rm /var/tmp/machine-id-old; sync; sync;"

	MorpheusContext morpheusContext

	def setup() {
		morpheusContext = Mock(MorpheusContext)
	}

	// ---- Helper to build a fully-wired ComputeServer with real objects ----

	private ComputeServer buildServer(
		Boolean isCloudInit,
		PlatformType platform,
		String sshUser = "admin",
		String sshPass = "secret"
	) {
		def server = new ComputeServer()
		if (isCloudInit != null) {
			def image = new VirtualImage()
			image.setCloudInit(isCloudInit)
			server.sourceImage = image
		}
		if (platform != null) {
			def osType = new OsType()
			osType.setPlatform(platform)
			server.serverOs = osType
		}
		server.sshUsername = sshUser
		server.sshPassword = sshPass
		return server
	}

	// =======================================================================
	// disableCloudInitCache — exact command verification
	// =======================================================================

	void "disableCloudInitCache sends the exact disable command when all conditions are met"() {
		given:
		def server = buildServer(true, PlatformType.windows)
		def expectedResult = new TaskResult(success: true, exitCode: "0")
		morpheusContext.executeCommandOnServer(
			server, DISABLE_COMMAND, false,
			"admin", "secret",
			null, null, null, null, true, true
		) >> Single.just(expectedResult)

		when:
		def result = CloudInitUtils.disableCloudInitCache(morpheusContext, server)

		then:
		result.is(expectedResult)
	}

	void "disableCloudInitCache invokes executeCommandOnServer exactly once"() {
		given:
		def server = buildServer(true, PlatformType.windows)
		morpheusContext.executeCommandOnServer(
			server, DISABLE_COMMAND, false,
			"admin", "secret",
			null, null, null, null, true, true
		) >> Single.just(new TaskResult())

		when:
		CloudInitUtils.disableCloudInitCache(morpheusContext, server)

		then:
		1 * morpheusContext.executeCommandOnServer(
			server, DISABLE_COMMAND, false,
			"admin", "secret",
			null, null, null, null, true, true
		) >> Single.just(new TaskResult())
	}

	// =======================================================================
	// restoreCloudInitCache — exact command verification
	// =======================================================================

	void "restoreCloudInitCache sends the exact restore command when all conditions are met"() {
		given:
		def server = buildServer(true, PlatformType.windows)
		def expectedResult = new TaskResult(success: true, exitCode: "0")
		morpheusContext.executeCommandOnServer(
			server, RESTORE_COMMAND, false,
			"admin", "secret",
			null, null, null, null, true, true
		) >> Single.just(expectedResult)

		when:
		def result = CloudInitUtils.restoreCloudInitCache(morpheusContext, server)

		then:
		result.is(expectedResult)
	}

	void "restoreCloudInitCache invokes executeCommandOnServer exactly once"() {
		given:
		def server = buildServer(true, PlatformType.windows)
		morpheusContext.executeCommandOnServer(
			server, RESTORE_COMMAND, false,
			"admin", "secret",
			null, null, null, null, true, true
		) >> Single.just(new TaskResult())

		when:
		CloudInitUtils.restoreCloudInitCache(morpheusContext, server)

		then:
		1 * morpheusContext.executeCommandOnServer(
			server, RESTORE_COMMAND, false,
			"admin", "secret",
			null, null, null, null, true, true
		) >> Single.just(new TaskResult())
	}

	// =======================================================================
	// Credential propagation — real objects, different creds
	// =======================================================================

	void "disableCloudInitCache propagates custom SSH credentials"() {
		given:
		def server = buildServer(true, PlatformType.windows, "deploy", "p@ss!")
		morpheusContext.executeCommandOnServer(
			server, DISABLE_COMMAND, false,
			"deploy", "p@ss!",
			null, null, null, null, true, true
		) >> Single.just(new TaskResult())

		when:
		def result = CloudInitUtils.disableCloudInitCache(morpheusContext, server)

		then:
		result != null
		1 * morpheusContext.executeCommandOnServer(
			server, DISABLE_COMMAND, false,
			"deploy", "p@ss!",
			null, null, null, null, true, true
		) >> Single.just(new TaskResult())
	}

	void "restoreCloudInitCache propagates custom SSH credentials"() {
		given:
		def server = buildServer(true, PlatformType.windows, "ops", "s3cure")
		morpheusContext.executeCommandOnServer(
			server, RESTORE_COMMAND, false,
			"ops", "s3cure",
			null, null, null, null, true, true
		) >> Single.just(new TaskResult())

		when:
		def result = CloudInitUtils.restoreCloudInitCache(morpheusContext, server)

		then:
		result != null
		1 * morpheusContext.executeCommandOnServer(
			server, RESTORE_COMMAND, false,
			"ops", "s3cure",
			null, null, null, null, true, true
		) >> Single.just(new TaskResult())
	}

	void "disableCloudInitCache propagates null SSH credentials"() {
		given:
		def server = buildServer(true, PlatformType.windows, null, null)
		morpheusContext.executeCommandOnServer(
			server, DISABLE_COMMAND, false,
			null, null,
			null, null, null, null, true, true
		) >> Single.just(new TaskResult())

		when:
		def result = CloudInitUtils.disableCloudInitCache(morpheusContext, server)

		then:
		result != null
	}

	// =======================================================================
	// TaskResult identity — returned object is the exact same instance
	// =======================================================================

	void "disableCloudInitCache returns the exact TaskResult from executeCommandOnServer"() {
		given:
		def server = buildServer(true, PlatformType.windows)
		def taskResult = new TaskResult(
			success: true,
			exitCode: "0",
			output: "cloud-init cache disabled",
			msg: "done"
		)
		morpheusContext.executeCommandOnServer(
			server, DISABLE_COMMAND, false,
			"admin", "secret",
			null, null, null, null, true, true
		) >> Single.just(taskResult)

		when:
		def result = CloudInitUtils.disableCloudInitCache(morpheusContext, server)

		then:
		result.is(taskResult)
		result.success == true
		result.exitCode == "0"
		result.output == "cloud-init cache disabled"
		result.msg == "done"
	}

	void "restoreCloudInitCache returns the exact TaskResult from executeCommandOnServer"() {
		given:
		def server = buildServer(true, PlatformType.windows)
		def taskResult = new TaskResult(
			success: false,
			exitCode: "1",
			error: "permission denied"
		)
		morpheusContext.executeCommandOnServer(
			server, RESTORE_COMMAND, false,
			"admin", "secret",
			null, null, null, null, true, true
		) >> Single.just(taskResult)

		when:
		def result = CloudInitUtils.restoreCloudInitCache(morpheusContext, server)

		then:
		result.is(taskResult)
		result.success == false
		result.exitCode == "1"
		result.error == "permission denied"
	}

	// =======================================================================
	// Guard: null sourceImage — using real ComputeServer
	// =======================================================================

	void "disableCloudInitCache returns null when sourceImage is null (real objects)"() {
		given:
		def server = new ComputeServer()

		when:
		def result = CloudInitUtils.disableCloudInitCache(morpheusContext, server)

		then:
		result == null
		0 * morpheusContext.executeCommandOnServer(*_)
	}

	void "restoreCloudInitCache returns null when sourceImage is null (real objects)"() {
		given:
		def server = new ComputeServer()

		when:
		def result = CloudInitUtils.restoreCloudInitCache(morpheusContext, server)

		then:
		result == null
		0 * morpheusContext.executeCommandOnServer(*_)
	}

	// =======================================================================
	// Guard: cloudInit is false — using real objects
	// =======================================================================

	void "disableCloudInitCache returns null when cloudInit is false (real objects)"() {
		given:
		def server = buildServer(false, PlatformType.windows)

		when:
		def result = CloudInitUtils.disableCloudInitCache(morpheusContext, server)

		then:
		result == null
		0 * morpheusContext.executeCommandOnServer(*_)
	}

	void "restoreCloudInitCache returns null when cloudInit is false (real objects)"() {
		given:
		def server = buildServer(false, PlatformType.windows)

		when:
		def result = CloudInitUtils.restoreCloudInitCache(morpheusContext, server)

		then:
		result == null
		0 * morpheusContext.executeCommandOnServer(*_)
	}

	// =======================================================================
	// Guard: serverOs is null — using real objects
	// =======================================================================

	void "disableCloudInitCache returns null when serverOs is null (real objects)"() {
		given:
		def server = buildServer(true, null)

		when:
		def result = CloudInitUtils.disableCloudInitCache(morpheusContext, server)

		then:
		result == null
		0 * morpheusContext.executeCommandOnServer(*_)
	}

	void "restoreCloudInitCache returns null when serverOs is null (real objects)"() {
		given:
		def server = buildServer(true, null)

		when:
		def result = CloudInitUtils.restoreCloudInitCache(morpheusContext, server)

		then:
		result == null
		0 * morpheusContext.executeCommandOnServer(*_)
	}

	// =======================================================================
	// Guard: every non-windows PlatformType returns null
	// =======================================================================

	@Unroll
	void "disableCloudInitCache returns null for platform #platform"() {
		given:
		def server = buildServer(true, platform)

		when:
		def result = CloudInitUtils.disableCloudInitCache(morpheusContext, server)

		then:
		result == null
		0 * morpheusContext.executeCommandOnServer(*_)

		where:
		platform << [
			PlatformType.linux,
			PlatformType.osx,
			PlatformType.other,
			PlatformType.ESXi,
			PlatformType.none,
			PlatformType.unknown,
			PlatformType.solaris
		]
	}

	@Unroll
	void "restoreCloudInitCache returns null for platform #platform"() {
		given:
		def server = buildServer(true, platform)

		when:
		def result = CloudInitUtils.restoreCloudInitCache(morpheusContext, server)

		then:
		result == null
		0 * morpheusContext.executeCommandOnServer(*_)

		where:
		platform << [
			PlatformType.linux,
			PlatformType.osx,
			PlatformType.other,
			PlatformType.ESXi,
			PlatformType.none,
			PlatformType.unknown,
			PlatformType.solaris
		]
	}

	// =======================================================================
	// Sequential workflow: disable then restore on the same server
	// =======================================================================

	void "disable then restore executes both commands in sequence on the same server"() {
		given:
		def server = buildServer(true, PlatformType.windows, "root", "toor")
		def disableResult = new TaskResult(success: true, output: "disabled")
		def restoreResult = new TaskResult(success: true, output: "restored")
		morpheusContext.executeCommandOnServer(
			server, DISABLE_COMMAND, false,
			"root", "toor",
			null, null, null, null, true, true
		) >> Single.just(disableResult)
		morpheusContext.executeCommandOnServer(
			server, RESTORE_COMMAND, false,
			"root", "toor",
			null, null, null, null, true, true
		) >> Single.just(restoreResult)

		when:
		def r1 = CloudInitUtils.disableCloudInitCache(morpheusContext, server)
		def r2 = CloudInitUtils.restoreCloudInitCache(morpheusContext, server)

		then:
		r1.is(disableResult)
		r1.output == "disabled"
		r2.is(restoreResult)
		r2.output == "restored"
	}

	// =======================================================================
	// Guard combinations — multiple null/false conditions at once
	// =======================================================================

	void "disableCloudInitCache returns null when both sourceImage is null and serverOs is null"() {
		given:
		def server = new ComputeServer()

		when:
		def result = CloudInitUtils.disableCloudInitCache(morpheusContext, server)

		then:
		result == null
		0 * morpheusContext.executeCommandOnServer(*_)
	}

	void "disableCloudInitCache returns null when cloudInit is false and platform is not windows"() {
		given:
		def server = buildServer(false, PlatformType.linux)

		when:
		def result = CloudInitUtils.disableCloudInitCache(morpheusContext, server)

		then:
		result == null
		0 * morpheusContext.executeCommandOnServer(*_)
	}

	void "restoreCloudInitCache returns null when cloudInit is false and serverOs is null"() {
		given:
		def server = new ComputeServer()
		def image = new VirtualImage()
		image.setCloudInit(false)
		server.sourceImage = image

		when:
		def result = CloudInitUtils.restoreCloudInitCache(morpheusContext, server)

		then:
		result == null
		0 * morpheusContext.executeCommandOnServer(*_)
	}

	// =======================================================================
	// Confirm windows is the only platform that triggers execution
	// =======================================================================

	@Unroll
	void "only windows platform triggers disableCloudInitCache (platform=#platform, expected=#shouldExecute)"() {
		given:
		def server = buildServer(true, platform)
		if (shouldExecute) {
			morpheusContext.executeCommandOnServer(
				server, DISABLE_COMMAND, false,
				"admin", "secret",
				null, null, null, null, true, true
			) >> Single.just(new TaskResult(success: true))
		}

		when:
		def result = CloudInitUtils.disableCloudInitCache(morpheusContext, server)

		then:
		if (shouldExecute) {
			assert result != null
			assert result.success == true
		} else {
			assert result == null
		}

		where:
		platform              || shouldExecute
		PlatformType.windows  || true
		PlatformType.linux    || false
		PlatformType.osx      || false
		PlatformType.other    || false
		PlatformType.ESXi     || false
		PlatformType.none     || false
		PlatformType.unknown  || false
		PlatformType.solaris  || false
	}
}
