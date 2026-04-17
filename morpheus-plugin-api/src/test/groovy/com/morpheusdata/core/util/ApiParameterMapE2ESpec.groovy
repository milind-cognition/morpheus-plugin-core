package com.morpheusdata.core.util

import com.morpheusdata.core.data.DataAndFilter
import com.morpheusdata.core.data.DataFilter
import com.morpheusdata.core.data.DataOrFilter
import com.morpheusdata.core.data.DataQuery
import com.morpheusdata.core.data.DatasetQuery
import spock.lang.Specification

class ApiParameterMapE2ESpec extends Specification {

	// --- DataQuery integration: put/get through DataQuery delegates to ApiParameterMap ---

	void "DataQuery put and get delegate to its ApiParameterMap"() {
		given:
		def query = new DataQuery()

		when:
		query.put('region', 'us-east-1')
		query.put('max', 25)

		then:
		query.get('region') == 'us-east-1'
		query.get('max') == 25
		query.parameters instanceof ApiParameterMap
		query.parameters.size() == 2
	}

	void "DataQuery putAt and getAt provide Groovy subscript access"() {
		given:
		def query = new DataQuery()

		when:
		query.putAt('cloud', 'aws')

		then:
		query.getAt('cloud') == 'aws'
		query.parameters.containsKey('cloud')
	}

	void "DataQuery putAll populates ApiParameterMap from a plain Map"() {
		given:
		def query = new DataQuery()
		def params = [offset: 10, sort: 'name', direction: 'asc']

		when:
		query.putAll(params)

		then:
		query.get('offset') == 10
		query.get('sort') == 'name'
		query.get('direction') == 'asc'
		query.parameters.size() == 3
	}

	void "DataQuery putAll with null map does not throw"() {
		given:
		def query = new DataQuery()

		when:
		query.putAll(null)

		then:
		noExceptionThrown()
		query.parameters.isEmpty()
	}

	void "DataQuery putAll with empty map is a no-op"() {
		given:
		def query = new DataQuery()

		when:
		query.putAll([:])

		then:
		noExceptionThrown()
		query.parameters.isEmpty()
	}

	// --- DataQuery.list() delegates to ApiParameterMap.list() ---

	void "DataQuery list returns collection as-is for collection values"() {
		given:
		def query = new DataQuery()
		def ids = [100L, 200L, 300L]

		when:
		query.put('ids', ids)
		def result = query.list('ids')

		then:
		result.is(ids)
		result.size() == 3
	}

	void "DataQuery list wraps scalar value in a single-element ArrayList"() {
		given:
		def query = new DataQuery()

		when:
		query.put('instanceId', 42)
		def result = query.list('instanceId')

		then:
		result instanceof ArrayList
		result.size() == 1
		result[0] == 42
	}

	void "DataQuery list for missing key returns list containing null"() {
		given:
		def query = new DataQuery()

		when:
		def result = query.list('nonexistent')

		then:
		result instanceof ArrayList
		result.size() == 1
		result[0] == null
	}

	// --- DataQuery.findParameter() with ApiParameterMap ---

	void "DataQuery findParameter matches exact key"() {
		given:
		def query = new DataQuery()
		query.put('config.region', 'eu-west-1')
		query.put('maxResults', 50)

		expect:
		query.findParameter('maxResults') == 'maxResults'
	}

	void "DataQuery findParameter matches suffix of key"() {
		given:
		def query = new DataQuery()
		query.put('config.region', 'eu-west-1')

		expect:
		query.findParameter('region') == 'config.region'
	}

	void "DataQuery findParameter returns null when no match"() {
		given:
		def query = new DataQuery()
		query.put('cloud', 'azure')

		expect:
		query.findParameter('missing') == null
	}

	// --- DataQuery.withParameters() uses ApiParameterMap.putAll() ---

	void "DataQuery withParameters merges into existing ApiParameterMap"() {
		given:
		def query = new DataQuery()
		query.put('existing', 'value')

		when:
		def result = query.withParameters([added: 'new', count: 5])

		then:
		result.is(query)
		query.get('existing') == 'value'
		query.get('added') == 'new'
		query.get('count') == 5
		query.parameters.size() == 3
	}

