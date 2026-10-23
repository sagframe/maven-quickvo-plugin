package org.sagacity.quickvo.dialect;

import org.sagacity.quickvo.datasource.DbSession;
import org.sagacity.quickvo.datasource.DbType;

/**
 * 方言工厂:按会话识别的数据库类型装配元数据方言。
 * polardb兼容mysql/oracle协议但缺专用系统表,统一回退通用JDBC方言(与历史行为一致)。
 *
 * @author zhongxuchen
 */
public final class Dialects {

	private Dialects() {
	}

	public static MetadataDialect of(DbSession session) {
		DbType dbType = session.getDbType();
		// polardb对oracle/mysql的专用系统表不可用,直接走通用JDBC提取
		if (session.isPolardb() && (dbType.isOracleFamily() || dbType.isMySqlFamily() || dbType == DbType.SQLSERVER
				|| dbType == DbType.CLICKHOUSE)) {
			return new JdbcDialect(dbType);
		}
		return switch (dbType) {
		case MYSQL, MYSQL57, OCEANBASE, TIDB -> new MySqlDialect(dbType);
		case POSTGRESQL, POSTGRESQL14, GAUSSDB, STARDB, VASTBASE, OPENGAUSS, OSCAR, HIGHGO, KINGBASE ->
			new PostgresDialect(dbType);
		case ORACLE, ORACLE11 -> new OracleDialect(dbType);
		case SQLSERVER -> new SqlServerDialect(dbType);
		case CLICKHOUSE -> new ClickHouseDialect(dbType);
		case DM -> new DmDialect(dbType);
		case HANA -> new HanaDialect(dbType);
		case IMPALA -> new ImpalaDialect(dbType);
		case DB2 -> new Db2Dialect(dbType);
		default -> new JdbcDialect(dbType);
		};
	}
}
