package org.sagacity.quickvo.datasource;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import org.sagacity.quickvo.model.DataSourceModel;

/**
 * 单个任务的数据库会话:连接+数据源配置+识别出的数据库类型,随任务创建和关闭。
 *
 * @author zhongxuchen
 */
public class DbSession implements AutoCloseable {
	private final Connection connection;
	private final DataSourceModel config;
	private final DbType dbType;
	/** 按数据库类型容错归一后的catalog(供元数据提取使用) */
	private final String catalog;
	/** 按数据库类型容错归一后的schema(供元数据提取使用) */
	private final String schema;

	public DbSession(Connection connection, DataSourceModel config) throws SQLException {
		this.connection = connection;
		this.config = config;
		// URL schema特征优先于产品名(衍生库产品名普遍误报母产品)
		this.dbType = DbType.resolve(config.getUrl(), connection.getMetaData().getDatabaseProductName(),
				majorVersion(connection));
		String[] normalized = SchemaCatalogNormalizer.normalize(dbType, config.getCatalog(), config.getSchema(),
				listAvailableSchemas(connection));
		this.catalog = normalized[0];
		this.schema = normalized[1];
	}

	/**
	 * 归一化后的catalog(方言语义:仅mysql系/sqlserver/db2有值,其它类型归一为null)
	 */
	public String getCatalog() {
		return catalog;
	}

	/**
	 * 归一化后的schema(方言语义:oracle/dm系大写,pg系按库内实际大小写,mysql系归一为null)
	 */
	public String getSchema() {
		return schema;
	}

	/** 数据库实际可用的schema清单(探测大小写纠错用,最多50个) */
	private static java.util.List<String> listAvailableSchemas(Connection conn) {
		java.util.List<String> schemas = new java.util.ArrayList<>();
		try (java.sql.ResultSet rs = conn.getMetaData().getSchemas()) {
			while (rs.next() && schemas.size() < 50) {
				schemas.add(rs.getString(1));
			}
		} catch (Exception e) {
			// 部分驱动不支持,忽略(保持原样配置)
		}
		return schemas;
	}

	private static int majorVersion(Connection conn) {
		try {
			return conn.getMetaData().getDatabaseMajorVersion();
		} catch (Exception e) {
			return -1;
		}
	}

	public Connection getConnection() {
		return connection;
	}

	public DatabaseMetaData meta() throws SQLException {
		return connection.getMetaData();
	}

	public DataSourceModel getConfig() {
		return config;
	}

	public DbType getDbType() {
		return dbType;
	}

	/** 连接url是否为polardb(polardb兼容mysql/oracle协议但缺专用系统表,需回退通用JDBC元数据) */
	public boolean isPolardb() {
		return config.getUrl() != null && config.getUrl().toLowerCase(java.util.Locale.ROOT).contains("polardb");
	}

	@Override
	public void close() {
		try {
			if (connection != null && !connection.isClosed()) {
				connection.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
