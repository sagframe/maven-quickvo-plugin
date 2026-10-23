package org.sagacity.quickvo.dialect;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.sagacity.quickvo.datasource.DbSession;
import org.sagacity.quickvo.datasource.DbType;
import org.sagacity.quickvo.model.TableColumnMeta;
import org.sagacity.quickvo.model.TableMeta;
import org.sagacity.quickvo.utils.Msg;

/**
 * clickhouse方言:列类型/默认值/主键/分区信息来自system.columns。
 *
 * @author zhongxuchen
 */
public class ClickHouseDialect extends JdbcDialect {

	public ClickHouseDialect(DbType dbType) {
		super(dbType);
	}

	/**
	 * 表清单:clickhouse-jdbc的getTables忽略catalog参数(实测对null/具体库名都返回所有库的表),
	 * 故直接查system.tables并按resolveDatabase过滤当前库,避免连接到非default库时把其它库的表也纳入(跨库污染)。
	 * engine含"View"由toTableMeta归一为VIEW。
	 */
	@Override
	public List<TableMeta> listTables(DbSession db) throws SQLException {
		String database = resolveDatabase(db);
		String sql = "SELECT name, engine, comment FROM system.tables WHERE database = ?";
		try (PreparedStatement pst = db.getConnection().prepareStatement(sql)) {
			pst.setString(1, database);
			try (ResultSet rs = pst.executeQuery()) {
				List<TableMeta> tables = new ArrayList<>();
				while (rs.next()) {
					tables.add(toTableMeta(db, rs.getString("name"), rs.getString("engine"),
							org.sagacity.quickvo.utils.StringUtil.clearMistyChars(rs.getString("comment"), " ")));
				}
				return tables;
			}
		}
	}

	@Override
	protected Map<String, TableColumnMeta> columnExtras(DbSession db, String tableName) throws SQLException {
		if (db.isPolardb()) {
			return null;
		}
		String database = resolveDatabase(db);
		String sql = "select name COLUMN_NAME,comment COMMENTS,is_in_primary_key PRIMARY_KEY,"
				+ "is_in_partition_key PARTITION_KEY,type TYPE,default_expression DEFAULT_EXPRESSION,default_kind DEFAULT_KIND"
				+ " from system.columns t where t.table=? and t.database=?";
		try (PreparedStatement pst = db.getConnection().prepareStatement(sql)) {
			pst.setString(1, tableName);
			pst.setString(2, database);
			try (ResultSet rs = pst.executeQuery()) {
				Map<String, TableColumnMeta> extras = new LinkedHashMap<>();
				while (rs.next()) {
					TableColumnMeta colMeta = new TableColumnMeta();
					colMeta.setColName(rs.getString("COLUMN_NAME"));
					colMeta.setColRemark(org.sagacity.quickvo.utils.StringUtil.clearMistyChars(rs.getString("COMMENTS"),
							" "));
					if ("1".equals(rs.getString("PRIMARY_KEY"))) {
						colMeta.setIsPrimaryKey(true);
					}
					if ("1".equals(rs.getString("PARTITION_KEY"))) {
						colMeta.setPartitionKey(true);
					}
					// CK计算列形态:ALIAS=虚拟(查询时计算),MATERIALIZED=物化(写入时计算)
					String defaultKind = rs.getString("DEFAULT_KIND");
					if ("ALIAS".equalsIgnoreCase(defaultKind)) {
						colMeta.setGeneratedType(1);
					} else if ("MATERIALIZED".equalsIgnoreCase(defaultKind)) {
						colMeta.setGeneratedType(2);
					}
					String typeName = rs.getString("TYPE");
					colMeta.setTypeName(typeName);
					colMeta.setColDefault(rs.getString("DEFAULT_EXPRESSION"));
					// DEFAULT_EXPRESSION为空表示"未声明DEFAULT"(ClickHouse的隐式默认不是显式默认),
					// 不能保留空串:模板会渲染成defaultValue="",与"无默认值"语义不符
					if (colMeta.getColDefault() != null && colMeta.getColDefault().isEmpty()) {
						colMeta.setColDefault(null);
					}
					extras.put(colMeta.getColName(), colMeta);
				}
				return extras;
			}
		}
	}

	@Override
	public List<String> getPrimaryKeys(DbSession db, String tableName) throws SQLException {
		String database = resolveDatabase(db);
		String sql = "select t.name COLUMN_NAME from system.columns t where t.table=? and t.database=? and t.is_in_primary_key=1";
		try (PreparedStatement pst = db.getConnection().prepareStatement(sql)) {
			pst.setString(1, tableName);
			pst.setString(2, database);
			try (ResultSet rs = pst.executeQuery()) {
				List<String> result = new ArrayList<>();
				while (rs.next()) {
					result.add(rs.getString("COLUMN_NAME"));
				}
				return result;
			}
		}
	}