	void "DataQuery withParameters with null is a no-op"() {
		given:
		def query = new DataQuery()
		query.put('keep', 'this')

		when:
		query.withParameters(null)

		then:
		query.parameters.size() == 1
		query.get('keep') == 'this'
	}

	// --- DataQuery constructor with parameters ---

	void "DataQuery toMap includes parameters from ApiParameterMap"() {
		given:
		def query = new DataQuery()
		query.put('zoneId', 'zone-1')
		query.max = 10L
		query.offset = 5L
		query.sort = 'name'

		when:
		def map = query.toMap()

		then:
		map['parameters'] instanceof ApiParameterMap
		map['parameters']['zoneId'] == 'zone-1'
		map['pageConfig']['max'] == 10L
		map['pageConfig']['offset'] == 5L
		map['pageConfig']['sort'] == 'name'
	}

	// --- DataQuery with filters + parameters coexistence ---

	void "DataQuery supports filters and parameters simultaneously"() {
		given:
		def query = new DataQuery()

		when:
		query.put('tenantId', 'abc-123')
		query.withFilter('name', 'TestServer')
		query.withFilter('status', '=', 'running')
		query.withFilters(
			new DataOrFilter(
				new DataFilter('type', 'vm'),
				new DataFilter('type', 'container')
			)
		)

		then:
		query.get('tenantId') == 'abc-123'
		query.filters.size() == 3
		query.parameters.size() == 1
	}

	// --- ApiParameterMap preserves insertion order (LinkedHashMap) ---

	void "ApiParameterMap preserves insertion order via DataQuery"() {
		given:
		def query = new DataQuery()

		when:
		query.put('charlie', 3)
		query.put('alpha', 1)
		query.put('bravo', 2)

		then:
		def keys = query.parameters.keySet().toList()
		keys == ['charlie', 'alpha', 'bravo']
	}

	// --- DataQuery overwrite behavior ---

	void "DataQuery put overwrites existing key in ApiParameterMap"() {
		given:
		def query = new DataQuery()

		when:
		query.put('region', 'us-east-1')
		query.put('region', 'us-west-2')

		then:
		query.get('region') == 'us-west-2'
		query.parameters.size() == 1
	}

	// --- DatasetQuery inherits ApiParameterMap behavior ---

	void "DatasetQuery inherits parameter operations from DataQuery"() {
		given:
		def dsQuery = new DatasetQuery()

		when:
		dsQuery.put('namespace', 'cloud')
		dsQuery.put('valueField', 'id')
		def listResult = dsQuery.list('namespace')

		then:
		dsQuery.get('namespace') == 'cloud'
		dsQuery.get('valueField') == 'id'
		listResult instanceof ArrayList
		listResult.size() == 1
		listResult[0] == 'cloud'
		dsQuery.parameters.size() == 2
	}

	void "DatasetQuery withParameters merges into inherited ApiParameterMap"() {
		given:
		def dsQuery = new DatasetQuery()

		when:
		dsQuery.withParameters([phrase: 'search term', max: 100])

		then:
		dsQuery.get('phrase') == 'search term'
		dsQuery.get('max') == 100
		dsQuery.parameters.size() == 2
	}

	// --- ApiParameterMap with various value types through DataQuery ---

	void "DataQuery handles diverse value types in ApiParameterMap"() {
		given:
		def query = new DataQuery()
		def nested = [host: 'localhost', port: 8080]

		when:
		query.put('stringVal', 'text')
		query.put('intVal', 42)
		query.put('longVal', 100L)
		query.put('boolVal', true)
		query.put('nullVal', null)
		query.put('listVal', [1, 2, 3])
		query.put('mapVal', nested)

		then:
		query.get('stringVal') == 'text'
		query.get('intVal') == 42
		query.get('longVal') == 100L
		query.get('boolVal') == true
		query.get('nullVal') == null
		query.get('listVal') == [1, 2, 3]
		query.get('mapVal') == nested
		query.parameters.size() == 7
	}

	void "DataQuery list wraps null value in single-element list"() {
		given:
		def query = new DataQuery()
		query.put('nullKey', null)

		when:
		def result = query.list('nullKey')

		then:
		result instanceof ArrayList
		result.size() == 1
		result[0] == null
	}

