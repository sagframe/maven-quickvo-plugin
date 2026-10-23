package org.sagacity.quickvo.dialect;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.sagacity.quickvo.datasource.DbSession;
import org.sagacity.quickvo.datasource.DbType;
import org.sagacity.quickvo.model.TableColumnMeta;
import org.sagacity.quickvo.model.TableMeta;
import org.sagacity.quickvo.utils.StringUtil;
import org.sagacity.quickvo.utils.Msg;

/**
 * mysql/mariadb方言:表列表与列扩展信息走information_schema。
 * 修复:原列信息查询的GROUP BY在mysql5.7+默认only_full_group_by模式下必然报错,
 * 导致分区键/生成列检测被静默降级,此处将全部非聚合列补入GROUP BY。
 *
 * @author zhongxuchen
 */
public class MySqlDialect extends JdbcDialect {

	public MySqlDialect(DbType dbType) {
		super(dbType);
	}

	@Override
	public List<TableMeta> listTables(DbSession db) throws SQLException {
		if (db.isPolardb()) {
			return super.listTables(db);
		}
		String schema = db.getSchema();
		String catalog = db.getCatalog();
		String dbFilter = schema != null ? schema : catalog;
		StringBuilder sql = new StringBuilder(
				"SELECT TABLE_NAME,TABLE_SCHEMA,TABLE_TYPE,TABLE_COMMENT FROM INFORMATION_SCHEMA.TABLES where 1=1 ");
		if (dbFilter != null) {
			sql.append(" and TABLE_SCHEMA=? ");
		}
		sql.append(" and ( TABLE_TYPE like '%TABLE' or TABLE_TYPE like '%VIEW')");
		try (PreparedStatement pst = db.getConnection().prepareStatement(sql.toString())) {
			if (dbFilter != null) {
				pst.setString(1, dbFilter);
			}
			try (ResultSet rs = pst.executeQuery()) {
				List<TableMeta> tables = new ArrayList<>();
				while (rs.next()) {
					tables.add(toTableMeta(db, rs.getString("TABLE_NAME"), rs.getString("TABLE_TYPE"),
							StringUtil.clearMistyChars(rs.getString("TABLE_COMMENT"), " ")));
				}
				return tables;
			}
		}
	}

	@Override
	protected Map<String, TableColumnMeta> columnExtras(DbSession db, String tableName) throws SQLException {
		String dbFilter = db.getSchema() != null ? db.getSchema()
				: db.getCatalog();
		String sql = """
				SELECT
				    COLUMN_NAME,
				    COLUMN_DEFAULT AS DATA_DEFAULT,
				    COLUMN_COMMENT AS COMMENTS,
				    CASE
				        WHEN PARTITION_EXPRESSION IS NOT NULL AND CONCAT(' ', PARTITION_EXPRESSION, ' ') REGEXP CONCAT('[^0-9a-zA-Z_]', COLUMN_NAME, '[^0-9a-zA-Z_]')
				        THEN 1
				        ELSE 0
				    END AS IS_PARTITION_KEY,
				    CASE
				        WHEN EXTRA LIKE '%GENERATED%' THEN
				            CASE
				                WHEN EXTRA LIKE '%VIRTUAL%' THEN 1
				                WHEN EXTRA LIKE '%STORED%' THEN 2
				                ELSE 0
				            END
				        ELSE 0
				    END AS GENERATED_TYPE
				FROM information_schema.COLUMNS c
				LEFT JOIN information_schema.PARTITIONS p
				    ON c.TABLE_SCHEMA = p.TABLE_SCHEMA
				   AND c.TABLE_NAME = p.TABLE_NAME
				WHERE c.TABLE_NAME = ?""";
		if (dbFilter != null) {
			sql = sql.concat(" AND c.TABLE_SCHEMA=? ");
		}
		// 全部非聚合列纳入GROUP BY,兼容only_full_group_by模式
		sql = sql.concat("""
				GROUP BY c.TABLE_SCHEMA, c.TABLE_NAME, c.COLUMN_NAME, c.ORDINAL_POSITION,
				         c.COLUMN_DEFAULT, c.COLUMN_COMMENT, p.PARTITION_EXPRESSION, c.EXTRA
				ORDER BY c.ORDINAL_POSITION""");
		try (PreparedStatement pst = db.getConnection().prepareStatement(sql)) {
			pst.setString(1, tableName);
			if (dbFilter != null) {
				pst.setString(2, dbFilter);
			}
			return extrasFromQuery(sql, pst);
		}
	}

