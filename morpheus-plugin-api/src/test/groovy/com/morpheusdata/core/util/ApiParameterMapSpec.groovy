package com.morpheusdata.core.util

import spock.lang.Specification

class ApiParameterMapSpec extends Specification {

	void "default constructor creates empty map"() {
		when:
		def map = new ApiParameterMap()

		then:
		map.size() == 0
		map.isEmpty()
	}

	void "constructor with null map does not throw"() {
		when:
		def map = new ApiParameterMap(null)

		then:
		noExceptionThrown()
		map.isEmpty()
	}

	void "constructor with populated map copies all entries"() {
		given:
		def source = [key1: 'value1', key2: 'value2', key3: 'value3']

		when:
		def map = new ApiParameterMap(source)

		then:
		map.size() == source.size()
		map.key1 == 'value1'
		map.key2 == 'value2'
		map.key3 == 'value3'
	}

	void "list returns collection as-is when value is already a Collection"() {
		given:
		def map = new ApiParameterMap()
		map.put(key, value)

		expect:
		map.list(key).is(value)

		where:
		key       | value
		'items'   | ['a', 'b', 'c']
		'numbers' | [1, 2, 3] as Set
	}

	void "list wraps single item in an ArrayList"() {
		given:
		def map = new ApiParameterMap()
		map.put(key, value)

		when:
		def result = map.list(key)

		then:
		result instanceof ArrayList
		result.size() == 1
		result[0] == value

		where:
		key      | value
		'name'   | 'morpheus'
		'count'  | 42
		'flag'   | true
	}

	void "list returns a list containing null when key does not exist"() {
		given:
		def map = new ApiParameterMap()

		when:
		def result = map.list('missing')

		then:
		result instanceof ArrayList
		result.size() == 1
		result[0] == null
	}

	void "standard map operations work"() {
		given:
		def map = new ApiParameterMap()

		when:
		map.put('alpha', 1)
		map.put('beta', 2)

		then:
		map.size() == 2
		map.get('alpha') == 1
		map.get('beta') == 2
		map.containsKey('alpha')
		!map.containsKey('gamma')
	}
}
