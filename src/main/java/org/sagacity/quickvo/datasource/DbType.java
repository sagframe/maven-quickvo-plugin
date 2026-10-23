package org.sagacity.quickvo.datasource;

import org.sagacity.quickvo.typemapping.DefaultTypeMappings;
import org.sagacity.quickvo.typemapping.OracleTypeMappings;
import org.sagacity.quickvo.typemapping.PostgresTypeMappings;
import org.sagacity.quickvo.utils.StringUtil;

/**
 * 数据库类型枚举,替代原DBUtil中int常量+长if-else识别链。
 * 枚举值与原DBType的int标识保持一对一,识别顺序与原getCurrentDBDialect一致,
 * 避免因前缀包含关系(如mariadb包含mysql)改变识别结果。
 *
 * @author zhongxuchen
 */
public enum DbType {
	UNDEFINE, ORACLE, ORACLE11, DB2, SQLSERVER, MYSQL, MYSQL57, POSTGRESQL, POSTGRESQL14, CLICKHOUSE, GAUSSDB,
	SQLITE, TIDB, OCEANBASE, DM, KINGBASE, MONGO, ES, TDENGINE, IMPALA, H2, OSCAR, MOGDB, VASTBASE, OPENGAUSS, HANA,
	STARDB, HIGHGO;

	/** JDBC URL schema段→数据库类型特征映射(借鉴sqltoy DataSourceUtils,URL特征优先于产品名) */
	private static final java.util.Map<String, DbType> URL_SCHEMA_TYPE = new java.util.HashMap<>();

	static {
		URL_SCHEMA_TYPE.put("oracle", ORACLE);
		URL_SCHEMA_TYPE.put("mysql", MYSQL);
		URL_SCHEMA_TYPE.put("mariadb", MYSQL);
		URL_SCHEMA_TYPE.put("postgresql", POSTGRESQL);
		URL_SCHEMA_TYPE.put("opengauss", OPENGAUSS);
		URL_SCHEMA_TYPE.put("mogdb", MOGDB);
		URL_SCHEMA_TYPE.put("vastbase", VASTBASE);
		URL_SCHEMA_TYPE.put("stardb", STARDB);
		URL_SCHEMA_TYPE.put("gaussdb", GAUSSDB);
		URL_SCHEMA_TYPE.put("dm", DM);
		URL_SCHEMA_TYPE.put("oceanbase", OCEANBASE);
		URL_SCHEMA_TYPE.put("sap", HANA);
		URL_SCHEMA_TYPE.put("clickhouse", CLICKHOUSE);
		URL_SCHEMA_TYPE.put("sqlite", SQLITE);
		URL_SCHEMA_TYPE.put("h2", H2);
		URL_SCHEMA_TYPE.put("kingbase8", KINGBASE);
		URL_SCHEMA_TYPE.put("kingbase", KINGBASE);
		URL_SCHEMA_TYPE.put("db2", DB2);
		URL_SCHEMA_TYPE.put("sqlserver", SQLSERVER);
		URL_SCHEMA_TYPE.put("mssql", SQLSERVER);
		URL_SCHEMA_TYPE.put("oscar", OSCAR);
		URL_SCHEMA_TYPE.put("mongo", MONGO);
		URL_SCHEMA_TYPE.put("mongodb", MONGO);
		URL_SCHEMA_TYPE.put("tdengine", TDENGINE);
		URL_SCHEMA_TYPE.put("impala", IMPALA);
		// 瀚高官方驱动URL形态jdbc:highgo://(产品名伪装PostgreSQL,仅URL特征可识别)
		URL_SCHEMA_TYPE.put("highgo", HIGHGO);
	}

	/**
	 * 提取JDBC URL的schema段(jdbc:xxx:中的xxx,小写),非jdbc前缀返回null
	 */
	static String urlSchema(String url) {
		if (url == null || !url.startsWith("jdbc:")) {
			return null;
		}
		int schemaEnd = url.indexOf(':', 5);
		if (schemaEnd <= 5) {
			return null;
		}
		return url.substring(5, schemaEnd).toLowerCase(java.util.Locale.ROOT);
	}

