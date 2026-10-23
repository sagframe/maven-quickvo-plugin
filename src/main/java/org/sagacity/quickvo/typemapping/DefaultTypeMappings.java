package org.sagacity.quickvo.typemapping;

/**
 * 通用默认类型映射表(native类型->jdbc类型/java类型),各数据库未覆盖差异项时兜底
 *
 * @author zhongxuchen
 * @date 2020-10-12
 */
public class DefaultTypeMappings {
	/**
	 * 默认数据类型匹配关系定义
	 */
	public static final String[][] jdbcTypeMapping = {
			// jdbc.type、 java.type、 importType、 precision(数据长度)、 scale(小数位)
			{ "REAL", "Float", "" }, { "TINYINT", "Integer", "" }, { "SHORT", "Short", "" },
			{ "SMALLINT", "Integer", "" }, { "MEDIUMINT", "Integer", "" }, { "SERIAL", "Integer", "" },
			{ "BIGSERIAL", "BigInteger", "java.math.BigInteger" }, { "SERIAL8", "BigInteger", "java.math.BigInteger" },
			{ "LARGEINT", "BigInteger", "java.math.BigInteger" }, { "BIGINT", "BigInteger", "java.math.BigInteger" },
			{ "BINARY_INTEGER", "Integer", "" }, { "INT", "Integer", "" }, { "INTEGER", "Integer", "" },
			{ "INT2", "Integer", "" }, { "_INT2", "Integer[]", "" }, { "INT4", "Integer", "" },
			{ "_INT4", "Integer[]", "" }, { "Int8", "Integer", "" }, { "_Int8", "Long[]", "" },
			{ "Int16", "Integer", "" }, { "Int32", "Long", "" }, { "Int64", "BigInteger", "java.math.BigInteger" },
			{ "Int128", "BigInteger", "java.math.BigInteger" },
			{ "BINARY_BIGINT", "BigInteger", "java.math.BigInteger" },
			{ "Int256", "BigInteger", "java.math.BigInteger" }, { "Enum8", "Integer", "" }, { "Enum16", "Integer", "" },
			{ "UInt8", "Integer", "" }, { "UInt16", "Integer", "" }, { "UInt32", "Long", "" },
			{ "UInt64", "BigInteger", "java.math.BigInteger" }, { "UInt128", "BigInteger", "java.math.BigInteger" },
			{ "UInt256", "BigInteger", "java.math.BigInteger" }, { "FLOAT", "Float", "" },
			{ "FLOAT4", "Float", "" }, { "_FLOAT4", "Float[]", "" }, { "FLOAT8", "Float", "" },
			{ "_FLOAT8", "Double[]", "" }, { "FLOAT32", "Float", "" }, { "FLOAT64", "Double", "" },
			{ "DOUBLE", "Double", "" }, { "NUMBER", "BigDecimal", "java.math.BigDecimal" },
			{ "MONEY", "BigDecimal", "java.math.BigDecimal" }, { "SMALLMONEY", "BigDecimal", "java.math.BigDecimal" },
			{ "NUMERIC", "BigDecimal", "java.math.BigDecimal" }, { "_NUMERIC", "BigDecimal[]", "java.math.BigDecimal" },
			{ "DECIMAL", "BigDecimal", "java.math.BigDecimal" }, { "DECIMAL32", "BigDecimal", "java.math.BigDecimal" },
			{ "DECIMAL64", "BigDecimal", "java.math.BigDecimal" },
			{ "DECIMAL128", "BigDecimal", "java.math.BigDecimal" },
			{ "DECIMAL256", "BigDecimal", "java.math.BigDecimal" },
			{ "BIGDECIMAL", "BigDecimal", "java.math.BigDecimal" },

			{ "TIMESTAMP", "Timestamp", "java.sql.Timestamp" }, { "TIMESTAMP(6)", "Timestamp", "java.sql.Timestamp" },
			{ "TIMESTAMPTZ", "OffsetDateTime", "java.time.OffsetDateTime" },
			{ "DATETIMEOFFSET", "OffsetDateTime", "java.time.OffsetDateTime" },
			{ "TIMESTAMP WITH TIMEZONE", "OffsetDateTime", "java.time.OffsetDateTime" },
			{ "TIMESTAMP_WITH_TIMEZONE", "OffsetDateTime", "java.time.OffsetDateTime" },

			{ "DATE", "LocalDate", "java.time.LocalDate" }, { "DATE32", "LocalDate", "java.time.LocalDate" },
			{ "DATETIME", "LocalDateTime", "java.time.LocalDateTime" },
			{ "DATETIME2", "LocalDateTime", "java.time.LocalDateTime" },
			{ "SMALLDATETIME", "LocalDateTime", "java.time.LocalDateTime" },
			{ "DATETIME64", "LocalDateTime", "java.time.LocalDateTime" },

			{ "TIME", "LocalTime", "java.time.LocalTime" },
			{ "TIME WITH TIMEZONE", "OffsetTime", "java.time.OffsetTime" },
			{ "TIME_WITH_TIMEZONE", "OffsetTime", "java.time.OffsetTime" },
			{ "TIMETZ", "OffsetTime", "java.time.OffsetTime" },

			{ "YEAR", "LocalDate", "java.time.LocalDate" }, { "VARCHAR", "String", "" }, { "_VARCHAR", "String[]", "" },
			{ "CHARACTER VARYING", "String", "" },
			{ "MEDIUMTEXT", "String", "" }, { "VARCHAR2", "String", "" }, { "LONG VARCHAR", "String", "" },
			{ "LONGVARCHAR", "String", "" }, { "NVARCHAR", "String", "" }, { "NVARCHAR2", "String", "" },
			{ "LONGNVARCHAR", "String", "" }, { "NCHAR", "String", "" }, { "JSON", "String", "" },
			{ "STRING", "String", "" }, { "UUID", "String", "" }, { "FixedSTRING", "String", "" },
			{ "CHAR", "String", "" }, { "BPCHAR", "String", "" }, { "CHARACTER", "String", "" },
			{ "BIT", "Boolean", "" }, { "BOOLEAN", "Boolean", "" }, { "BOOL", "Boolean", "" },
			// Clob/Blob系java类型为String/byte[]无需import;oracle.sql.BLOB在新驱动已移除会致编译失败
			{ "Clob", "String", "" }, { "DBCLOB", "String", "" },
			{ "NCLOB", "String", "" },
			{ "BLOB", "byte[]", "", "oracle" }, { "Blob", "byte[]", "" },
			{ "LONGBLOB", "byte[]", "" }, { "MEDIUMBLOB", "byte[]", "" },
			{ "TEXT", "String", "" }, { "_TEXT", "String[]", "" }, { "LONGTEXT", "String", "" },
			{ "TINYTEXT", "String", "" }, { "LONG VARGRAPHIC", "String", "" }, { "VARGRAPHIC", "String", "" },
			{ "GRAPHIC", "String", "" }, { "IMAGE", "byte[]", "" },
			{ "VARBINARY", "byte[]", "" },
			// 空间类型(oracle的SDO_GEOMETRY、sqlserver/mysql/达梦的geometry),默认String(WKT形式),
			// 可通过type-mapping自定义成org.locationtech.jts.geom.Geometry等(jts-core为可选依赖)
			{ "GEOMETRY", "String", "" }, { "SDO_GEOMETRY", "String", "" }, { "GEOGRAPHY", "String", "" },
			{ "POINT", "String", "" }, { "LINESTRING", "String", "" }, { "POLYGON", "String", "" },
			{ "MULTIPOINT", "String", "" }, { "MULTILINESTRING", "String", "" }, { "MULTIPOLYGON", "String", "" },
			{ "GEOMETRYCOLLECTION", "String", "" },
			{ "JSONB", "String", "" },
			// xml与sqlserver sql_variant系
			{ "XML", "String", "" }, { "SQL_VARIANT", "String", "" }, { "XMLTYPE", "String", "" },
			// sqlserver的uniqueidentifier/sysname/hierarchyid、mysql的enum/set、pg的varbit:
			// 缺映射会落到JdbcTypes.<原文>原样拼接,产出无法编译的生成物
			{ "UNIQUEIDENTIFIER", "String", "" }, { "SYSNAME", "String", "" }, { "HIERARCHYID", "String", "" },
			{ "ENUM", "String", "" }, { "SET", "String", "" }, { "NTEXT", "String", "" },
			{ "VARBIT", "byte[]", "" }, { "BIT VARYING", "byte[]", "" },
			// oracle系:RAW/ROWID/二进制浮点
			{ "RAW", "byte[]", "" }, { "LONG RAW", "byte[]", "" }, { "ROWID", "String", "" },
			{ "UROWID", "String", "" }, { "BINARY_FLOAT", "Float", "" }, { "BINARY_DOUBLE", "Double", "" },
			{ "BINARY", "byte[]", "" }, { "BYTEA", "byte[]", "" },
			{ "LONGVARBINARY", "byte[]", "" },
			// 向量类型(mysql heatwave、sqlserver2025、dm8、oracle
			// 23ai等),默认Float[](值为[1,2,3]形式),可通过type-mapping自定义成String、List<Float>等
			{ "VECTOR", "Float[]", "" } };

