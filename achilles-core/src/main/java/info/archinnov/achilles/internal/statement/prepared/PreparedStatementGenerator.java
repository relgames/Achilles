/*
 * Copyright (C) 2012-2014 DuyHai DOAN
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package info.archinnov.achilles.internal.statement.prepared;

import static com.datastax.driver.core.querybuilder.QueryBuilder.*;
import static com.google.common.collect.ImmutableMap.of;
import static info.archinnov.achilles.counter.AchillesCounter.*;
import static info.archinnov.achilles.counter.AchillesCounter.CQLQueryType.*;
import static info.archinnov.achilles.counter.AchillesCounter.ClusteredCounterStatement.*;
import info.archinnov.achilles.counter.AchillesCounter.CQLQueryType;
import info.archinnov.achilles.internal.metadata.holder.EntityMeta;
import info.archinnov.achilles.internal.metadata.holder.PropertyMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.RegularStatement;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.Delete;
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.datastax.driver.core.querybuilder.Select;
import com.datastax.driver.core.querybuilder.Select.Selection;
import com.datastax.driver.core.querybuilder.Update;
import com.datastax.driver.core.querybuilder.Update.Assignments;

public class PreparedStatementGenerator {
	private static final Logger log = LoggerFactory.getLogger(PreparedStatementGenerator.class);

	public PreparedStatement prepareInsertPS(Session session, EntityMeta entityMeta) {
		log.trace("Generate prepared statement for INSERT on {}", entityMeta);
		PropertyMeta idMeta = entityMeta.getIdMeta();
		Insert insert = insertInto(entityMeta.getTableName());
		prepareInsertPrimaryKey(idMeta, insert);

		for (PropertyMeta pm : entityMeta.getAllMetasExceptIdAndCounters()) {
			String property = pm.getPropertyName();
			insert.value(property, bindMarker(property));
		}

		insert.using(ttl(bindMarker("ttl")));
		return session.prepare(insert.getQueryString());
	}

	public PreparedStatement prepareSelectFieldPS(Session session, EntityMeta entityMeta, PropertyMeta pm) {
		log.trace("Generate prepared statement for SELECT property {}", pm);

		PropertyMeta idMeta = entityMeta.getIdMeta();

		if (pm.isCounter()) {
			throw new IllegalArgumentException("Cannot prepare statement for property '" + pm.getPropertyName()
					+ "' of entity '" + entityMeta.getClassName() + "' because it is a counter type");
		} else {
			Selection select = prepareSelectField(pm, select());
			Select from = select.from(entityMeta.getTableName());
			RegularStatement statement = prepareWhereClauseForSelect(idMeta, from);
			return session.prepare(statement.getQueryString());
		}
	}

	public PreparedStatement prepareUpdateFields(Session session, EntityMeta entityMeta, List<PropertyMeta> pms) {

		log.trace("Generate prepared statement for UPDATE properties {}", pms);

		PropertyMeta idMeta = entityMeta.getIdMeta();
		Update update = update(entityMeta.getTableName());

		Assignments assignments = null;
		for (int i = 0; i < pms.size(); i++) {
			PropertyMeta pm = pms.get(i);
			String property = pm.getPropertyName();
			if (i == 0) {
				assignments = update.with(set(property, bindMarker(property)));
			} else {
				assignments.and(set(property, bindMarker(property)));
			}
		}
		RegularStatement statement = prepareWhereClauseForUpdate(idMeta, assignments, true);
		return session.prepare(statement.getQueryString());
	}

	public PreparedStatement prepareSelectPS(Session session, EntityMeta entityMeta) {
		log.trace("Generate prepared statement for SELECT of {}", entityMeta);

		PropertyMeta idMeta = entityMeta.getIdMeta();

		Selection select = select();

		for (PropertyMeta pm : entityMeta.getColumnsMetaToLoad()) {
			select = prepareSelectField(pm, select);
		}
		Select from = select.from(entityMeta.getTableName());

		RegularStatement statement = prepareWhereClauseForSelect(idMeta, from);
		return session.prepare(statement.getQueryString());
	}

	public Map<CQLQueryType, PreparedStatement> prepareSimpleCounterQueryMap(Session session) {

		StringBuilder incr = new StringBuilder();
		incr.append("UPDATE ").append(CQL_COUNTER_TABLE).append(" ");
		incr.append("SET ").append(CQL_COUNTER_VALUE).append(" = ");
		incr.append(CQL_COUNTER_VALUE).append(" + ? ");
		incr.append("WHERE ").append(CQL_COUNTER_FQCN).append(" = ? ");
		incr.append("AND ").append(CQL_COUNTER_PRIMARY_KEY).append(" = ? ");
		incr.append("AND ").append(CQL_COUNTER_PROPERTY_NAME).append(" = ?");

		StringBuilder decr = new StringBuilder();
		decr.append("UPDATE ").append(CQL_COUNTER_TABLE).append(" ");
		decr.append("SET ").append(CQL_COUNTER_VALUE).append(" = ");
		decr.append(CQL_COUNTER_VALUE).append(" - ? ");
		decr.append("WHERE ").append(CQL_COUNTER_FQCN).append(" = ? ");
		decr.append("AND ").append(CQL_COUNTER_PRIMARY_KEY).append(" = ? ");
		decr.append("AND ").append(CQL_COUNTER_PROPERTY_NAME).append(" = ?");

		StringBuilder select = new StringBuilder();
		select.append("SELECT ").append(CQL_COUNTER_VALUE).append(" ");
		select.append("FROM ").append(CQL_COUNTER_TABLE).append(" ");
		select.append("WHERE ").append(CQL_COUNTER_FQCN).append(" = ? ");
		select.append("AND ").append(CQL_COUNTER_PRIMARY_KEY).append(" = ? ");
		select.append("AND ").append(CQL_COUNTER_PROPERTY_NAME).append(" = ?");

		StringBuilder delete = new StringBuilder();
		delete.append("DELETE FROM ").append(CQL_COUNTER_TABLE).append(" ");
		delete.append("WHERE ").append(CQL_COUNTER_FQCN).append(" = ? ");
		delete.append("AND ").append(CQL_COUNTER_PRIMARY_KEY).append(" = ? ");
		delete.append("AND ").append(CQL_COUNTER_PROPERTY_NAME).append(" = ?");

		Map<CQLQueryType, PreparedStatement> counterPSMap = new HashMap<>();
		counterPSMap.put(INCR, session.prepare(incr.toString()));
		counterPSMap.put(DECR, session.prepare(decr.toString()));
		counterPSMap.put(SELECT, session.prepare(select.toString()));
		counterPSMap.put(DELETE, session.prepare(delete.toString()));

		return counterPSMap;
	}

	public Map<CQLQueryType, Map<String, PreparedStatement>> prepareClusteredCounterQueryMap(Session session,
			EntityMeta meta) {
		PropertyMeta idMeta = meta.getIdMeta();
		String tableName = meta.getTableName();

		Map<CQLQueryType, Map<String, PreparedStatement>> clusteredCounterPSMap = new HashMap<>();
		Map<String, PreparedStatement> incrStatementPerCounter = new HashMap<>();
		Map<String, PreparedStatement> decrStatementPerCounter = new HashMap<>();
		Map<String, PreparedStatement> selectStatementPerCounter = new HashMap<>();

		for (PropertyMeta counterMeta : meta.getAllCounterMetas()) {
			String counterName = counterMeta.getPropertyName();

			RegularStatement incrementStatement = prepareWhereClauseForUpdate(idMeta,
					update(tableName).with(incr(counterName, bindMarker(counterName))), false);

			RegularStatement decrementStatement = prepareWhereClauseForUpdate(idMeta,
					update(tableName).with(decr(counterName, bindMarker(counterName))), false);
			RegularStatement selectStatement = prepareWhereClauseForSelect(idMeta, select(counterName).from(tableName));

			incrStatementPerCounter.put(counterName, session.prepare(incrementStatement));
			decrStatementPerCounter.put(counterName, session.prepare(decrementStatement));
			selectStatementPerCounter.put(counterName, session.prepare(selectStatement));
		}
		clusteredCounterPSMap.put(INCR, incrStatementPerCounter);
		clusteredCounterPSMap.put(DECR, decrStatementPerCounter);

		RegularStatement selectStatement = prepareWhereClauseForSelect(idMeta, select().from(tableName));
		selectStatementPerCounter.put(SELECT_ALL.name(), session.prepare(selectStatement));
		clusteredCounterPSMap.put(SELECT, selectStatementPerCounter);

		RegularStatement deleteStatement = prepareWhereClauseForDelete(idMeta, QueryBuilder.delete().from(tableName));
		clusteredCounterPSMap.put(DELETE, of(DELETE_ALL.name(), session.prepare(deleteStatement)));

		return clusteredCounterPSMap;
	}

	private Selection prepareSelectField(PropertyMeta pm, Selection select) {
		if (pm.isEmbeddedId()) {
			for (String component : pm.getComponentNames()) {
				select = select.column(component);
			}
		} else {
			select = select.column(pm.getPropertyName());
		}
		return select;
	}

	private void prepareInsertPrimaryKey(PropertyMeta idMeta, Insert insert) {
		if (idMeta.isEmbeddedId()) {
			for (String component : idMeta.getComponentNames()) {
				insert.value(component, bindMarker(component));
			}
		} else {
			String idName = idMeta.getPropertyName();
			insert.value(idName, bindMarker(idName));
		}
	}

	private RegularStatement prepareWhereClauseForSelect(PropertyMeta idMeta, Select from) {
		RegularStatement statement;
		if (idMeta.isEmbeddedId()) {
			Select.Where where = null;
			int i = 0;
			for (String clusteredId : idMeta.getComponentNames()) {
				if (i == 0) {
					where = from.where(eq(clusteredId, bindMarker(clusteredId)));
				} else {
					where.and(eq(clusteredId, bindMarker(clusteredId)));
				}
				i++;
			}
			statement = where;
		} else {
			String idName = idMeta.getPropertyName();
			statement = from.where(eq(idName, bindMarker(idName)));
		}
		return statement;
	}

	private RegularStatement prepareWhereClauseForUpdate(PropertyMeta idMeta, Assignments update, boolean prepareTTL) {
		Update.Where where = null;
		if (idMeta.isEmbeddedId()) {
			int i = 0;
			for (String clusteredId : idMeta.getComponentNames()) {
				if (i == 0) {
					where = update.where(eq(clusteredId, bindMarker(clusteredId)));
				} else {
					where.and(eq(clusteredId, bindMarker(clusteredId)));
				}
				i++;
			}
		} else {
			String idName = idMeta.getPropertyName();
			where = update.where(eq(idName, bindMarker(idName)));
		}

		if (prepareTTL) {
			return where.using(ttl(bindMarker("ttl")));
		} else {
			return where;
		}
	}

	public Map<String, PreparedStatement> prepareRemovePSs(Session session, EntityMeta entityMeta) {

		log.trace("Generate prepared statement for DELETE of {}", entityMeta);

		PropertyMeta idMeta = entityMeta.getIdMeta();

		Map<String, PreparedStatement> removePSs = new HashMap<>();

		Delete mainFrom = QueryBuilder.delete().from(entityMeta.getTableName());
		RegularStatement mainStatement = prepareWhereClauseForDelete(idMeta, mainFrom);
		removePSs.put(entityMeta.getTableName(), session.prepare(mainStatement.getQueryString()));

		return removePSs;
	}

	private RegularStatement prepareWhereClauseForDelete(PropertyMeta idMeta, Delete mainFrom) {
		RegularStatement mainStatement;
		if (idMeta.isEmbeddedId()) {
			Delete.Where where = null;
			int i = 0;
			for (String clusteredId : idMeta.getComponentNames()) {
				if (i == 0) {
					where = mainFrom.where(eq(clusteredId, bindMarker(clusteredId)));
				} else {
					where.and(eq(clusteredId, bindMarker(clusteredId)));
				}
				i++;
			}
			mainStatement = where;
		} else {
			String idName = idMeta.getPropertyName();
			mainStatement = mainFrom.where(eq(idName, bindMarker(idName)));
		}
		return mainStatement;
	}
}