	/**
	 * 依据JDBC URL特征+产品名+主版本号识别数据库类型,URL schema段特征优先:
	 * 大量衍生库的getDatabaseProductName()误报母产品(openGauss 5.0实测上报PostgreSQL、
	 * 达梦compatibleMode=oracle时伪装Oracle、OceanBase/StarRocks上报MySQL),
	 * 而JDBC URL的schema段由驱动忠实保留;URL未命中走产品名链。
	 * 版本折叠规则:oracle<=11为ORACLE11,mysql<=5为MYSQL57,postgresql<15为POSTGRESQL14(POSTGRESQL默认代表15+),greenplum按POSTGRESQL。
	 */
	public static DbType resolve(String url, String productName, int majorVersion) {
		// URL schema特征优先(命中后仍执行版本折叠)
		String schema = urlSchema(url);
		if (schema != null) {
			DbType urlType = URL_SCHEMA_TYPE.get(schema);
			if (urlType != null) {
				return foldVersion(urlType, majorVersion);
			}
		}
		return foldVersion(resolve(productName, majorVersion), majorVersion);
	}

	/** 版本差异折叠(oracle/mysql/pg三族),非版本敏感类型原样返回 */
	private static DbType foldVersion(DbType dbType, int majorVersion) {
		if (dbType == ORACLE && majorVersion <= 11 && majorVersion > 0) {
			return ORACLE11;
		}
		if (dbType == MYSQL && majorVersion <= 5 && majorVersion > 0) {
			return MYSQL57;
		}
		if (dbType == POSTGRESQL && majorVersion > 0 && majorVersion < 15) {
			return POSTGRESQL14;
		}
		return dbType;
	}

	/**
	 * 依据jdbc产品名和主版本号识别数据库类型(不含URL特征,优先使用resolve(url,productName,majorVersion));
	 * 版本区分规则:oracle<=11为ORACLE11,mysql<=5为MYSQL57,postgresql<15为POSTGRESQL14(POSTGRESQL默认代表15+),greenplum按POSTGRESQL处理。
	 */
	public static DbType resolve(String productName, int majorVersion) {
		if (productName == null) {
			return UNDEFINE;
		}
		// 剔除空白后的产品名匹配,顺序保持与历史行为一致
		String name = productName.replaceAll("\\s+", "");
		if (StringUtil.indexOfIgnoreCase(name, "oracle") != -1) {
			// majorVersion<=0(探测失败)时不折叠为旧版本,按当前版本处理
			return majorVersion > 0 && majorVersion <= 11 ? ORACLE11 : ORACLE;
		}
		if (StringUtil.indexOfIgnoreCase(name, "mysql") != -1
				|| StringUtil.indexOfIgnoreCase(name, "mariadb") != -1
				|| StringUtil.indexOfIgnoreCase(name, "innosql") != -1) {
			return majorVersion > 0 && majorVersion <= 5 ? MYSQL57 : MYSQL;
		}
		if (StringUtil.indexOfIgnoreCase(name, "postgresql") != -1) {
			return (majorVersion > 0 && majorVersion < 15) ? POSTGRESQL14 : POSTGRESQL;
		}
		if (StringUtil.indexOfIgnoreCase(name, "sqlserver") != -1
				|| StringUtil.indexOfIgnoreCase(name, "mssql") != -1
				|| StringUtil.indexOfIgnoreCase(name, "microsoftsqlserver") != -1) {
			return SQLSERVER;
		}
		if (StringUtil.indexOfIgnoreCase(name, "db2") != -1) {
			return DB2;
		}
		if (StringUtil.indexOfIgnoreCase(name, "clickhouse") != -1) {
			return CLICKHOUSE;
		}
		// SAP HANA驱动上报产品名为HDB
		if ("HDB".equalsIgnoreCase(name) || StringUtil.indexOfIgnoreCase(name, "HANA") != -1) {
			return HANA;
		}
		if (StringUtil.indexOfIgnoreCase(name, "oceanbase") != -1) {
			return OCEANBASE;
		}
		if (StringUtil.indexOfIgnoreCase(name, "opengauss") != -1) {
			return OPENGAUSS;
		}
		if (StringUtil.indexOfIgnoreCase(name, "gaussdb") != -1 || "zenith".equalsIgnoreCase(name)) {
			return GAUSSDB;
		}
		if (StringUtil.indexOfIgnoreCase(name, "mogdb") != -1) {
			return MOGDB;
		}
		if (StringUtil.indexOfIgnoreCase(name, "stardb") != -1) {
			return STARDB;
		}
		if (StringUtil.indexOfIgnoreCase(name, "sqlite") != -1) {
			return SQLITE;
		}
		if (StringUtil.indexOfIgnoreCase(name, "dm") != -1) {
			return DM;
		}
		if (StringUtil.indexOfIgnoreCase(name, "tidb") != -1) {
			return TIDB;
		}
		if (StringUtil.indexOfIgnoreCase(name, "tdengine") != -1) {
			return TDENGINE;
		}
		if (StringUtil.indexOfIgnoreCase(name, "kingbase") != -1) {
			return KINGBASE;
		}
		if (StringUtil.indexOfIgnoreCase(name, "greenplum") != -1) {
			return POSTGRESQL;
		}
		if (StringUtil.indexOfIgnoreCase(name, "impala") != -1) {
			return IMPALA;
		}
		if (StringUtil.indexOfIgnoreCase(name, "elastic") != -1) {
			return ES;
		}
		if (StringUtil.indexOfIgnoreCase(name, "h2") != -1) {
			return H2;
		}
		if (StringUtil.indexOfIgnoreCase(name, "oscar") != -1) {
			return OSCAR;
		}
		if (StringUtil.indexOfIgnoreCase(name, "vastbase") != -1) {
			return VASTBASE;
		}
		return UNDEFINE;
	}

