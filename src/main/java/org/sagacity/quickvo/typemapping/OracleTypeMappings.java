package org.sagacity.quickvo.typemapping;

/**
 * Oracle默认类型映射表
 *
 * @author zhongxuchen
 * @date 2020-10-12
 */
public class OracleTypeMappings {
	/**
	 * Oracle默认数据类型匹配关系,基于DefaultTypeMappings派生保持单一数据源,
	 * 仅覆盖Oracle差异项:Oracle的DATE类型含时分秒,映射LocalDateTime避免时间部分丢失
	 */
	public static final String[][] jdbcTypeMapping = buildJdbcTypeMapping();

	private static String[][] buildJdbcTypeMapping() {
		String[][] source = DefaultTypeMappings.jdbcTypeMapping;
		String[][] result = new String[source.length][];
		for (int i = 0; i < source.length; i++) {
			// Oracle DATE含时分秒
			result[i] = source[i][0].equalsIgnoreCase("DATE")
					? new String[] { "DATE", "LocalDateTime", "java.time.LocalDateTime" }
					: source[i];
		}
		return result;
	}

	public static final String[][] jdbcAry = { { "REAL", "REAL" }, { "YEAR", "DATE" }, { "DateTime", "DATE" },
			{ "Int", "INTEGER" }, { "Int2", "INTEGER" }, { "_Int2", "ARRAY" }, { "Int4", "INTEGER" },
			{ "_Int4", "ARRAY" }, { "Int8", "INTEGER" }, { "_Int8", "ARRAY" }, { "SMALLINT", "SMALLINT" }, { "SHORT", "SMALLINT" }, { "FLOAT", "FLOAT" }, { "DOUBLE", "DOUBLE" }, { "BIGDECIMAL", "DECIMAL" }, { "BINARY_INTEGER", "INTEGER" }, { "SHORT", "SMALLINT" },
			{ "LARGEINT", "BIGINT" }, { "MEDIUMINT", "INTEGER", "" }, { "Int16", "INTEGER" }, { "Int32", "INTEGER" },
			{ "Int64", "BIGINT" }, { "SERIAL8", "BIGINT" }, { "BINARY_BIGINT", "BIGINT" }, { "Int128", "BIGINT" }, { "Int256", "BIGINT" }, { "UInt128", "BIGINT" }, { "UInt256", "BIGINT" }, { "SERIAL", "INTEGER", "" }, { "BIGSERIAL", "BIGINT" },
		{ "Enum8", "INTEGER" }, { "Enum16", "INTEGER" }, { "UInt8", "INTEGER" }, { "UInt16", "INTEGER" },
		{ "UInt32", "INTEGER" }, { "UInt64", "BIGINT" }, { "FLOAT4", "FLOAT" }, { "_FLOAT4", "ARRAY" },
		{ "FLOAT8", "FLOAT" }, { "_FLOAT8", "ARRAY" }, { "FLOAT32", "FLOAT" }, { "BINARY_FLOAT", "FLOAT" }, { "BINARY_DOUBLE", "DOUBLE" }, { "FLOAT64", "DOUBLE" },
		// 与Default派生的jdbcTypeMapping键保持一致(一致性测试要求mapping键在jdbcAry有映射或自身即合法枚举)
		{ "UNIQUEIDENTIFIER", "VARCHAR" }, { "SYSNAME", "VARCHAR" }, { "HIERARCHYID", "VARCHAR" },
		{ "ENUM", "VARCHAR" }, { "SET", "VARCHAR" }, { "NTEXT", "CLOB" },
		{ "VARBIT", "VARBINARY" }, { "BIT VARYING", "VARBINARY" },
			{ "STRING", "VARCHAR" }, { "FixedSTRING", "VARCHAR" }, { "LONG VARGRAPHIC", "CLOB" },
			{ "VARGRAPHIC", "VARCHAR" }, { "GRAPHIC", "VARCHAR" }, { "LONG VARCHAR", "VARCHAR" }, { "DATE", "DATE" },
			{ "DATETIME", "DATE" },

			{ "TIMESTAMP", "TIMESTAMP" }, { "TIMESTAMP(6)", "TIMESTAMP" }, { "TIMESTAMPTZ", "TIMESTAMP_WITH_TIMEZONE" },
			{ "TIMESTAMP WITH TIMEZONE", "TIMESTAMP_WITH_TIMEZONE" },
			{ "TIMESTAMP_WITH_TIMEZONE", "TIMESTAMP_WITH_TIMEZONE" }, { "DATETIMEOFFSET", "TIMESTAMP_WITH_TIMEZONE" },

			{ "TIME", "TIME" }, { "TIME WITH TIMEZONE", "TIME_WITH_TIMEZONE" },
			{ "TIME_WITH_TIMEZONE", "TIME_WITH_TIMEZONE" }, { "TIMETZ", "TIME_WITH_TIMEZONE" },

			{ "CHAR", "CHAR" }, { "CHARACTER", "CHAR" }, { "NCHAR", "NCHAR" }, { "CHARACTER VARYING", "VARCHAR" }, { "LONGVARCHAR", "LONGVARCHAR" }, { "LONGNVARCHAR", "LONGNVARCHAR" }, { "CLOB", "CLOB" }, { "NCLOB", "NCLOB" }, { "DBCLOB", "CLOB" }, { "BLOB", "BLOB" }, { "BINARY", "BINARY" }, { "RAW", "BINARY" }, { "LONG RAW", "LONGVARBINARY" }, { "ROWID", "ROWID" }, { "UROWID", "ROWID" },
			{ "VARBINARY", "BINARY" }, { "LONGVARBINARY", "BINARY" }, { "BYTEA", "BINARY" }, { "LONGBLOB", "BLOB" },
			{ "BOOLEAN", "BOOLEAN" }, { "BOOL", "BOOLEAN" }, { "MEDIUMBLOB", "BLOB" }, { "LONGTEXT", "VARCHAR" },
			{ "MEDIUMTEXT", "VARCHAR" }, { "TEXT", "VARCHAR" }, { "_TEXT", "ARRAY" }, { "TINYTEXT", "VARCHAR" },
			{ "VARCHAR", "VARCHAR" }, { "_VARCHAR", "ARRAY" }, { "NVARCHAR", "VARCHAR" }, { "NVARCHAR2", "VARCHAR" },
			{ "BPCHAR", "VARCHAR" }, { "VARCHAR2", "VARCHAR" }, { "TINYINT", "TINYINT" }, { "INT", "INTEGER" },
			{ "INTEGER", "INTEGER" }, { "BIGINT", "BIGINT" }, { "BIT", "BIT" }, { "NUMBER", "DECIMAL" },
			{ "DECIMAL", "DECIMAL" }, { "DECIMAL32", "DECIMAL" }, { "DECIMAL64", "DECIMAL" }, { "DECIMAL128", "DECIMAL" }, { "DECIMAL256", "DECIMAL" }, { "MONEY", "DECIMAL" }, { "SMALLMONEY", "DECIMAL" }, { "NUMERIC", "DECIMAL" },
			{ "_NUMERIC", "ARRAY" }, { "IMAGE", "LONGVARBINARY" },
			// 空间类型(oracle为SDO_GEOMETRY)统一转JdbcTypes.GEOMETRY
			{ "GEOMETRY", "GEOMETRY" }, { "SDO_GEOMETRY", "GEOMETRY" },
			{ "JSON", "JSON" }, { "XML", "SQLXML" }, { "SQL_VARIANT", "OTHER" }, { "XMLTYPE", "SQLXML" }, { "JSONB", "JSONB" }, { "VECTOR", "VECTOR" },
			// 与Default对齐的补充条目
			{ "DATE32", "DATE" }, { "DATETIME2", "TIMESTAMP" }, { "SMALLDATETIME", "TIMESTAMP" },
			{ "DATETIME64", "DATE" }, { "UUID", "VARCHAR" },
			{ "GEOMETRY", "GEOMETRY" }, { "GEOGRAPHY", "GEOMETRY" }, { "POINT", "GEOMETRY" },
			{ "LINESTRING", "GEOMETRY" }, { "POLYGON", "GEOMETRY" }, { "MULTIPOINT", "GEOMETRY" },
			{ "MULTILINESTRING", "GEOMETRY" }, { "MULTIPOLYGON", "GEOMETRY" },
			{ "GEOMETRYCOLLECTION", "GEOMETRY" } };
}