	@Override
	public TablePartition getTablePartition(DbSession db, String tableName) throws SQLException {
		String sql = "SELECT engine, partition_key FROM system.tables WHERE database = ? AND name = ?";
		String database = resolveDatabase(db);
		try (PreparedStatement pst = db.getConnection().prepareStatement(sql)) {
			pst.setString(1, database);
			pst.setString(2, tableName);
			try (ResultSet rs = pst.executeQuery()) {
				if (!rs.next()) return null;
				String partitionKey = rs.getString(2);
				if (partitionKey == null || partitionKey.isBlank()) return null;
				return new TablePartition("RANGE", partitionKey.trim());
			}
		} catch (Exception e) {
			return null;
		}
	}

	@Override
	public String getPkConstraint(DbSession db, String tableName) {
		return null;
	}

	/**
	 * MPP表引擎元数据:system.tables的engine/sorting_key(ORDER BY)/primary_key
	 */
	@Override
	public MppTableInfo getMppTableInfo(DbSession db, String tableName) throws SQLException {
		String sql = "SELECT engine, sorting_key, primary_key FROM system.tables WHERE database = ? AND name = ?";
		String database = resolveDatabase(db);
		try (PreparedStatement pst = db.getConnection().prepareStatement(sql)) {
			pst.setString(1, database);
			pst.setString(2, tableName);
			try (ResultSet rs = pst.executeQuery()) {
				if (!rs.next()) {
					return null;
				}
				String engine = rs.getString(1);
				String sortingKey = rs.getString(2);
				// ReplacingMergeTree(ver)等带参数引擎:engine_full中提取参数
				String engineArgs = extractEngineArgs(db, tableName, engine);
				String[] orderBy = sortingKey == null || sortingKey.isBlank() ? new String[0]
						: sortingKey.split("\\s*,\\s*");
				return new MppTableInfo(engine, engineArgs, "", orderBy, new String[0], -1, new String[0]);
			}
		} catch (Exception e) {
			logger.warning(Msg.get("提取表:","Extract table: ") + tableName + Msg.get(" 引擎元数据失败:"," failed to extract engine metadata: ") + e.getMessage());
			return null;
		}
	}

	/** 从engine_full提取引擎参数(如ReplacingMergeTree(ver)的ver) */
	private String extractEngineArgs(DbSession db, String tableName, String engine) {
		try (PreparedStatement pst = db.getConnection().prepareStatement(
				"SELECT engine_full FROM system.tables WHERE database = ? AND name = ?")) {
			String database = resolveDatabase(db);
			pst.setString(1, database);
			pst.setString(2, tableName);
			try (ResultSet rs = pst.executeQuery()) {
				if (rs.next()) {
					String full = rs.getString(1);
					// 形如 "ReplacingMergeTree(ver) ORDER BY ..." 提取括号内参数
					java.util.regex.Matcher matcher = java.util.regex.Pattern
							.compile(Pattern.quote(engine) + "\\s*\\(([^)]*)\\)").matcher(full == null ? "" : full);
					if (matcher.find() && !matcher.group(1).isBlank()) {
						return matcher.group(1).trim();
					}
				}
			}
		} catch (Exception e) {
			// engine_full不可用则忽略参数
		}
		return "";
	}

	/**
	 * 解析clickhouse当前库名:配置的catalog/schema优先;都未配置时从连接推导。
	 * clickhouse-jdbc用getSchema()暴露当前库(getCatalog()返回null),而README建议CK不配catalog/schema,
	 * 故必须回退到连接的getSchema()/getCatalog(),否则会误查硬编码的"default"库,
	 * 导致system.columns零行、整表列被丢(历史缺陷)。最终兜底"default"。
	 */
	private String resolveDatabase(DbSession db) {
		String catalog = db.getCatalog();
		if (catalog != null && !catalog.isBlank()) {
			return catalog;
		}
		String schema = db.getSchema();
		if (schema != null && !schema.isBlank()) {
			return schema;
		}
		try {
			String connSchema = db.getConnection().getSchema();
			if (connSchema != null && !connSchema.isBlank()) {
				return connSchema;
			}
			String connCatalog = db.getConnection().getCatalog();
			if (connCatalog != null && !connCatalog.isBlank()) {
				return connCatalog;
			}
		} catch (SQLException | RuntimeException e) {
			// 连接不支持getSchema/getCatalog时忽略,回退default
		}
		return "default";
	}
}