	/**
	 * 分区策略:information_schema.PARTITIONS的PARTITION_METHOD+PARTITION_EXPRESSION
	 */
	/**
	 * 分区明细:information_schema.PARTITIONS的PARTITION_NAME/PARTITION_DESCRIPTION/TABLESPACE_NAME;
	 * StarRocks该视图不填充,改走SHOW PARTITIONS取上边界。无明细返回空表(hash分区/自动分区场景)
	 */
	private java.util.List<org.sagacity.quickvo.model.PartitionDef> loadPartitionDefs(DbSession db, String tableName) {
		java.util.List<org.sagacity.quickvo.model.PartitionDef> defs = new java.util.ArrayList<>();
		String dbFilter = db.getSchema() != null ? db.getSchema() : db.getCatalog();
		String sql = "SELECT PARTITION_NAME, PARTITION_DESCRIPTION, TABLESPACE_NAME FROM information_schema.PARTITIONS"
				+ " WHERE TABLE_NAME = ? AND PARTITION_NAME IS NOT NULL";
		if (dbFilter != null) {
			sql = sql.concat(" AND TABLE_SCHEMA = ?");
		}
		sql = sql.concat(" ORDER BY PARTITION_ORDINAL_POSITION");
		try (PreparedStatement pst = db.getConnection().prepareStatement(sql)) {
			pst.setString(1, tableName);
			if (dbFilter != null) {
				pst.setString(2, dbFilter);
			}
			try (ResultSet rs = pst.executeQuery()) {
				while (rs.next()) {
					String desc = StringUtil.trim(rs.getString(2));
					// Doris的PARTITION_DESCRIPTION为区间形式:[('下界'), ('上界')),
					// VALUES LESS THAN渲染取上界(右括号前最后一对引号内的值)
					if (desc != null && desc.startsWith("[")) {
						int close = desc.lastIndexOf(')');
						int q2 = desc.lastIndexOf('\'', close);
						int q1 = desc.lastIndexOf('\'', q2 - 1);
						if (q1 >= 0 && q2 > q1 + 1) {
							desc = desc.substring(q1 + 1, q2);
						}
					}
					defs.add(new org.sagacity.quickvo.model.PartitionDef(rs.getString(1), desc,
							StringUtil.trim(rs.getString(3))));
				}
			}
			// StarRocks的PARTITIONS视图存在但不填充(返回0行不抛异常),空结果时也走SHOW PARTITIONS兜底
			if (!defs.isEmpty()) {
				return defs;
			}
		} catch (Exception e) {
			// 视图查询异常时也走SHOW PARTITIONS兜底
		}
		// SHOW PARTITIONS兜底(StarRocks的PARTITIONS视图不填充)
		try (Statement st = db.getConnection().createStatement();
				ResultSet rs = st.executeQuery("SHOW PARTITIONS FROM `" + tableName + "`")) {
			while (rs.next()) {
				String range = rs.getString("Range");
				String upper = null;
				if (range != null) {
					// SR Range列形态:[types: [DATE]; keys: [下界]; ..types: [DATE]; keys: [上界]; )
					// 取最后一个keys: [值]作为VALUES LESS THAN上界
					java.util.regex.Matcher km = java.util.regex.Pattern
							.compile("keys:\\s*\\[([^\\]]+)\\]").matcher(range);
					while (km.find()) {
						upper = km.group(1).trim();
					}
				}
				defs.add(new org.sagacity.quickvo.model.PartitionDef(rs.getString("PartitionName"), upper, null));
			}
		} catch (Exception ignore) {
			// 无明细场景由调用方按空表处理
		}
		return defs;
	}

	/** SHOW CREATE TABLE分区子句兜底:PARTITION BY RANGE(TRADE_DATE)形式 */
	private TablePartition partitionFromShowCreate(DbSession db, String tableName) {
		try (Statement st = db.getConnection().createStatement();
				ResultSet rs = st.executeQuery("SHOW CREATE TABLE `" + tableName + "`")) {
			if (rs.next()) {
				String createSql = rs.getString(2);
				if (createSql != null) {
					java.util.regex.Matcher m = java.util.regex.Pattern
							.compile("(?i)PARTITION\\s+BY\\s+(RANGE\\s+COLUMNS|LIST\\s+COLUMNS|RANGE|LIST|HASH|KEY)\\s*\\((.*)\\)")
							.matcher(createSql);
					if (m.find()) {
						String strategy = m.group(1).replaceAll("\\s+", "_").toUpperCase();
						if ("KEY".equals(strategy) || strategy.startsWith("LINEAR")) {
							strategy = "HASH";
						}
						return new TablePartition(strategy, m.group(2), loadPartitionDefs(db, tableName));
					}
				}
			}
		} catch (Exception e) {
			logger.fine(Msg.get("提取表:", "Extract table: ") + tableName
					+ Msg.get(" SHOW CREATE分区兜底失败:", " SHOW CREATE partition fallback failed: ") + e.getMessage());
		}
		return null;
	}

