package org.sagacity.quickvo.typemapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.sagacity.quickvo.core.TypeMappingResolver;
import org.sagacity.quickvo.datasource.DbType;

/**
 * 类型映射表结构性单测:无死行(被前行忽略大小写遮挡)、jdbcAry覆盖jdbcTypeMapping全部native类型
 * (保证@Column type=输出的JdbcTypes枚举合法),并固化本轮回补的类型
 */
public class TypeMappingTableTest {

	/** sqltoy JdbcTypes的合法常量清单 */
	private static final Set<String> SQLTOY_JDBC_TYPES = Set.of("BIT", "TINYINT", "SMALLINT", "INTEGER", "BIGINT",
			"FLOAT", "REAL", "DOUBLE", "NUMERIC", "DECIMAL", "CHAR", "VARCHAR", "LONGVARCHAR", "DATE", "TIME",
			"TIMESTAMP", "BINARY", "VARBINARY", "LONGVARBINARY", "NULL", "OTHER", "JAVA_OBJECT", "DISTINCT",
			"STRUCT", "ARRAY", "BLOB", "CLOB", "REF", "DATALINK", "BOOLEAN", "ROWID", "NCHAR", "NVARCHAR",
			"LONGNVARCHAR", "NCLOB", "SQLXML", "REF_CURSOR", "TIME_WITH_TIMEZONE", "TIMESTAMP_WITH_TIMEZONE",
			"JSON", "JSONB", "HSTORE", "UUID", "VECTOR", "GEOMETRY");

	private void assertNoDeadRows(String table, String[][] mapping) {
		Map<String, Integer> seen = new HashMap<>();
		for (int i = 0; i < mapping.length; i++) {
			String key = mapping[i][0].toLowerCase();
			Integer prev = seen.get(key);
			final String current = mapping[i][0];
			final int prevIndex = prev == null ? -1 : prev;
			// 3元素行无条件命中,遮挡后续同key行;4元素方言限定行仅特定方言命中,不构成遮挡
			assertTrue(prev == null || mapping[prev].length == 4,
					() -> table + " 死行: " + current + " 被行" + prevIndex + "遮挡");
			if (prev == null) {
				seen.put(key, i);
			}
		}
	}

	private void assertJdbcAryCovers(String table, String[][] mapping, String[][] jdbcAry) {
		Set<String> aryTypes = new HashSet<>();
		Set<String> aryValues = new HashSet<>();
		for (String[] row : jdbcAry) {
			aryTypes.add(row[0].toLowerCase());
			aryValues.add(row[1]);
			// jdbcAry的目标值必须是sqltoy合法枚举
			assertTrue(SQLTOY_JDBC_TYPES.contains(row[1]), () -> table + " jdbcAry非法目标: " + row[0] + "->" + row[1]);
		}
		for (String[] row : mapping) {
			String key = row[0].toLowerCase();
			// native类型要么在jdbcAry有映射,要么其native名本身即合法枚举(如TIMESTAMP/BOOLEAN)
			assertTrue(aryTypes.contains(key) || SQLTOY_JDBC_TYPES.contains(row[0].toUpperCase()),
					() -> table + " jdbcTypeMapping有但jdbcAry缺失: " + row[0]);
		}
	}

	@Test
	public void defaultTableShouldBeConsistent() {
		assertNoDeadRows("Default", DefaultTypeMappings.jdbcTypeMapping);
		assertJdbcAryCovers("Default", DefaultTypeMappings.jdbcTypeMapping, DefaultTypeMappings.jdbcAry);
	}

	@Test
	public void postgresTableShouldBeConsistent() {
		assertNoDeadRows("Postgres", PostgresTypeMappings.jdbcTypeMapping);
		assertJdbcAryCovers("Postgres", PostgresTypeMappings.jdbcTypeMapping, PostgresTypeMappings.jdbcAry);
	}

	@Test
	public void oracleTableShouldBeConsistent() {
		assertNoDeadRows("Oracle", OracleTypeMappings.jdbcTypeMapping);
		assertJdbcAryCovers("Oracle", OracleTypeMappings.jdbcTypeMapping, OracleTypeMappings.jdbcAry);
	}

	@Test
	public void newlyAddedTypesShouldMap() {
		// pg系统类型(此前兜底String+警告)
		assertEquals("String", TypeMappingResolver.defaultJavaType("oid", "postgresql", DbType.POSTGRESQL)[0]);
		assertEquals("String", TypeMappingResolver.defaultJavaType("interval", "postgresql", DbType.POSTGRESQL)[0]);
		assertEquals("String", TypeMappingResolver.defaultJavaType("inet", "postgresql", DbType.POSTGRESQL)[0]);
		// oracle系:RAW/ROWID/二进制浮点
		assertEquals("byte[]", TypeMappingResolver.defaultJavaType("RAW", "oracle", DbType.ORACLE)[0]);
		assertEquals("String", TypeMappingResolver.defaultJavaType("ROWID", "oracle", DbType.ORACLE)[0]);
		assertEquals("Float", TypeMappingResolver.defaultJavaType("BINARY_FLOAT", "oracle", DbType.ORACLE)[0]);
		assertEquals("Double", TypeMappingResolver.defaultJavaType("BINARY_DOUBLE", "oracle", DbType.ORACLE)[0]);
		assertEquals("String", TypeMappingResolver.defaultJavaType("XMLTYPE", "oracle", DbType.ORACLE)[0]);
		// 通用:XML/SQL_VARIANT
		assertEquals("String", TypeMappingResolver.defaultJavaType("XML", "mysql", DbType.MYSQL)[0]);
		assertEquals("String", TypeMappingResolver.defaultJavaType("SQL_VARIANT", "sqlserver", DbType.SQLSERVER)[0]);
	}

	@Test
	public void previouslyIllegalEnumsShouldMapToValidJdbcType() {
		// 此前SHORT/BIGDECIMAL/DATETIMEOFFSET/BINARY_INTEGER的jdbcAry缺失会生成非法JdbcTypes枚举
		assertEquals("SMALLINT", TypeMappingResolver.toJdbcType("SHORT", DbType.MYSQL));
		assertEquals("DECIMAL", TypeMappingResolver.toJdbcType("BIGDECIMAL", DbType.MYSQL));
		assertEquals("TIMESTAMP_WITH_TIMEZONE", TypeMappingResolver.toJdbcType("DATETIMEOFFSET", DbType.SQLSERVER));
		assertEquals("INTEGER", TypeMappingResolver.toJdbcType("BINARY_INTEGER", DbType.ORACLE));
		// h2的CHARACTER VARYING
		assertEquals("VARCHAR", TypeMappingResolver.toJdbcType("CHARACTER VARYING", DbType.H2));
	}
}
