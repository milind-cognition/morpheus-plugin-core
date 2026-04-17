package com.morpheusdata.core.util

import spock.lang.Specification

import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocketFactory
import java.security.Security
import java.security.cert.X509Certificate

/**
 * E2E tests for SSLUtility covering integration behavior, idempotency,
 * JVM-global state management, exception paths, and BouncyCastle registration.
 *
 * Complements the unit-level SSLUtilitySpec from PR #9.
 */
class SSLUtilityE2ESpec extends Specification {

	HostnameVerifier originalHostnameVerifier
	SSLSocketFactory originalSSLSocketFactory

	def setup() {
		originalHostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
		originalSSLSocketFactory = HttpsURLConnection.getDefaultSSLSocketFactory()
	}

	def cleanup() {
		HttpsURLConnection.setDefaultHostnameVerifier(originalHostnameVerifier)
		HttpsURLConnection.setDefaultSSLSocketFactory(originalSSLSocketFactory)
	}

	// --- BouncyCastle provider registration ---

	void "BouncyCastle provider is registered after SSLUtility class loading"() {
		when: "force SSLUtility static initializer to run"
		SSLUtility.trustAllHostnames()

		then:
		Security.getProvider("BC") != null
	}

	// --- Combined workflow: trustAllHostnames + trustAllHttpsCertificates ---

	void "calling both trustAllHostnames and trustAllHttpsCertificates configures JVM globals"() {
		given:
		def socketFactoryBefore = HttpsURLConnection.getDefaultSSLSocketFactory()

		when:
		SSLUtility.trustAllHostnames()
		SSLUtility.trustAllHttpsCertificates()

		then:
		def verifier = HttpsURLConnection.getDefaultHostnameVerifier()
		def factory = HttpsURLConnection.getDefaultSSLSocketFactory()
		verifier instanceof SSLUtility.FakeHostnameVerifier
		!factory.is(socketFactoryBefore)
		verifier.verify("example.com", null)
	}

	// --- Idempotency ---

	void "trustAllHostnames is idempotent and does not throw on repeated calls"() {
		when:
		SSLUtility.trustAllHostnames()
		def firstVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
		SSLUtility.trustAllHostnames()
		def secondVerifier = HttpsURLConnection.getDefaultHostnameVerifier()

		then:
		noExceptionThrown()
		firstVerifier instanceof SSLUtility.FakeHostnameVerifier
		secondVerifier instanceof SSLUtility.FakeHostnameVerifier
	}

	void "trustAllHttpsCertificates is idempotent and does not throw on repeated calls"() {
		when:
		SSLUtility.trustAllHttpsCertificates()
		def firstFactory = HttpsURLConnection.getDefaultSSLSocketFactory()
		SSLUtility.trustAllHttpsCertificates()
		def secondFactory = HttpsURLConnection.getDefaultSSLSocketFactory()

		then:
		noExceptionThrown()
		firstFactory != null
		secondFactory != null
	}

	// --- OldFakeX509TrustManager exception behavior ---

	void "OldFakeX509TrustManager.checkClientTrusted throws UnsupportedOperationException"() {
		given:
		def manager = new SSLUtility.OldFakeX509TrustManager()

		when:
		manager.checkClientTrusted(null, null)

		then:
		thrown(UnsupportedOperationException)
	}

	void "OldFakeX509TrustManager.checkServerTrusted throws UnsupportedOperationException"() {
		given:
		def manager = new SSLUtility.OldFakeX509TrustManager()

		when:
		manager.checkServerTrusted(null, null)

		then:
		thrown(UnsupportedOperationException)
	}

	// --- OldFakeX509TrustManager legacy boolean methods ---

	void "OldFakeX509TrustManager.isClientTrusted returns true"() {
		given:
		def manager = new SSLUtility.OldFakeX509TrustManager()

		expect:
		manager.isClientTrusted(null)
		manager.isClientTrusted(new X509Certificate[0])
	}

	void "OldFakeX509TrustManager.isServerTrusted returns true"() {
		given:
		def manager = new SSLUtility.OldFakeX509TrustManager()

		expect:
		manager.isServerTrusted(null)
		manager.isServerTrusted(new X509Certificate[0])
	}

	// --- SSLSocketFactory produced by trustAllHttpsCertificates uses FakeX509TrustManager ---

	void "trustAllHttpsCertificates installs a valid socket factory with cipher suites"() {
		when:
		SSLUtility.trustAllHttpsCertificates()
		def factory = HttpsURLConnection.getDefaultSSLSocketFactory()

		then:
		factory != null
		factory.defaultCipherSuites != null
		factory.defaultCipherSuites.length > 0
		factory.supportedCipherSuites != null
		factory.supportedCipherSuites.length > 0
	}

	// --- Verifier and factory are distinct from JVM defaults ---

	void "installed hostname verifier differs from default JVM verifier"() {
		given:
		def defaultVerifier = HttpsURLConnection.getDefaultHostnameVerifier()

		when:
		SSLUtility.trustAllHostnames()
		def installedVerifier = HttpsURLConnection.getDefaultHostnameVerifier()

		then:
		!installedVerifier.is(defaultVerifier)
		installedVerifier instanceof SSLUtility.FakeHostnameVerifier
	}

	void "installed SSLSocketFactory differs from default JVM factory"() {
		given:
		def defaultFactory = HttpsURLConnection.getDefaultSSLSocketFactory()

		when:
		SSLUtility.trustAllHttpsCertificates()
		def installedFactory = HttpsURLConnection.getDefaultSSLSocketFactory()

		then:
		!installedFactory.is(defaultFactory)
	}

	// --- State isolation: cleanup restores original verifier and factory ---

	void "JVM state is restored after cleanup to pre-test values"() {
		given:
		def preTestVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
		def preTestFactory = HttpsURLConnection.getDefaultSSLSocketFactory()

		when: "SSLUtility modifies JVM globals"
		SSLUtility.trustAllHostnames()
		SSLUtility.trustAllHttpsCertificates()

		then:
		!HttpsURLConnection.getDefaultHostnameVerifier().is(preTestVerifier)
		!HttpsURLConnection.getDefaultSSLSocketFactory().is(preTestFactory)

		when: "we manually restore"
		HttpsURLConnection.setDefaultHostnameVerifier(preTestVerifier)
		HttpsURLConnection.setDefaultSSLSocketFactory(preTestFactory)

		then:
		HttpsURLConnection.getDefaultHostnameVerifier().is(preTestVerifier)
		HttpsURLConnection.getDefaultSSLSocketFactory().is(preTestFactory)
	}

	// --- FakeHostnameVerifier with a mock SSLSession ---

	void "FakeHostnameVerifier.verify returns true even with a mock SSLSession"() {
		given:
		def verifier = new SSLUtility.FakeHostnameVerifier()
		def mockSession = [:] as SSLSession

		expect:
		verifier.verify("secure.example.com", mockSession)
		verifier.verify("", mockSession)
	}

	// --- FakeX509TrustManager accepts any certificate chain ---

	void "FakeX509TrustManager.checkClientTrusted accepts non-null empty chain"() {
		given:
		def manager = new SSLUtility.FakeX509TrustManager()

		when:
		manager.checkClientTrusted(new X509Certificate[0], "RSA")

		then:
		noExceptionThrown()
	}

	void "FakeX509TrustManager.checkServerTrusted accepts non-null empty chain"() {
		given:
		def manager = new SSLUtility.FakeX509TrustManager()

		when:
		manager.checkServerTrusted(new X509Certificate[0], "RSA")

		then:
		noExceptionThrown()
	}
}