	@Override
	public TablePartition getTablePartition(DbSession db, String tableName) throws SQLException {
		String dbFilter = db.getSchema() != null ? db.getSchema()
				: db.getCatalog();
		String sql = "SELECT PARTITION_METHOD, PARTITION_EXPRESSION FROM information_schema.PARTITIONS"
				+ " WHERE TABLE_NAME = ?";
		if (dbFilter != null) {
			sql = sql.concat(" AND TABLE_SCHEMA = ?");
		}
		sql = sql.concat(" LIMIT 1");
		try (PreparedStatement pst = db.getConnection().prepareStatement(sql)) {
			pst.setString(1, tableName);
			if (dbFilter != null) {
				pst.setString(2, dbFilter);
			}
			try (ResultSet rs = pst.executeQuery()) {
				if (!rs.next()) {
					// StarRocks等PARTITIONS视图不填充,退化为SHOW CREATE TABLE提取分区子句
					return partitionFromShowCreate(db, tableName);
				}
				String method = rs.getString(1);
				// doris/starrocks非分区表在PARTITIONS中有一行UNPARTITIONED记录,视为无分区
				if (method == null || "UNPARTITIONED".equalsIgnoreCase(method.trim())) {
					return null;
				}
				method = method.toUpperCase();
				// 归一mysql分区方法名
				String strategy = switch (method) {
				case "RANGE COLUMNS" -> "RANGE_COLUMNS";
				case "LIST COLUMNS" -> "LIST_COLUMNS";
				case "LINEAR HASH", "LINEAR KEY", "KEY" -> "HASH";
				default -> method;
				};
				return new TablePartition(strategy, rs.getString(2), loadPartitionDefs(db, tableName));
			}
		} catch (Exception e) {
			logger.warning(Msg.get("提取表:","Extract table: ") + tableName + Msg.get(" 分区策略失败:"," failed to extract partition strategy: ") + e.getMessage());
			return null;
		}
	}

	/**
	 * MPP表引擎元数据:Doris/StarRocks走SHOW CREATE TABLE解析
	 * (ENGINE=OLAP/{UNIQUE|PRIMARY|DUPLICATE|AGGREGATE} KEY(...)/DISTRIBUTED BY HASH(...)[ BUCKETS n]);
	 * 普通mysql的SHOW CREATE TABLE无ENGINE=OLAP返回null
	 */
	@Override
	public MppTableInfo getMppTableInfo(DbSession db, String tableName) throws SQLException {
		String createSql;
		try (java.sql.Statement st = db.getConnection().createStatement();
				ResultSet rs = st.executeQuery("SHOW CREATE TABLE `" + tableName + "`")) {
			if (!rs.next()) {
				return null;
			}
			createSql = rs.getString(2) == null ? rs.getString(1) : rs.getString(2);
		} catch (Exception e) {
			return null;
		}
		if (createSql == null || !createSql.toUpperCase().contains("ENGINE=OLAP")
				&& !createSql.toUpperCase().contains("ENGINE = OLAP")) {
			// 普通mysql表
			return null;
		}
		// 在大写串上做词定位,但分组取原文(保持列名原始大小写,与@Column(name=...)一致)
		String upper = createSql.toUpperCase();
		String engine = "OLAP";
		String keyModel = "";
		String[] orderBy = new String[0];
		String[] distributedBy = new String[0];
		int buckets = -1;
		java.util.regex.Matcher keyMatcher = java.util.regex.Pattern
				.compile("(UNIQUE|PRIMARY|DUPLICATE|AGGREGATE)\\s+KEY\\s*\\(([^)]+)\\)").matcher(upper);
		if (keyMatcher.find()) {
			keyModel = keyMatcher.group(1);
			orderBy = splitColumns(createSql.substring(keyMatcher.start(2), keyMatcher.end(2)));
		}
		// DISTRIBUTED BY HASH(col1,col2) [BUCKETS n]
		java.util.regex.Matcher distMatcher = java.util.regex.Pattern
				.compile("DISTRIBUTED\\s+BY\\s+HASH\\s*\\(([^)]+)\\)(?:\\s+BUCKETS\\s+(\\d+))?").matcher(upper);
		if (distMatcher.find()) {
			distributedBy = splitColumns(createSql.substring(distMatcher.start(1), distMatcher.end(1)));
			if (distMatcher.group(2) != null) {
				buckets = Integer.parseInt(distMatcher.group(2));
			}
		}
		return new MppTableInfo(engine, "", keyModel, orderBy, distributedBy, buckets, extractProperties(createSql));
	}

