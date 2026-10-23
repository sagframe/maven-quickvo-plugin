package org.sagacity.quickvo.metadata;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.sagacity.quickvo.datasource.DbSession;
import org.sagacity.quickvo.datasource.DbType;
import org.sagacity.quickvo.dialect.Dialects;
import org.sagacity.quickvo.dialect.MetadataDialect;
import org.sagacity.quickvo.model.IndexModel;
import org.sagacity.quickvo.model.TableColumnMeta;
import org.sagacity.quickvo.model.TableConstractModel;
import org.sagacity.quickvo.model.TableMeta;
import org.sagacity.quickvo.utils.StringUtil;
import org.sagacity.quickvo.utils.Msg;

/**
 * 数据库元数据读取门面:组合方言完成表/列/主键/外键/索引信息提取,并按任务include/exclude过滤表。
 * 一个任务一个实例,替代原DBHelper的static连接模式。
 *
 * @author zhongxuchen
 */
public class MetadataReader {
	private final Logger logger;
	private final DbSession session;
	private final MetadataDialect dialect;

	public MetadataReader(DbSession session, Logger logger) {
		this.session = session;
		this.logger = logger;
		this.dialect = Dialects.of(session);
	}

	public DbType getDbType() {
		return session.getDbType();
	}

	/** 方言名称(类型映射第4列匹配用) */
	public String getDBDialect() {
		return session.getDbType().dialectName();
	}

	/**
	 * 获取符合include/exclude正则的表和视图
	 */
	public List<TableMeta> getTableAndView(String[] includes, String[] excludes) throws SQLException {
		// 只展示实际配置的schema/catalog,避免null段造成误导
		List<String> scopes = new ArrayList<>(2);
		if (session.getSchema() != null) {
			scopes.add("schema=" + session.getSchema());
		}
		if (session.getCatalog() != null) {
			scopes.add("catalog=" + session.getCatalog());
		}
		logger.info(Msg.get("提取数据库表,范围:{", "Listing tables in ") + String.join(", ", scopes)
				+ Msg.get("},数据库类型:", " (database: ") + session.getDbType() + ")");
		List<TableMeta> tables = new ArrayList<>();
		for (TableMeta tableMeta : dialect.listTables(session)) {
			String tableName = tableMeta.getTableName();
			boolean include = includes == null || includes.length == 0;
			if (!include) {
				for (String pattern : includes) {
					if (StringUtil.matches(tableName, pattern)) {
						include = true;
						break;
					}
				}
			}
			if (include && excludes != null && excludes.length > 0) {
				for (String pattern : excludes) {
					if (StringUtil.matches(tableName, pattern)) {
						include = false;
						break;
					}
				}
			}
			if (include) {
				tables.add(tableMeta);
			}
		}
		return tables;
	}

	/**
	 * 获取表字段信息
	 */
	public List<TableColumnMeta> getTableColumnMeta(String tableName, boolean toUpperCase) throws SQLException {
		return dialect.getColumns(session, tableName, toUpperCase);
	}

	/**
	 * 获取表的外键信息(当前表字段引用其它表主键);行按约束名+列组合去重(部分驱动返回重复行)
	 */
	public List<TableConstractModel> getTableImpForeignKeys(String tableName) {
		try (ResultSet rs = session.meta().getImportedKeys(session.getCatalog(),
				session.getSchema(), tableName)) {
			List<TableConstractModel> result = new ArrayList<>();
			java.util.Set<String> seen = new java.util.HashSet<>();
			while (rs.next()) {
				String key = rs.getString("FK_NAME") + "|" + rs.getString("FKCOLUMN_NAME") + "|"
						+ rs.getString("PKCOLUMN_NAME");
				if (!seen.add(key)) {
					continue;
				}
				TableConstractModel constractModel = new TableConstractModel();
				constractModel.setFkName(rs.getString("FK_NAME"));
				constractModel.setFkRefTableName(rs.getString("PKTABLE_NAME"));
				constractModel.setFkColName(rs.getString("FKCOLUMN_NAME"));
				constractModel.setPkColName(rs.getString("PKCOLUMN_NAME"));
				constractModel.setUpdateRule(rs.getInt("UPDATE_RULE"));
				constractModel.setDeleteRule(rs.getInt("DELETE_RULE"));
				result.add(constractModel);
			}
			return result;
		} catch (Exception e) {
			logger.warning(Msg.get("提取表:","Extract table: ") + tableName + Msg.get(" 外键信息失败:"," failed to extract foreign keys: ") + e.getMessage());
			return new ArrayList<>();
		}
	}

