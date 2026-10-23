package org.sagacity.quickvo.typemapping;

/**
 * postgresql血缘数据库默认类型映射表(含jsonb/vector等扩展类型)
 *
 * @author zhongxuchen
 * @date 2020-10-12
 */
public class PostgresTypeMappings {
	public static final String[][] jdbcTypeMapping = {
			// jdbc.type java.type importType precision(数据长度) scale(小数位)
			{ "REAL", "Float", "" }, { "TINYINT", "Integer", "" }, { "SHORT", "Short", "" },
			{ "SMALLINT", "Integer", "" }, { "MEDIUMINT", "Integer", "" }, { "SERIAL", "Integer", "" },
			{ "BIGSERIAL", "BigInteger", "java.math.BigInteger" }, { "SERIAL8", "BigInteger", "java.math.BigInteger" },
			{ "BIGINT", "BigInteger", "java.math.BigInteger" }, { "INT", "Integer", "" }, { "INTEGER", "Integer", "" },
			{ "INT2", "Integer", "" }, { "_INT2", "Integer[]", "" }, { "INT4", "Integer", "" },
			{ "_INT4", "Integer[]", "" }, { "Int8", "Long", "" }, { "_Int8", "Long[]", "" },
			{ "FLOAT", "Float", "" }, { "FLOAT4", "Float", "" },
			{ "_FLOAT4", "Float[]", "" }, { "FLOAT8", "Double", "" }, { "_FLOAT8", "Double[]", "" },
			{ "DOUBLE", "Double", "" }, { "NUMBER", "BigDecimal", "java.math.BigDecimal" },
			{ "MONEY", "BigDecimal", "java.math.BigDecimal" }, { "SMALLMONEY", "BigDecimal", "java.math.BigDecimal" },
			{ "NUMERIC", "BigDecimal", "java.math.BigDecimal" }, { "_NUMERIC", "BigDecimal[]", "java.math.BigDecimal" },
			{ "DECIMAL", "BigDecimal", "java.math.BigDecimal" },

			{ "TIMESTAMP", "Timestamp", "java.sql.Timestamp" }, { "TIMESTAMP(6)", "Timestamp", "java.sql.Timestamp" },
			{ "TIMESTAMPTZ", "OffsetDateTime", "java.time.OffsetDateTime" },
			{ "TIMESTAMP WITH TIMEZONE", "OffsetDateTime", "java.time.OffsetDateTime" },
			{ "TIMESTAMP_WITH_TIMEZONE", "OffsetDateTime", "java.time.OffsetDateTime" },

			{ "BIGDECIMAL", "BigDecimal", "java.math.BigDecimal" }, { "DATE", "LocalDate", "java.time.LocalDate" },
			{ "DATETIME", "LocalDateTime", "java.time.LocalDateTime" },

			{ "TIME", "LocalTime", "java.time.LocalTime" },
			{ "TIME WITH TIMEZONE", "OffsetTime", "java.time.OffsetTime" },
			{ "TIME_WITH_TIMEZONE", "OffsetTime", "java.time.OffsetTime" },
			{ "TIMETZ", "OffsetTime", "java.time.OffsetTime" },

			{ "YEAR", "LocalDate", "java.time.LocalDate" }, { "VARCHAR", "String", "" }, { "_VARCHAR", "String[]", "" },
			{ "MEDIUMTEXT", "String", "" }, { "VARCHAR2", "String", "" },
			{ "LONGVARCHAR", "String", "" }, { "NVARCHAR", "String", "" }, { "LONGNVARCHAR", "String", "" },
			{ "NCHAR", "String", "" }, { "JSON", "String", "" }, { "STRING", "String", "" }, { "CHAR", "String", "" },
			{ "BPCHAR", "String", "" }, { "CHARACTER", "String", "" }, { "BIT", "Boolean", "" },
			{ "BOOLEAN", "Boolean", "" }, { "BOOL", "Boolean", "" }, { "TEXT", "String", "" },
			{ "_TEXT", "String[]", "" }, { "LONGTEXT", "String", "" }, { "TINYTEXT", "String", "" },
			{ "LONG VARCHAR", "String", "" },
			// 空间类型(postgis扩展的geometry),默认String(WKT形式),可通过type-mapping自定义成org.locationtech.jts.geom.Geometry等
			{ "GEOMETRY", "String", "" }, { "SDO_GEOMETRY", "String", "" },
			{ "JSONB", "String", "" }, { "BINARY", "byte[]", "" }, { "BYTEA", "byte[]", "" }, { "UUID", "String", "" },
			// pg变长位串(varbit/bit varying)映射byte[]
			{ "VARBIT", "byte[]", "" }, { "BIT VARYING", "byte[]", "" },
			{ "TSVECTOR", "String", "" },
			// pg系统类型
			{ "OID", "String", "" }, { "INTERVAL", "String", "" }, { "INET", "String", "" },
			{ "CIDR", "String", "" }, { "MACADDR", "String", "" }, { "NAME", "String", "" },
			{ "XID", "String", "" }, { "REFCURSOR", "String", "" },
			// 向量类型(pgvector的vector、gaussdb企业版的floatvector),默认Float[](值为[1,2,3]形式),可通过type-mapping自定义成String、List<Float>等
			{ "VECTOR", "Float[]", "" }, { "FLOATVECTOR", "Float[]", "" } };

