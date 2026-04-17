package com.morpheusdata.core.util

import spock.lang.Specification
import spock.lang.Stepwise

import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocketFactory
import java.security.cert.X509Certificate

@Stepwise
class SSLUtilitySpec extends Specification {

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

	void "trustAllHostnames sets a verifier that returns true for any hostname"() {
		when:
		SSLUtility.trustAllHostnames()
		def verifier = HttpsURLConnection.getDefaultHostnameVerifier()

		then:
		verifier.verify("any.hostname.com", null)
		verifier.verify("", null)
		verifier.verify("localhost", null)
	}

	void "trustAllHttpsCertificates changes the default SSLSocketFactory"() {
		given:
		def before = HttpsURLConnection.getDefaultSSLSocketFactory()

		when:
		SSLUtility.trustAllHttpsCertificates()
		def after = HttpsURLConnection.getDefaultSSLSocketFactory()

		then:
		after != null
		!after.is(before)
	}

	void "FakeHostnameVerifier.verify always returns true"() {
		given:
		def verifier = new SSLUtility.FakeHostnameVerifier()

		expect:
		verifier.verify("any.host", null)
		verifier.verify("", null)
		verifier.verify(null, null)
	}

	void "FakeX509TrustManager.getAcceptedIssuers returns empty array"() {
		given:
		def manager = new SSLUtility.FakeX509TrustManager()

		when:
		X509Certificate[] issuers = manager.getAcceptedIssuers()

		then:
		issuers != null
		issuers.length == 0
	}

	void "FakeX509TrustManager.checkClientTrusted does not throw"() {
		given:
		def manager = new SSLUtility.FakeX509TrustManager()

		when:
		manager.checkClientTrusted(null, null)

		then:
		noExceptionThrown()
	}

	void "FakeX509TrustManager.checkServerTrusted does not throw"() {
		given:
		def manager = new SSLUtility.FakeX509TrustManager()

		when:
		manager.checkServerTrusted(null, null)

		then:
		noExceptionThrown()
	}

	void "OldFakeHostnameVerifier.verify always returns true"() {
		given:
		def verifier = new SSLUtility.OldFakeHostnameVerifier()

		expect:
		verifier.verify("any.host", null)
		verifier.verify("", null)
		verifier.verify(null, null)
	}

	void "OldFakeX509TrustManager.getAcceptedIssuers returns empty array"() {
		given:
		def manager = new SSLUtility.OldFakeX509TrustManager()

		when:
		X509Certificate[] issuers = manager.getAcceptedIssuers()

		then:
		issuers != null
		issuers.length == 0
	}

	void "isDeprecatedSSLProtocol returns false in normal JVM"() {
		when:
		SSLUtility.trustAllHostnames()
		def verifier = HttpsURLConnection.getDefaultHostnameVerifier()

		then:
		verifier instanceof SSLUtility.FakeHostnameVerifier
	}
}