	/**
	 * 获取表主键被其他表关联的信息(作为其它表的外键);行按约束名+列组合去重
	 */
	public List<TableConstractModel> getTableExportKeys(String tableName) {
		try (ResultSet rs = session.meta().getExportedKeys(session.getCatalog(),
				session.getSchema(), tableName)) {
			List<TableConstractModel> result = new ArrayList<>();
			java.util.Set<String> seen = new java.util.HashSet<>();
			while (rs.next()) {
				String key = rs.getString("FK_NAME") + "|" + rs.getString("FKCOLUMN_NAME") + "|"
						+ rs.getString("PKCOLUMN_NAME");
				if (!seen.add(key)) {
					continue;
				}
				TableConstractModel constractModel = new TableConstractModel();
				constractModel.setPkRefTableName(rs.getString("FKTABLE_NAME"));
				constractModel.setPkColName(rs.getString("PKCOLUMN_NAME"));
				constractModel.setPkRefColName(rs.getString("FKCOLUMN_NAME"));
				constractModel.setUpdateRule(rs.getInt("UPDATE_RULE"));
				constractModel.setDeleteRule(rs.getInt("DELETE_RULE"));
				result.add(constractModel);
			}
			return result;
		} catch (Exception e) {
			logger.warning(Msg.get("提取表:","Extract table: ") + tableName + Msg.get(" 被外键关联信息失败:"," failed to extract exported keys: ") + e.getMessage());
			return new ArrayList<>();
		}
	}

	/**
	 * 获取表的主键列
	 */
	public List<String> getTablePrimaryKeys(String tableName) {
		try {
			return dialect.getPrimaryKeys(session, tableName);
		} catch (Exception e) {
			logger.warning(Msg.get("提取表:","Extract table: ") + tableName + Msg.get(" 主键信息失败:"," failed to extract primary keys: ") + e.getMessage());
			return new ArrayList<>();
		}
	}

	/**
	 * 获取表的主键约束名称
	 */
	public String getTablePKConstraint(String tableName) {
		try {
			return dialect.getPkConstraint(session, tableName);
		} catch (Exception e) {
			logger.warning(Msg.get("提取表:","Extract table: ") + tableName + Msg.get(" 主键约束名称失败:"," failed to extract primary key constraint name: ") + e.getMessage());
			return null;
		}
	}

	/**
	 * 获取表注释(部分数据库JDBC不返回注释时的补充提取)
	 */
	public String getTableRemark(String tableName) {
		try {
			return dialect.getTableRemark(session, tableName);
		} catch (Exception e) {
			logger.warning(Msg.get("提取表:","Extract table: ") + tableName + Msg.get(" 注释失败:"," failed to extract comments: ") + e.getMessage());
			return null;
		}
	}

	/**
	 * 获取表分区策略(非分区表返回null)
	 */
	public org.sagacity.quickvo.dialect.TablePartition getTablePartition(String tableName) {
		try {
			return dialect.getTablePartition(session, tableName);
		} catch (Exception e) {
			logger.warning(Msg.get("提取表:","Extract table: ") + tableName + Msg.get(" 分区策略失败:"," failed to extract partition strategy: ") + e.getMessage());
			return null;
		}
	}

	/**
	 * 获取MPP表引擎元数据(ClickHouse/Doris/StarRocks;非MPP返回null)
	 */
	public org.sagacity.quickvo.dialect.MppTableInfo getMppTableInfo(String tableName) {
		try {
			return dialect.getMppTableInfo(session, tableName);
		} catch (Exception e) {
			logger.warning(Msg.get("提取表:","Extract table: ") + tableName + Msg.get(" MPP引擎元数据失败:"," failed to extract MPP engine metadata: ") + e.getMessage());
			return null;
		}
	}

	/**
	 * 获取表的索引信息(排除主键索引)
	 */
	public List<IndexModel> getIndexInfo(String tableName, String pkName) {
		try {
			return dialect.getIndexInfo(session, tableName, pkName);
		} catch (Exception e) {
			logger.warning(Msg.get("提取表:","Extract table: ") + tableName + Msg.get(" 索引信息失败:"," failed to extract index info: ") + e.getMessage());
			return new ArrayList<>();
		}
	}

	/**
	 * 列出数据库可用的schema名称(用于配置纠错提示)
	 */
	public List<String> listSchemas() {
		List<String> schemas = new ArrayList<>();
		try (ResultSet rs = session.meta().getSchemas()) {
			while (rs.next() && schemas.size() < 50) {
				schemas.add(rs.getString(1));
			}
		} catch (Exception e) {
			// 部分驱动不支持,忽略
		}
		return schemas;
	}

	/**
	 * 列出数据库可用的catalog名称(用于配置纠错提示)
	 */
	public List<String> listCatalogs() {
		List<String> catalogs = new ArrayList<>();
		try (ResultSet rs = session.meta().getCatalogs()) {
			while (rs.next() && catalogs.size() < 50) {
				catalogs.add(rs.getString(1));
			}
		} catch (Exception e) {
			// 部分驱动不支持,忽略
		}
		return catalogs;
	}

	/** 当前连接的数据库名称 */
	public String currentCatalog() {
		try {
			return session.getConnection().getCatalog();
		} catch (Exception e) {
			return null;
		}
	}
}
