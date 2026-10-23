package org.sagacity.quickvo.dialect;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
 * postgresql血缘方言(postgresql/gaussdb/openGauss/mogdb之外的pg系、vastbase、stardb、oscar):
 * 表列表走pg_class,列扩展信息走pg_attribute+pg_description+pg_attrdef+pg_partitioned_table。
 *
 * @author zhongxuchen
 */
public class PostgresDialect extends JdbcDialect {

	/** PARTITION BY 头部解析:从pg_get_tabledef提取"RANGE (col1, col2)"形态的策略+键列表达式 */
	private static final java.util.regex.Pattern PARTITION_BY_PATTERN = java.util.regex.Pattern
			.compile("(?is)PARTITION\\s+BY\\s+(\\w+\\s*\\([^)]*\\))");

	/** openGauss系(og/opengauss5/vastbase)无pg_partitioned_table目录,分区元数据在pg_partition(pg_get_partkeydef也不存在);
	 * 方言实例级缓存探测结果(一个会话一个方言实例,免每表重复探测) */
	private Boolean vanillaPartitionCatalog;

	public PostgresDialect(DbType dbType) {
		super(dbType);
	}

	/** pg_partitioned_table目录是否可用(vanilla PG系有;openGauss系没有,分区走pg_partition) */
	private boolean hasVanillaPartitionCatalog(DbSession db) {
		if (vanillaPartitionCatalog == null) {
			try (PreparedStatement pst = db.getConnection()
					.prepareStatement("select 1 from pg_catalog.pg_partitioned_table limit 1")) {
				pst.executeQuery();
				vanillaPartitionCatalog = true;
			} catch (Exception e) {
				vanillaPartitionCatalog = false;
			}
		}
		return vanillaPartitionCatalog;
	}

	@Override
	public List<TableMeta> listTables(DbSession db) throws SQLException {
		String schema = db.getSchema();
		String catalog = db.getCatalog();
		if (StringUtil.isBlank(schema)) {
			schema = catalog;
		}
		String sql = """
				SELECT
				  c.relname AS TABLE_NAME,
				  CASE c.relkind
				    WHEN 'r' THEN 'TABLE'
				    WHEN 'p' THEN 'TABLE'
				    WHEN 'v' THEN 'VIEW'
				  END AS TABLE_TYPE,
				  d.description AS COMMENTS
				FROM pg_class c
					LEFT JOIN pg_description d ON d.objoid = c.oid AND d.objsubid = 0
					JOIN pg_namespace n ON n.oid = c.relnamespace
				WHERE
				  c.relkind IN ('r', 'v', 'p')
				  AND c.oid NOT IN (SELECT inhrelid FROM pg_inherits)""";
		if (StringUtil.isNotBlank(schema)) {
			sql = sql.concat(" AND n.nspname=? ");
		} else {
			sql = sql.concat(" AND c.relname NOT LIKE 'pg_%' AND n.nspname NOT LIKE 'pg_%' ");
		}
		try (PreparedStatement pst = db.getConnection().prepareStatement(sql)) {
			if (StringUtil.isNotBlank(schema)) {
				pst.setString(1, schema);
			}
			try (ResultSet rs = pst.executeQuery()) {
				List<TableMeta> tables = new ArrayList<>();
				while (rs.next()) {
					tables.add(toTableMeta(db, rs.getString("TABLE_NAME"), rs.getString("TABLE_TYPE"),
							StringUtil.clearMistyChars(rs.getString("COMMENTS"), " ")));
				}
				return tables;
			}
		}
	}

	/**
	 * 分区策略:vanilla PG走pg_partitioned_table.partstrat + pg_get_partkeydef表达式;
	 * openGauss系(og/opengauss5/vastbase,无上述目录与函数)走pg_partition.partstrategy +
	 * pg_get_tabledef解析"PARTITION BY RANGE (col)"头部。
	 * 按schema(nspname)过滤,与listTables/columnExtras一致,避免不同schema下同名分区表取到错误的那个
	 */
	@Override
	public TablePartition getTablePartition(DbSession db, String tableName) throws SQLException {
		String schema = db.getSchema();
		String catalog = db.getCatalog();
		if (StringUtil.isBlank(schema)) {
			schema = catalog;
		}
		if (hasVanillaPartitionCatalog(db)) {
			return vanillaPartition(db, tableName, schema);
		}
		return openGaussPartition(db, tableName, schema);
	}

