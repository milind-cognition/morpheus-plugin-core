package com.morpheusdata.core.util

import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.model.ComputeServer
import com.morpheusdata.model.OsType
import com.morpheusdata.model.PlatformType
import com.morpheusdata.model.TaskResult
import com.morpheusdata.model.VirtualImage
import io.reactivex.rxjava3.core.Single
import spock.lang.Specification

class CloudInitUtilsSpec extends Specification {

	MorpheusContext morpheusContext
	ComputeServer server

	def setup() {
		morpheusContext = Mock(MorpheusContext)
		server = Mock(ComputeServer)
	}

	// --- disableCloudInitCache tests ---

	void "disableCloudInitCache returns null when sourceImage is null"() {
		given:
		server.getSourceImage() >> null

		when:
		def result = CloudInitUtils.disableCloudInitCache(morpheusContext, server)

		then:
		result == null
	}

	void "disableCloudInitCache returns null when sourceImage.isCloudInit is false"() {
		given:
		def image = Mock(VirtualImage)
		image.isCloudInit() >> false
		server.getSourceImage() >> image

		when:
		def result = CloudInitUtils.disableCloudInitCache(morpheusContext, server)

		then:
		result == null
	}

	void "disableCloudInitCache returns null when serverOs is null"() {
		given:
		def image = Mock(VirtualImage)
		image.isCloudInit() >> true
		server.getSourceImage() >> image
		server.getServerOs() >> null

		when:
		def result = CloudInitUtils.disableCloudInitCache(morpheusContext, server)

		then:
		result == null
	}

	void "disableCloudInitCache returns null when platform is not windows"() {
		given:
		def image = Mock(VirtualImage)
		image.isCloudInit() >> true
		server.getSourceImage() >> image
		def osType = Mock(OsType)
		osType.getPlatform() >> PlatformType.linux
		server.getServerOs() >> osType

		when:
		def result = CloudInitUtils.disableCloudInitCache(morpheusContext, server)

		then:
		result == null
	}

	void "disableCloudInitCache calls executeCommandOnServer when all conditions are met"() {
		given:
		def image = Mock(VirtualImage)
		image.isCloudInit() >> true
		server.getSourceImage() >> image
		def osType = Mock(OsType)
		osType.getPlatform() >> PlatformType.windows
		server.getServerOs() >> osType
		server.getSshUsername() >> "testuser"
		server.getSshPassword() >> "testpass"
		morpheusContext.executeCommandOnServer(server, _ as String, false, "testuser", "testpass", null, null, null, null, true, true) >> Single.just(new TaskResult())

		when:
		def result = CloudInitUtils.disableCloudInitCache(morpheusContext, server)

		then:
		result != null
		result instanceof TaskResult
	}

	// --- restoreCloudInitCache tests ---

	void "restoreCloudInitCache returns null when sourceImage is null"() {
		given:
		server.getSourceImage() >> null

		when:
		def result = CloudInitUtils.restoreCloudInitCache(morpheusContext, server)

		then:
		result == null
	}

	void "restoreCloudInitCache returns null when sourceImage.isCloudInit is false"() {
		given:
		def image = Mock(VirtualImage)
		image.isCloudInit() >> false
		server.getSourceImage() >> image

		when:
		def result = CloudInitUtils.restoreCloudInitCache(morpheusContext, server)

		then:
		result == null
	}

	void "restoreCloudInitCache returns null when serverOs is null"() {
		given:
		def image = Mock(VirtualImage)
		image.isCloudInit() >> true
		server.getSourceImage() >> image
		server.getServerOs() >> null

		when:
		def result = CloudInitUtils.restoreCloudInitCache(morpheusContext, server)

		then:
		result == null
	}

	void "restoreCloudInitCache returns null when platform is not windows"() {
		given:
		def image = Mock(VirtualImage)
		image.isCloudInit() >> true
		server.getSourceImage() >> image
		def osType = Mock(OsType)
		osType.getPlatform() >> PlatformType.linux
		server.getServerOs() >> osType

		when:
		def result = CloudInitUtils.restoreCloudInitCache(morpheusContext, server)

		then:
		result == null
	}

	void "restoreCloudInitCache calls executeCommandOnServer with restore command when all conditions are met"() {
		given:
		def image = Mock(VirtualImage)
		image.isCloudInit() >> true
		server.getSourceImage() >> image
		def osType = Mock(OsType)
		osType.getPlatform() >> PlatformType.windows
		server.getServerOs() >> osType
		server.getSshUsername() >> "testuser"
		server.getSshPassword() >> "testpass"
		morpheusContext.executeCommandOnServer(server, _ as String, false, "testuser", "testpass", null, null, null, null, true, true) >> Single.just(new TaskResult())

		when:
		def result = CloudInitUtils.restoreCloudInitCache(morpheusContext, server)

		then:
		result != null
		result instanceof TaskResult
	}
}