	public static final String[][] jdbcAry = { { "REAL", "REAL" }, { "YEAR", "DATE" }, { "DateTime", "DATE" },
			{ "Int", "INTEGER" }, { "Int2", "INTEGER" }, { "_Int2", "ARRAY" }, { "Int4", "INTEGER" },
			{ "_Int4", "ARRAY" }, { "Int8", "INTEGER" }, { "_Int8", "ARRAY" }, { "SMALLINT", "SMALLINT" },
			{ "LARGEINT", "BIGINT" }, { "MEDIUMINT", "INTEGER", "" }, { "Int16", "INTEGER" },
			{ "BINARY_INTEGER", "INTEGER" }, { "Int32", "INTEGER" }, { "Int64", "BIGINT" }, { "Int128", "BIGINT" },
			{ "Int256", "BIGINT" }, { "SERIAL8", "BIGINT" }, { "SERIAL", "INTEGER", "" }, { "BIGSERIAL", "BIGINT" },
			{ "BINARY_BIGINT", "BIGINT" }, { "Enum8", "INTEGER" }, { "Enum16", "INTEGER" }, { "UInt8", "INTEGER" },
			{ "UInt16", "INTEGER" }, { "UInt32", "INTEGER" }, { "UInt64", "BIGINT" }, { "UInt128", "BIGINT" },
			{ "UInt256", "BIGINT" }, { "FLOAT4", "FLOAT" }, { "_FLOAT4", "ARRAY" }, { "FLOAT8", "FLOAT" },
			{ "_FLOAT8", "ARRAY" }, { "FLOAT32", "FLOAT" }, { "FLOAT64", "DOUBLE" }, { "STRING", "VARCHAR" },
			{ "UUID", "VARCHAR" }, { "FixedSTRING", "VARCHAR" }, { "LONG VARGRAPHIC", "CLOB" },
			{ "VARGRAPHIC", "VARCHAR" }, { "GRAPHIC", "VARCHAR" }, { "LONG VARCHAR", "VARCHAR" }, { "DATE", "DATE" },
			{ "DATE32", "DATE" }, { "DATETIME", "DATE" }, { "DATETIME2", "TIMESTAMP" }, { "SMALLDATETIME", "TIMESTAMP" }, { "DATETIME64", "DATE" },
			{ "DOUBLE PRECISION", "DOUBLE" },
			{ "CHARACTER LARGE OBJECT", "CLOB" },
			{ "BINARY LARGE OBJECT", "BLOB" },

			{ "TIMESTAMP", "TIMESTAMP" }, { "TIMESTAMP(6)", "TIMESTAMP" }, { "FLOAT", "FLOAT" }, { "DOUBLE", "DOUBLE" }, { "SHORT", "SMALLINT" }, { "BIGDECIMAL", "DECIMAL" },
			{ "TIMESTAMPTZ", "TIMESTAMP_WITH_TIMEZONE" },
			{ "TIMESTAMP WITH TIMEZONE", "TIMESTAMP_WITH_TIMEZONE" },
			{ "TIMESTAMP_WITH_TIMEZONE", "TIMESTAMP_WITH_TIMEZONE" }, { "DATETIMEOFFSET", "TIMESTAMP_WITH_TIMEZONE" },
			{ "UNIQUEIDENTIFIER", "VARCHAR" }, { "SYSNAME", "VARCHAR" }, { "HIERARCHYID", "VARCHAR" },
			{ "ENUM", "VARCHAR" }, { "SET", "VARCHAR" }, { "NTEXT", "CLOB" },
			{ "VARBIT", "VARBINARY" }, { "BIT VARYING", "VARBINARY" },

			{ "TIME", "TIME" }, { "TIME WITH TIMEZONE", "TIME_WITH_TIMEZONE" },
			{ "TIME_WITH_TIMEZONE", "TIME_WITH_TIMEZONE" }, { "TIMETZ", "TIME_WITH_TIMEZONE" },

			{ "CHAR", "CHAR" }, { "CHARACTER", "CHAR" }, { "NCHAR", "NCHAR" }, { "CLOB", "CLOB" },
			{ "NCLOB", "NCLOB" }, { "LONGVARCHAR", "LONGVARCHAR" }, { "LONGNVARCHAR", "LONGNVARCHAR" },
			{ "DBCLOB", "CLOB" }, { "BLOB", "BLOB" }, { "BINARY", "BINARY" },
			{ "VARBINARY", "BINARY" }, { "LONGVARBINARY", "BINARY" }, { "BYTEA", "BINARY" }, { "LONGBLOB", "BLOB" },
			{ "BOOLEAN", "BOOLEAN" }, { "BOOL", "BOOLEAN" }, { "MEDIUMBLOB", "BLOB" }, { "LONGTEXT", "VARCHAR" },
			{ "MEDIUMTEXT", "VARCHAR" }, { "TEXT", "VARCHAR" }, { "_TEXT", "ARRAY" }, { "TINYTEXT", "VARCHAR" },
			{ "VARCHAR", "VARCHAR" }, { "CHARACTER VARYING", "VARCHAR" }, { "_VARCHAR", "ARRAY" }, { "NVARCHAR", "VARCHAR" }, { "NVARCHAR2", "VARCHAR" },
			{ "BPCHAR", "VARCHAR" }, { "VARCHAR2", "VARCHAR" }, { "TINYINT", "TINYINT" }, { "INT", "INTEGER" },
			{ "INTEGER", "INTEGER" }, { "BIGINT", "BIGINT" }, { "BIT", "BIT" }, { "NUMBER", "DECIMAL" },
			{ "DECIMAL", "DECIMAL" }, { "DECIMAL32", "DECIMAL" }, { "DECIMAL64", "DECIMAL" },
			{ "DECIMAL128", "DECIMAL" }, { "DECIMAL256", "DECIMAL" }, { "MONEY", "DECIMAL" },
			{ "SMALLMONEY", "DECIMAL" }, { "NUMERIC", "DECIMAL" }, { "_NUMERIC", "ARRAY" },
			{ "IMAGE", "LONGVARBINARY" },
			// 空间类型统一转JdbcTypes.GEOMETRY(含mysql的POINT等子类型名)
			{ "GEOMETRY", "GEOMETRY" }, { "SDO_GEOMETRY", "GEOMETRY" }, { "GEOGRAPHY", "GEOMETRY" },
			{ "POINT", "GEOMETRY" }, { "LINESTRING", "GEOMETRY" }, { "POLYGON", "GEOMETRY" },
			{ "MULTIPOINT", "GEOMETRY" }, { "MULTILINESTRING", "GEOMETRY" }, { "MULTIPOLYGON", "GEOMETRY" },
			{ "GEOMETRYCOLLECTION", "GEOMETRY" }, { "JSON", "JSON" },
			{ "XML", "SQLXML" }, { "SQL_VARIANT", "OTHER" }, { "XMLTYPE", "SQLXML" },
			{ "RAW", "BINARY" }, { "LONG RAW", "LONGVARBINARY" }, { "ROWID", "ROWID" },
			{ "UROWID", "ROWID" }, { "BINARY_FLOAT", "FLOAT" }, { "BINARY_DOUBLE", "DOUBLE" },
			{ "JSONB", "JSONB" }, { "VECTOR", "VECTOR" } };
}