	public static final String[][] jdbcAry = { { "REAL", "REAL" }, { "YEAR", "DATE" }, { "DateTime", "DATE" },
			{ "Int", "INTEGER" }, { "Int2", "INTEGER" }, { "_Int2", "ARRAY" }, { "Int4", "INTEGER" },
			{ "_Int4", "ARRAY" }, { "Int8", "BIGINT" }, { "_Int8", "ARRAY" }, { "SMALLINT", "SMALLINT" }, { "MEDIUMINT", "INTEGER" }, { "REFCURSOR", "OTHER" },
			{ "SERIAL8", "BIGINT" }, { "SERIAL", "INTEGER", "" }, { "BIGSERIAL", "BIGINT" }, { "FLOAT", "FLOAT" }, { "DOUBLE", "DOUBLE" }, { "SHORT", "SMALLINT" }, { "BIGDECIMAL", "DECIMAL" },
			{ "FLOAT4", "FLOAT" },
			{ "_FLOAT4", "ARRAY" }, { "FLOAT8", "DOUBLE" }, { "_FLOAT8", "ARRAY" }, { "STRING", "VARCHAR" },
			{ "LONG VARCHAR", "VARCHAR" },

			{ "DATE", "DATE" }, { "DATETIME", "DATE" },

			{ "TIMESTAMP", "TIMESTAMP" }, { "TIMESTAMP(6)", "TIMESTAMP" }, { "TIMESTAMPTZ", "TIMESTAMP_WITH_TIMEZONE" },
			{ "TIMESTAMP WITH TIMEZONE", "TIMESTAMP_WITH_TIMEZONE" },
			{ "TIMESTAMP_WITH_TIMEZONE", "TIMESTAMP_WITH_TIMEZONE" },
			{ "VARBIT", "VARBINARY" }, { "BIT VARYING", "VARBINARY" },

			{ "TIME", "TIME" }, { "TIME WITH TIMEZONE", "TIME_WITH_TIMEZONE" },
			{ "TIME_WITH_TIMEZONE", "TIME_WITH_TIMEZONE" }, { "TIMETZ", "TIME_WITH_TIMEZONE" },

			{ "CHAR", "CHAR" }, { "CHARACTER", "CHAR" }, { "NCHAR", "NCHAR" }, { "LONGVARCHAR", "LONGVARCHAR" },
			{ "LONGNVARCHAR", "LONGNVARCHAR" }, { "BINARY", "BINARY" }, { "BYTEA", "BINARY" }, { "BOOLEAN", "BOOLEAN" },
			{ "BOOL", "BOOLEAN" }, { "LONGTEXT", "VARCHAR" }, { "MEDIUMTEXT", "VARCHAR" }, { "TEXT", "VARCHAR" },
			{ "_TEXT", "ARRAY" }, { "TINYTEXT", "VARCHAR" }, { "VARCHAR", "VARCHAR" }, { "_VARCHAR", "ARRAY" },
			{ "NVARCHAR", "VARCHAR" }, { "BPCHAR", "VARCHAR" }, { "VARCHAR2", "VARCHAR" }, { "TINYINT", "TINYINT" },
			{ "INT", "INTEGER" }, { "INTEGER", "INTEGER" }, { "BIGINT", "BIGINT" }, { "BIT", "BIT" },
			{ "NUMBER", "DECIMAL" }, { "DECIMAL", "DECIMAL" }, { "MONEY", "DECIMAL" }, { "SMALLMONEY", "DECIMAL" },
			{ "NUMERIC", "DECIMAL" }, { "_NUMERIC", "ARRAY" },
			// 空间类型(postgis扩展)统一转JdbcTypes.GEOMETRY(子类型名兜底,防止生成不存在的JdbcTypes.POINT等)
			{ "GEOMETRY", "GEOMETRY" }, { "SDO_GEOMETRY", "GEOMETRY" }, { "GEOGRAPHY", "GEOMETRY" },
			{ "POINT", "GEOMETRY" }, { "LINESTRING", "GEOMETRY" }, { "POLYGON", "GEOMETRY" },
			{ "MULTIPOINT", "GEOMETRY" }, { "MULTILINESTRING", "GEOMETRY" }, { "MULTIPOLYGON", "GEOMETRY" },
			{ "GEOMETRYCOLLECTION", "GEOMETRY" },
			{ "UUID", "VARCHAR" }, { "TSVECTOR", "VARCHAR" }, { "OID", "OTHER" }, { "INTERVAL", "OTHER" }, { "INET", "OTHER" },
			{ "CIDR", "OTHER" }, { "MACADDR", "OTHER" }, { "NAME", "VARCHAR" }, { "XID", "OTHER" }, { "JSON", "JSON" }, { "JSONB", "JSONB" },
			// 向量类型统一转JdbcTypes.VECTOR(floatvector为gaussdb企业版向量类型名)
			{ "VECTOR", "VECTOR" }, { "FLOATVECTOR", "VECTOR" } };
}