	void "DataQuery list wraps string value in single-element list"() {
		given:
		def query = new DataQuery()
		query.put('name', 'morpheus')

		when:
		def result = query.list('name')

		then:
		result instanceof ArrayList
		result.size() == 1
		result[0] == 'morpheus'
	}

	void "DataQuery list returns Set as-is when value is a Set"() {
		given:
		def query = new DataQuery()
		def ids = [10, 20, 30] as Set

		when:
		query.put('ids', ids)
		def result = query.list('ids')

		then:
		result.is(ids)
		result instanceof Set
	}

	// --- ApiParameterMap constructed from existing Map used in DataQuery ---

	void "ApiParameterMap from source map used as DataQuery parameters"() {
		given:
		def source = [cloud: 'aws', region: 'us-east-1', zones: ['a', 'b']]
		def paramMap = new ApiParameterMap(source)
		def query = new DataQuery()

		when:
		query.parameters = paramMap

		then:
		query.parameters.size() == 3
		query.parameters.get('cloud') == 'aws'
		query.parameters.list('zones').is(source.zones)
		query.parameters.list('region') instanceof ArrayList
		query.parameters.list('region')[0] == 'us-east-1'
	}

	// --- End-to-end: full DataQuery build with parameters, filters, paging, sort ---

	void "full DataQuery build with parameters, filters, joins, and paging"() {
		given:
		def query = new DataQuery()

		when:
		query.put('tenantId', 'tenant-1')
		query.put('tags', ['prod', 'us-east'])
		query.withParameters([includeDeleted: false])
		query.withFilter('status', 'active')
		query.withFilter('type', '!=', 'deprecated')
		query.withFilters(
			new DataAndFilter(
				new DataFilter('cloud', 'aws'),
				new DataFilter('region', 'us-east-1')
			)
		)
		query.withJoin('interfaces.network')
		query.withSort('name', DataQuery.SortOrder.desc)
		query.max = 50L
		query.offset = 0L
		query.phrase = 'production servers'

		then: 'parameters are accessible'
		query.get('tenantId') == 'tenant-1'
		query.list('tags') == ['prod', 'us-east']
		query.get('includeDeleted') == false
		query.parameters.size() == 3

		and: 'filters are set'
		query.filters.size() == 3

		and: 'joins are set'
		query.joins.size() == 1
		query.joins[0] == 'interfaces.network'

		and: 'paging and sort config'
		query.max == 50L
		query.offset == 0L
		query.sort == 'name'
		query.order == DataQuery.SortOrder.desc

		and: 'toMap produces expected structure'
		def map = query.toMap()
		map['phrase'] == 'production servers'
		map['parameters'] instanceof ApiParameterMap
		map['parameters'].size() == 3
		map['filters'].size() == 3
		map['pageConfig']['max'] == 50L
		map['pageConfig']['sort'] == 'name'
		map['pageConfig']['order'] == DataQuery.SortOrder.desc
	}

	// --- Edge case: findParameter suffix matching with dotted keys ---

	void "findParameter prefers exact match over suffix match"() {
		given:
		def query = new DataQuery()
		query.put('region', 'exact')
		query.put('config.region', 'suffix')

		expect:
		query.findParameter('region') == 'region'
	}

	void "findParameter with multiple dotted keys returns first suffix match"() {
		given:
		def query = new DataQuery()
		query.put('app.config.timeout', 30)
		query.put('db.config.timeout', 60)

		when:
		def found = query.findParameter('timeout')

		then:
		found == 'app.config.timeout' || found == 'db.config.timeout'
		found != null
	}

	// --- ApiParameterMap generic type safety through DataQuery ---

	void "DataQuery parameters default type is ApiParameterMap of String to Object"() {
		given:
		def query = new DataQuery()

		expect:
		query.parameters != null
		query.parameters instanceof ApiParameterMap
		query.parameters.isEmpty()
	}

	void "fresh DataQuery has empty parameters that support all map operations"() {
		given:
		def query = new DataQuery()

		when:
		query.parameters.put('directKey', 'directValue')

		then:
		query.parameters.get('directKey') == 'directValue'
		query.parameters.containsKey('directKey')
		!query.parameters.containsKey('other')
		query.parameters.containsValue('directValue')
		query.parameters.entrySet().size() == 1
		query.parameters.keySet() == ['directKey'] as Set
		query.parameters.values().toList() == ['directValue']
	}
}