	/** vanilla PG:pg_partitioned_table.partstrat(r/l/h) + pg_get_partkeydef表达式 */
	private TablePartition vanillaPartition(DbSession db, String tableName, String schema) throws SQLException {
		String sql = """
				SELECT p.partstrat, pg_get_partkeydef(c.oid) AS partkey_def
				FROM pg_class c
				JOIN pg_partitioned_table p ON p.partrelid = c.oid
				JOIN pg_namespace n ON n.oid = c.relnamespace
				WHERE c.relname = ?""";
		if (StringUtil.isNotBlank(schema)) {
			sql = sql.concat(" AND n.nspname=? ");
		}
		try (PreparedStatement pst = db.getConnection().prepareStatement(sql)) {
			pst.setString(1, tableName);
			if (StringUtil.isNotBlank(schema)) {
				pst.setString(2, schema);
			}
			try (ResultSet rs = pst.executeQuery()) {
				if (!rs.next()) {
					return null;
				}
				String strategy = switch (rs.getString(1)) {
				case "r" -> "RANGE";
				case "l" -> "LIST";
				case "h" -> "HASH";
				default -> rs.getString(1).toUpperCase();
				};
				return new TablePartition(strategy, rs.getString(2));
			}
		} catch (Exception e) {
			logger.warning(Msg.get("提取表:","Extract table: ") + tableName + Msg.get(" 分区策略失败:"," failed to extract partition strategy: ") + e.getMessage());
			return null;
		}
	}

	/**
	 * openGauss系:pg_partition取分区元数据(真库实测parttype='r'为分区表本身、'p'为各分区明细行,
	 * 明细行携带partstrategy与boundaries上界),pg_get_tabledef的DDL里解析"PARTITION BY RANGE (col)"
	 * 作为表达式(与vanilla的pg_get_partkeydef输出同形态);i为interval分区,本质range扩展归RANGE。
	 * 分区明细必须提取:openGauss建表强制要求分区清单(裸PARTITION BY无清单报syntax error,
	 * 与vanilla PG父表可独立创建不同),sqltoy按明细渲染"PARTITION p1 VALUES LESS THAN (v)"恰为
	 * openGauss语法,端到端闭环
	 */
	private TablePartition openGaussPartition(DbSession db, String tableName, String schema) throws SQLException {
		String sql = """
				SELECT p.partstrategy, pg_get_tabledef(c.oid) AS table_def,
				       p.relname AS part_name, array_to_string(p.boundaries, ',') AS bounds
				FROM pg_partition p
				JOIN pg_class c ON c.oid = p.parentid
				JOIN pg_namespace n ON n.oid = c.relnamespace
				WHERE p.parttype = 'p' AND c.relname = ?""";
		if (StringUtil.isNotBlank(schema)) {
			sql = sql.concat(" AND n.nspname=? ");
		}
		try (PreparedStatement pst = db.getConnection().prepareStatement(sql)) {
			pst.setString(1, tableName);
			if (StringUtil.isNotBlank(schema)) {
				pst.setString(2, schema);
			}
			try (ResultSet rs = pst.executeQuery()) {
				String strategy = null;
				String expression = null;
				List<org.sagacity.quickvo.model.PartitionDef> defs = new ArrayList<>();
				while (rs.next()) {
					if (strategy == null) {
						strategy = switch (rs.getString(1)) {
						case "r", "i" -> "RANGE";
						case "l" -> "LIST";
						case "h" -> "HASH";
						default -> rs.getString(1).toUpperCase();
						};
						if (rs.getString(2) != null) {
							java.util.regex.Matcher m = PARTITION_BY_PATTERN.matcher(rs.getString(2));
							if (m.find()) {
								expression = m.group(1).replaceAll("\\s+", " ").trim();
							}
						}
					}
					defs.add(new org.sagacity.quickvo.model.PartitionDef(rs.getString(3),
							formatBoundaries(rs.getString(4)), null));
				}
				if (strategy == null) {
					return null;
				}
				return new TablePartition(strategy, expression, defs);
			}
		} catch (Exception e) {
			logger.warning(Msg.get("提取表:","Extract table: ") + tableName + Msg.get(" 分区策略失败:"," failed to extract partition strategy: ") + e.getMessage());
			return null;
		}
	}