	/**
	 * 提取SHOW CREATE TABLE的PROPERTIES子句为"k=v"数组(sqltoy端按=拆分渲染PROPERTIES("k"="v"));
	 * 注意Doris/StarRocks会回显大量环境默认属性(如replication_allocation/storage_format),
	 * 属于部署相关配置,跨环境全量还原可能不兼容,仅保留表定义语义相关的属性
	 */
	private String[] extractProperties(String createSql) {
		int idx = createSql.toUpperCase().lastIndexOf("PROPERTIES");
		if (idx < 0) {
			return new String[0];
		}
		int open = createSql.indexOf('(', idx);
		int close = createSql.lastIndexOf(')');
		if (open < 0 || close <= open) {
			return new String[0];
		}
		String body = createSql.substring(open + 1, close);
		java.util.List<String> props = new java.util.ArrayList<>();
		// 逐对解析"key"="value",键值内不含引号(Doris属性键值均为合法标识符/字面量)
		java.util.regex.Matcher m = java.util.regex.Pattern
				.compile("\"([^\"]+)\"\\s*=\\s*\"([^\"]*)\"").matcher(body);
		while (m.find()) {
			String key = m.group(1);
			// 过滤环境默认属性:只保留用户在DDL中显式设置且具跨环境意义的表级属性
			// 黑名单为Doris/StarRocks的SHOW CREATE回显集:含部署相关(replication/storage)与
			// 运行时状态(is_being_synced/light_schema_change/group_commit等),非用户表定义语义
			if (key.startsWith("replication_") || key.startsWith("min_load_rep") || key.startsWith("group_commit")
					|| key.startsWith("enable_unique_key_merge_on_write") || key.startsWith("dynamic_partition.enable")
					|| key.equals("in_memory") || key.equals("storage_format") || key.equals("storage_medium")
					|| key.equals("storage_type") || key.equals("function_column") || key.equals("compression")
					|| key.equals("write_quorum") || key.equals("schema_change_sync") || key.equals("is_being_synced")
					|| key.equals("light_schema_change") || key.equals("disable_auto_compaction")
					|| key.equals("inverted_index_storage_format") || key.equals("enable_persistent_index")
					|| key.equals("create_table_timeout") || key.equals("storage_page_size")
					|| key.equals("disable_hotkey_warning") || key.equals("row_store_page_size")) {
				continue;
			}
			props.add(key + "=" + m.group(2));
		}
		return props.toArray(new String[0]);
	}

	/** 反引号列组拆分 */
	private String[] splitColumns(String columnGroup) {
		String[] columns = columnGroup.split("\\s*,\\s*");
		List<String> result = new ArrayList<>();
		for (String column : columns) {
			String trimmed = column.trim().replace("`", "").replace("\"", "");
			if (!trimmed.isEmpty()) {
				result.add(trimmed);
			}
		}
		return result.toArray(new String[0]);
	}

	@Override
	public List<String> getPrimaryKeys(DbSession db, String tableName) throws SQLException {
		try {
			List<String> pks = super.getPrimaryKeys(db, tableName);
			// Doris/StarRocks等兼容mysql协议库的getPrimaryKeys返回空集而非抛异常,
			// 空结果时也走desc兜底(DESC的Key列:mysql为PRI,doris/starrocks为true/false)
			if (pks != null && !pks.isEmpty()) {
				return pks;
			}
		} catch (Exception e) {
			logger.info(Msg.get("JDBC方式提取主键失败,尝试desc方式:", "JDBC primary key extraction failed, trying DESC: ")
					+ e.getMessage());
		}
		// 针对StarRocks等不支持getPrimaryKeys的mysql系数据库兜底
		if (db.isPolardb()) {
			return new ArrayList<>();
		}
		try (Statement st = db.getConnection().createStatement();
				ResultSet rs = st.executeQuery("desc `" + tableName + "`")) {
			List<String> result = new ArrayList<>();
			while (rs.next()) {
				// DESC的KEY列是字符串:空/PRI/MUL/UNI,只有PRI是主键(历史getBoolean从未生效)
				String keyFlag = rs.getString("KEY");
				// mysql系KEY列为PRI; doris系DESC的Key列为true/false
				if ("PRI".equalsIgnoreCase(keyFlag) || "true".equalsIgnoreCase(keyFlag)) {
					result.add(rs.getString("FIELD"));
				}
			}
			return result;
		} catch (Exception e) {
			logger.warning(Msg.get("提取表:","Extract table: ") + tableName + Msg.get(" 主键信息失败:"," failed to extract primary keys: ") + e.getMessage());
			return new ArrayList<>();
		}
	}
}
