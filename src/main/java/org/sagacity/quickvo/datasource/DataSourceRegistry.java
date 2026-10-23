package org.sagacity.quickvo.datasource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import org.sagacity.quickvo.model.DataSourceModel;
import org.sagacity.quickvo.utils.Msg;

/**
 * 数据源注册表:quickvo.xml中的datasource定义按name登记,任务按名开启会话。
 * 替代原DBHelper中的static dbMaps/conn/dbConfig。
 *
 * @author zhongxuchen
 */
public class DataSourceRegistry {
	private final Map<String, DataSourceModel> dataSources = new LinkedHashMap<>();

	public void register(String name, DataSourceModel config) {
		dataSources.put(name == null || name.trim().isEmpty() ? String.valueOf(dataSources.size()) : name, config);
	}

	public boolean isEmpty() {
		return dataSources.isEmpty();
	}

	public boolean contains(String name) {
		return dataSources.containsKey(name);
	}

	/** 已注册数据源数量(datasource属性缺省匹配仅允许单数据源场景) */
	public int size() {
		return dataSources.size();
	}

	/**
	 * 打开指定数据源会话;name为空且只定义了一个数据源时允许缺省匹配
	 */
	public Optional<DbSession> open(String name, Logger logger) throws Exception {
		String key = (name == null || name.trim().isEmpty()) ? "0" : name;
		DataSourceModel config = dataSources.get(key);
		if (config == null && (name == null || name.trim().isEmpty()) && dataSources.size() == 1) {
			config = dataSources.values().iterator().next();
		}
		if (config == null) {
			logger.info(Msg.get("数据库名称:", "Database name: ") + name
					+ Msg.get("不在dataSource定义的名单中,请检查<task datasource=\"", " not in datasource definitions, check <task datasource=\"") + name
					+ Msg.get("\" 跟<datasource name=\"定义值\"> 的一致性", "\" vs <datasource name=\">"));
			return Optional.empty();
		}
		logger.info(Msg.get("开始连接数据库:","Connecting database:") + name + Msg.get(",url:",", url:") + config.getUrl());
		try {
			Class.forName(config.getDriver());
			Connection conn = DriverManager.getConnection(config.getUrl(), config.getUsername(), config.getPassword());
			try {
				return Optional.of(new DbSession(conn, config));
			} catch (Exception constructFail) {
				// DbSession构造中getMetaData等失败时必须关闭连接,否则句柄泄漏直至GC
				try {
					conn.close();
				} catch (Exception ignore) {
				}
				throw constructFail;
			}
		} catch (ClassNotFoundException cnfe) {
			logger.info(Msg.get("数据库驱动未能加载,请在插件<dependencies>中配置对应数据库驱动依赖!驱动:"," JDBC driver not found, declare it in plugin <dependencies>! Driver: ") + config.getDriver());
			throw cnfe;
		} catch (SQLException se) {
			logger.info(Msg.get("获取数据库连接失败!url:"," Failed to get connection! url: ") + config.getUrl());
			throw se;
		}
	}
}