	/**
	 * openGauss分区边界值格式化:空边界为MAXVALUE分区;纯数值原样(VALUES LESS THAN (100)),
	 * 其余按SQL字面量加单引号(日期/时间戳/字符串边界,内部单引号翻倍转义);
	 * 多列边界逗号拼接
	 */
	static String formatBoundaries(String boundaries) {
		if (boundaries == null || boundaries.trim().isEmpty()) {
			return "MAXVALUE";
		}
		StringBuilder sb = new StringBuilder();
		for (String part : boundaries.split(",")) {
			if (sb.length() > 0) {
				sb.append(", ");
			}
			// 单值格式化逻辑与db2/sqlserver共用(TablePartition.formatBoundaryValue)
			sb.append(TablePartition.formatBoundaryValue(part));
		}
		return sb.toString();
	}

	/**
	 * 从分区表达式"RANGE (col1, col2)"提取键列名清单(openGauss分支的分区键标记用);
	 * 实现已上移至TablePartition.keyColumns供db2/sqlserver方言共用
	 */
	public static java.util.List<String> partitionKeyColumns(String expression) {
		return TablePartition.keyColumns(expression);
	}

	@Override
	protected Map<String, TableColumnMeta> columnExtras(DbSession db, String tableName) throws SQLException {		String schema = db.getSchema();
		String catalog = db.getCatalog();
		if (StringUtil.isBlank(schema)) {
			schema = catalog;
		}
		// vanilla PG:分区键标记来自pg_partitioned_table.partattrs;
		// openGauss系无该目录(整个join会让查询报错回退JDBC,注释/默认值/生成列全丢),
		// 改去掉该join,分区键在查询后按getTablePartition表达式的键列清单回标
		boolean vanilla = hasVanillaPartitionCatalog(db);
		String sql = vanilla ? """
				SELECT
				    a.attname AS COLUMN_NAME,
				    d.description AS COMMENTS,
				    CASE WHEN a.attgenerated = 's' THEN 2 ELSE 0 END AS GENERATED_TYPE,
				    CASE WHEN p.partattrs IS NOT NULL AND a.attnum = ANY(p.partattrs) THEN 1 ELSE 0 END AS IS_PARTITION_KEY,
				    pg_get_expr(ad.adbin, ad.adrelid) AS DATA_DEFAULT
				FROM
				    pg_class c
				JOIN pg_namespace n ON c.relnamespace = n.oid
				JOIN pg_attribute a ON c.oid = a.attrelid
				LEFT JOIN pg_description d ON c.oid = d.objoid AND a.attnum = d.objsubid
				LEFT JOIN pg_attrdef ad ON c.oid = ad.adrelid AND a.attnum = ad.adnum
				LEFT JOIN pg_partitioned_table p ON c.oid = p.partrelid
				WHERE  a.attnum > 0
				      AND NOT a.attisdropped
				      AND c.relname = ?"""
				: """
				SELECT
				    a.attname AS COLUMN_NAME,
				    d.description AS COMMENTS,
				    0 AS GENERATED_TYPE,
				    0 AS IS_PARTITION_KEY,
				    pg_get_expr(ad.adbin, ad.adrelid) AS DATA_DEFAULT
				FROM
				    pg_class c
				JOIN pg_namespace n ON c.relnamespace = n.oid
				JOIN pg_attribute a ON c.oid = a.attrelid
				LEFT JOIN pg_description d ON c.oid = d.objoid AND a.attnum = d.objsubid
				LEFT JOIN pg_attrdef ad ON c.oid = ad.adrelid AND a.attnum = ad.adnum
				WHERE  a.attnum > 0
				      AND NOT a.attisdropped
				      AND c.relname = ?""";
		if (StringUtil.isNotBlank(schema)) {
			sql = sql.concat(" AND n.nspname=? ");
		}
		sql = sql.concat(" ORDER BY a.attnum");
		try (PreparedStatement pst = db.getConnection().prepareStatement(sql)) {
			pst.setString(1, tableName);
			if (StringUtil.isNotBlank(schema)) {
				pst.setString(2, schema);
			}
			Map<String, TableColumnMeta> extras = extrasFromQuery(sql, pst);
			if (!vanilla) {
				markPartitionKeys(extras, getTablePartition(db, tableName));
			}
			return extras;
		}
	}

	/** openGauss分支:按分区表达式的键列清单回标分区键(大小写不敏感) */
	private static void markPartitionKeys(Map<String, TableColumnMeta> extras, TablePartition partition) {
		if (partition == null || partition.expression() == null) {
			return;
		}
		for (String keyColumn : partitionKeyColumns(partition.expression())) {
			for (TableColumnMeta colMeta : extras.values()) {
				if (colMeta.getColName() != null && colMeta.getColName().equalsIgnoreCase(keyColumn)) {
					colMeta.setPartitionKey(true);
				}
			}
		}
	}
}