	/**
	 * 方言名称:与历史getCurrentDBDialect返回值一致(版本差异型如oracle11/mysql57/postgresql15
	 * 折叠为基础方言名,保证类型映射表中第4列方言匹配行为不变)
	 */
	public String dialectName() {
		return switch (this) {
		case ORACLE11 -> "oracle";
		case MYSQL57 -> "mysql";
		case POSTGRESQL14 -> "postgresql";
		default -> name().toLowerCase();
		};
	}

	/** oracle系(12c+与11g),元数据提取走oracle专用SQL */
	public boolean isOracleFamily() {
		return this == ORACLE || this == ORACLE11;
	}

	/** mysql系,元数据提取走information_schema */
	public boolean isMySqlFamily() {
		// update 2026-10-13 补TIDB(真库实测mysql-full.sql全兼容,分区TO_DAYS形态/注释/
		// 默认值与mysql同构,路由MySqlDialect提分区;此前落default的JdbcDialect提不出分区)
		return this == MYSQL || this == MYSQL57 || this == OCEANBASE || this == TIDB;
	}

	/**
	 * postgresql血缘(走pg系统表提取元数据);注意历史上MOGDB不走pg元数据提取
	 * 但类型映射按postgres处理,故此方法不含MOGDB。
	 * HIGHGO(瀚高,PG12内核)真库实测pg系统目录与vanilla PG完全一致,归pg血缘;
	 * KINGBASE(金仓V9,PG内核)真库实测有pg_partitioned_table/pg_get_partkeydef且
	 * 无openGauss系pg_partition(与sqltoy侧金仓归PG系一致,2026-10-13对齐)
	 */
	public boolean isPgFamilyMetadata() {
		return this == POSTGRESQL || this == POSTGRESQL14 || this == GAUSSDB || this == STARDB || this == VASTBASE
				|| this == OPENGAUSS || this == OSCAR || this == HIGHGO || this == KINGBASE;
	}

	/** 类型映射按postgres方言处理(含MOGDB) */
	public boolean isPgFamilyTypeMapping() {
		return isPgFamilyMetadata() || this == MOGDB;
	}

	/** 默认native类型->jdbc类型映射表 */
	public String[][] jdbcTypeAry() {
		if (isPgFamilyTypeMapping()) {
			return PostgresTypeMappings.jdbcAry;
		}
		if (isOracleFamily()) {
			return OracleTypeMappings.jdbcAry;
		}
		return DefaultTypeMappings.jdbcAry;
	}

	/** 默认native类型->java类型映射表 */
	public String[][] jdbcTypeMapping() {
		if (isPgFamilyTypeMapping()) {
			return PostgresTypeMappings.jdbcTypeMapping;
		}
		if (isOracleFamily()) {
			// oracle基于DefaultTypeMappings派生,差异项:DATE含时分秒映射LocalDateTime
			return OracleTypeMappings.jdbcTypeMapping;
		}
		return DefaultTypeMappings.jdbcTypeMapping;
	}
}
