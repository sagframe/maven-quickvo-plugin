package org.sagacity.quickvo.dialect;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.sagacity.quickvo.datasource.DbSession;
import org.sagacity.quickvo.datasource.DbType;
import org.sagacity.quickvo.model.IndexModel;
import org.sagacity.quickvo.model.TableColumnMeta;
import org.sagacity.quickvo.model.TableMeta;
import org.sagacity.quickvo.utils.StringUtil;
import org.sagacity.quickvo.utils.Msg;

/**
 * 基于JDBC DatabaseMetaData的通用方言实现,各数据库方言继承后只覆盖差异部分。
 * 原DBHelper中重复5次的"扩展信息查询+JDBC列信息合并"逻辑收敛到此处。
 *
 * @author zhongxuchen
 */
public class JdbcDialect implements MetadataDialect {
	/** postgresql默认值::type强转尾巴匹配 */
	protected static final Pattern PG_CAST_PATTERN = Pattern
			.compile("::[a-zA-Z_][a-zA-Z0-9_]*(?:\\s+[a-zA-Z0-9_]+)*(?:\\(\\d+(?:,\\d+)?\\))?$");

	/** pg系序列默认值匹配:nextval('seq_name'::regclass) */
	protected static final Pattern NEXTVAL_PATTERN = Pattern.compile("(?i)nextval\\s*\\(");

	/** oracle系序列默认值匹配:"SCHEMA"."ISEQ$$_xxx".nextval(identity系统序列)或SEQ_xxx.NEXTVAL */
	protected static final Pattern NEXTVAL_KEYWORD = Pattern
			.compile("(?i)(nextval\\s*\\(|\\.nextval\\s*$)");

	protected final Logger logger = Logger.getLogger("sagacity.quickvo.dialect");
	protected final DbType dbType;

	public JdbcDialect(DbType dbType) {
		this.dbType = dbType;
	}

	@Override
	public DbType getDbType() {
		return dbType;
	}

	@Override
	public List<TableMeta> listTables(DbSession db) throws SQLException {
		try (ResultSet rs = db.meta().getTables(db.getCatalog(), db.getSchema(), null,
				new String[] { "TABLE", "VIEW" })) {
			List<TableMeta> tables = new ArrayList<>();
			while (rs.next()) {
				tables.add(toTableMeta(db, rs.getString("TABLE_NAME"), rs.getString("TABLE_TYPE"),
						StringUtil.clearMistyChars(rs.getString("REMARKS"), " ")));
			}
			return tables;
		}
	}

	/**
	 * 列扩展信息(注释/默认值/分区/生成列),key为列名;默认无,由具体方言覆盖
	 */
	protected Map<String, TableColumnMeta> columnExtras(DbSession db, String tableName) throws SQLException {
		return null;
	}

	@Override
	public List<TableColumnMeta> getColumns(DbSession db, String tableName, boolean toUpperCase)
			throws SQLException {
		Map<String, TableColumnMeta> extras = safeColumnExtras(db, tableName);
		try (ResultSet rs = openColumns(db, tableName, toUpperCase)) {
			return mergeColumnMeta(db, rs, extras, tableName);
		}
	}

	/**
	 * 查询数据字典标记为隐藏的列(Oracle/达梦的invisible列);库无HIDDEN_COLUMN列或权限不足时返回空集,
	 * 调用方据此保持原行为
	 */
	protected java.util.Set<String> queryHiddenColumns(DbSession db, String tableName, String schema) {
		java.util.Set<String> hidden = new java.util.HashSet<>();
		String sql = "SELECT COLUMN_NAME FROM ALL_TAB_COLS WHERE TABLE_NAME = ?"
				+ (StringUtil.isNotBlank(schema) ? " AND OWNER = ?" : "") + " AND HIDDEN_COLUMN = 'YES'";
		try (java.sql.PreparedStatement pst = db.getConnection().prepareStatement(sql)) {
			pst.setString(1, tableName);
			if (StringUtil.isNotBlank(schema)) {
				pst.setString(2, schema);
			}
			try (java.sql.ResultSet rs = pst.executeQuery()) {
				while (rs.next()) {
					hidden.add(rs.getString(1));
				}
			}
		} catch (Exception e) {
			// 老版本无HIDDEN_COLUMN列/无ALL_TAB_COLS权限:不剔除,维持既有行为
			return java.util.Set.of();
		}
		return hidden;
	}

	/**
	 * 从JDBC列清单中剔除隐藏列:Oracle 12c+的invisible列经JDBC getColumns仍会返回,
	 * 若进入实体会被sqltoy纳入insert/update,而该列在SQL/DDL中并不存在,运行期报错
	 */
	protected List<TableColumnMeta> removeHiddenColumns(String tableName, List<TableColumnMeta> columns,
			java.util.Set<String> hidden) {
		if (hidden == null || hidden.isEmpty()) {
			return columns;
		}
		List<TableColumnMeta> result = new ArrayList<>(columns.size());
		List<String> removed = new ArrayList<>();
		for (TableColumnMeta col : columns) {
			if (col.getColName() != null && hidden.contains(col.getColName())) {
				removed.add(col.getColName());
			} else {
				result.add(col);
			}
		}
		if (!removed.isEmpty()) {
			logger.info(Msg.get("表:", "Table: ") + tableName
					+ Msg.get(" 忽略隐藏列(invisible):", " skipped hidden (invisible) columns: ")
					+ String.join(",", removed));
		}
		return result;
	}

	/**
	 * 打开JDBC列信息结果集;mysql用"%"通配,oracle支持schema/catalog转大写重试
	 */
	protected ResultSet openColumns(DbSession db, String tableName, boolean toUpperCase) throws SQLException {
		String catalog = db.getCatalog();
		String schema = db.getSchema();
		if (db.getDbType().isMySqlFamily() && !db.isPolardb()) {
			return db.meta().getColumns(catalog, schema, tableName, "%");
		}
		if (toUpperCase) {
			return db.meta().getColumns(catalog == null ? null : catalog.toUpperCase(),
					schema == null ? null : schema.toUpperCase(), tableName, null);
		}
		return db.meta().getColumns(catalog, schema, tableName, null);
	}

	/**
	 * JDBC列信息与方言扩展信息合并,保持原DBHelper的完整合并语义
	 */
	protected List<TableColumnMeta> mergeColumnMeta(DbSession db, ResultSet rs,
			Map<String, TableColumnMeta> extras, String tableName) throws SQLException {
		List<TableColumnMeta> result = new ArrayList<>();
		boolean clickhouse = db.getDbType() == DbType.CLICKHOUSE;
		boolean oracle = db.getDbType() == DbType.ORACLE;
		boolean pgFamily = db.getDbType().isPgFamilyMetadata();
		// 引号标识符启发式仅适用oracle/pg系(其默认值中的"col"是列引用);
		// mysql系JSON表达式默认值含引号但并非生成列
		// 用isMySqlFamily()统一判定(含MYSQL/MYSQL57/OCEANBASE),避免OceanBase被漏判:
		// OB(mysql模式)的JSON表达式默认值同样含引号但并非生成列,生成列由MySqlDialect的extras识别
		boolean mysqlFamily = db.getDbType().isMySqlFamily();
		// extras为空(扩展查询因schema/catalog未命中、或视图不在sys.tables等而返回0行)时回退标准JDBC元数据,
		// 避免整表列被丢弃导致生成失败(降级保留列,仅注释/默认值/分区键等扩展信息可能缺失)
		boolean hasExtras = extras != null && !extras.isEmpty();
		if (extras != null && extras.isEmpty()) {
			logger.warning(Msg.get("表:", "Table: ") + tableName
					+ Msg.get(" 扩展元数据为空,回退标准JDBC元数据(注释/默认值/分区键可能缺失),请检查schema/catalog配置!",
							" column extras empty, falling back to standard JDBC metadata (comments/defaults/partition-keys may be missing); check schema/catalog config!"));
		}
		while (rs.next()) {
			String colName = rs.getString("COLUMN_NAME");
			TableColumnMeta colMeta;
			if (!hasExtras) {
				colMeta = new TableColumnMeta();
				colMeta.setColName(colName);
				String rawDefault = rs.getString("COLUMN_DEF");
					// 序列/identity默认值(oracle的"SCHEMA"."ISEQ$$_n".nextval、pg的nextval(..))含引号标识符,
					// 但属自增而非STORED生成列,须排除在引号启发式之外,交由下方NEXTVAL归一处理
					if (!mysqlFamily && isQuotedIdentifierGeneratedDefault(rawDefault)) {
						colMeta.setGeneratedType(2);
						colMeta.setColDefault(null);
					} else {
						colMeta.setColDefault(clearDefaultValue(rawDefault));
					}
				colMeta.setColRemark(StringUtil.clearMistyChars(rs.getString("REMARKS"), " "));
			} else {
				colMeta = extras.get(colName);
				if (colMeta != null && colMeta.getColDefault() != null) {
						// 原始DEFAULT含列引用(如"SALARY"+"BONUS")则为STORED生成列(Oracle DATA_DEFAULT为LONG,SQL侧无法REGEXP);
						// 但序列/identity默认值(.."ISEQ$$_n".nextval)同样含引号,须排除,交由下方NEXTVAL归一为自增
						if (!mysqlFamily && colMeta.getGeneratedType() == 0
								&& isQuotedIdentifierGeneratedDefault(colMeta.getColDefault())) {
							colMeta.setGeneratedType(2);
							colMeta.setColDefault(null);
						} else {
							colMeta.setColDefault(clearDefaultValue(colMeta.getColDefault()));
						}
					} else if (colMeta != null && !clickhouse) {
					// clickhouse默认值已在扩展信息中提取
					colMeta.setColDefault(clearDefaultValue(rs.getString("COLUMN_DEF")));
				}
			}
			if (colMeta == null) {
				logger.warning(Msg.get("表:", "Table: ") + tableName + Msg.get(" 对应的列:", " column: ") + colName
						+ Msg.get("不在当前用户表字段内,请检查schema或catalog配置是否正确!"," not in current schema, check schema/catalog configuration!"));
				continue;
			}
			colMeta.setDataType(rs.getInt("DATA_TYPE"));
			if (!clickhouse || !hasExtras) {
				// clickhouse正常从system.columns取类型;但扩展信息失败回退时必须从JDBC补齐,避免typeName=null下游NPE
				colMeta.setTypeName(rs.getString("TYPE_NAME"));
			}
			colMeta.setLength(rs.getInt("COLUMN_SIZE"));
			colMeta.setPrecision(colMeta.getLength());
			colMeta.setScale(rs.getInt("DECIMAL_DIGITS"));
			colMeta.setNumPrecRadix(rs.getInt("NUM_PREC_RADIX"));
			String isAutoIncrement = rs.getString("IS_AUTOINCREMENT");
			colMeta.setAutoIncrement("true".equalsIgnoreCase(isAutoIncrement) || "yes".equalsIgnoreCase(isAutoIncrement)
					|| "y".equalsIgnoreCase(isAutoIncrement) || "1".equals(isAutoIncrement));
			// oracle系:identity列的DATA_DEFAULT为系统序列"ISEQ$$_xxx".nextval,
			// 显式序列默认值为SEQ_xxx.NEXTVAL;pg系serial/identity默认值为nextval('seq'::regclass)。
			// 统一视同数据库自增(数据库优先→identity策略),序列表达式作为defaultValue无意义,清除
			if ((oracle || pgFamily) && colMeta.getColDefault() != null
					&& NEXTVAL_KEYWORD.matcher(colMeta.getColDefault()).find()) {
				colMeta.setAutoIncrement(true);
				colMeta.setColDefault(null);
			}
			if (colMeta.getColDefault() != null && colMeta.getColDefault().equalsIgnoreCase("NULL")) {
				colMeta.setColDefault(null);
			}
			colMeta.setNullable(rs.getInt("NULLABLE") == 1);
			result.add(colMeta);
		}
		return result;
	}

	/**
	 * 引号标识符启发式:默认值含"col"形态的列引用(如oracle的"SALARY"+"BONUS")视为STORED生成列;
	 * 判定前先剔除单引号字符串字面量(含''转义形态),避免json/文本默认值(如pg的'{"a":1}'::jsonb、
	 * oracle的'{"type":"x"}')内部的双引号被误判为列引用——误判会把普通列标成生成列,
	 * 进而被sqltoy排除出insert/update造成写入静默丢失
	 */
	protected static boolean isQuotedIdentifierGeneratedDefault(String rawDefault) {
		if (rawDefault == null || rawDefault.indexOf('"') == -1) {
			return false;
		}
		String stripped = rawDefault.replaceAll("'(?:[^']|'')*'", "''");
		return stripped.matches(".*\"[A-Za-z_][A-Za-z0-9_]*\".*") && !NEXTVAL_KEYWORD.matcher(stripped).find();
	}

	/** 组装表元信息:类型统一归一为TABLE/VIEW,schema取数据源配置 */
	protected TableMeta toTableMeta(DbSession db, String tableName, String tableType, String remark) {
		TableMeta tableMeta = new TableMeta();
		tableMeta.setTableName(tableName);
		tableMeta.setSchema(db.getSchema());
		tableMeta.setTableType(tableType != null && tableType.toLowerCase().contains("view") ? "VIEW" : "TABLE");
		tableMeta.setTableRemark(remark);
		return tableMeta;
	}

	@Override
	public List<String> getPrimaryKeys(DbSession db, String tableName) throws SQLException {
		try (ResultSet rs = db.meta().getPrimaryKeys(db.getCatalog(), db.getSchema(),
				tableName)) {
			// LinkedHashSet去重同时保持主键列的约束内顺序
			LinkedHashSet<String> pkList = new LinkedHashSet<>();
			while (rs.next()) {
				pkList.add(rs.getString("COLUMN_NAME"));
			}
			return new ArrayList<>(pkList);
		}
	}

	@Override
	public String getPkConstraint(DbSession db, String tableName) throws SQLException {
		try (ResultSet rs = db.meta().getPrimaryKeys(db.getCatalog(), db.getSchema(),
				tableName)) {
			return rs.next() ? rs.getString("PK_NAME") : null;
		}
	}

	@Override
	public List<IndexModel> getIndexInfo(DbSession db, String tableName, String pkName) throws SQLException {
		List<IndexModel> result = new ArrayList<>();
		LinkedHashSet<String> indexNames = new LinkedHashSet<>();
		collectIndexes(db, tableName, pkName, true, result, indexNames);
		collectIndexes(db, tableName, pkName, false, result, indexNames);
		return result;
	}

		private void collectIndexes(DbSession db, String tableName, String pkName, boolean unique, List<IndexModel> result,
				LinkedHashSet<String> indexNames) throws SQLException {
		try (ResultSet rs = db.meta().getIndexInfo(db.getCatalog(), db.getSchema(),
				tableName, unique, false)) {
			Map<String, IndexModel> indexMap = new LinkedHashMap<>();
				while (rs.next()) {
					String indexName = rs.getString("INDEX_NAME");
					if ((indexName == null || indexName.equalsIgnoreCase("PRIMARY"))
							|| (pkName != null && indexName.equalsIgnoreCase(pkName))) {
						continue;
					}
					// 表统计行(TYPE=tableIndexStatistic,COLUMN_NAME为null)不参与索引构建
					if (rs.getShort("TYPE") == java.sql.DatabaseMetaData.tableIndexStatistic) {
						continue;
					}
					// 过滤数据库系统自动生成的索引名:DM的index\d+、Oracle的SYS_、H2的primary_key_N、
					// SQLite的sqlite_autoindex_(PK/UNIQUE约束的隐式索引,重建表时由PRIMARY KEY子句自动产生,
					// 且sqlite_autoindex_为SQLite保留前缀,显式CREATE INDEX会报SQLITE_ERROR)
					if (indexName.matches("(?i)index\\d+") || indexName.toUpperCase().startsWith("SYS_")
							|| indexName.toLowerCase().startsWith("primary_key_")
							|| indexName.toLowerCase().startsWith("sqlite_autoindex_")) {
						continue;
					}
					String columnName = rs.getString("COLUMN_NAME");
					String sortType = rs.getString("ASC_OR_DESC");
					sortType = sortType == null ? "" : ("A".equalsIgnoreCase(sortType) ? "ASC" : "DESC");
					IndexModel indexModel = indexMap.get(indexName);
					if (indexModel == null) {
						// indexNames仅用于跨unique/非unique两轮去重:本轮内同名索引的后续列行走下方追加分支,
						// 若在此处按名字continue,组合索引的第2..N列会被误杀(历史缺陷:多列追加分支成死代码)
						if (indexNames.contains(indexName)) {
							continue;
						}
						indexModel = new IndexModel();
						indexModel.setIndexName(indexName);
						indexModel.setSortTypes(new String[] { sortType });
						indexModel.setColumns(new String[] { columnName });
						// 唯一性以NON_UNIQUE列为准,而非getIndexInfo的unique入参:部分驱动(如sqlite-jdbc)
						// 忽略unique过滤参数,对非唯一索引也在unique=true调用中返回,若以入参判定会把普通索引误标为唯一
						indexModel.setIsUnique(!rs.getBoolean("NON_UNIQUE"));
						indexModel.setTableName(tableName);
						result.add(indexModel);
						indexNames.add(indexName);
					} else {
					List<String> columns = new ArrayList<>(List.of(indexModel.getColumns()));
					columns.add(columnName);
					List<String> sortTypes = new ArrayList<>(List.of(indexModel.getSortTypes()));
					sortTypes.add(sortType);
					indexModel.setColumns(columns.toArray(new String[0]));
					indexModel.setSortTypes(sortTypes.toArray(new String[0]));
				}
				indexMap.put(indexName, indexModel);
			}
		} catch (Exception e) {
			logger.warning(Msg.get("提取表:","Extract table: ") + tableName + Msg.get(" 索引信息失败:"," failed to extract index info: ") + e.getMessage());
		}
	}

	/**
	 * 扩展信息提取失败时回退纯JDBC元数据(兼容部分衍生库缺系统表场景),但必须留下可排查的告警
	 */
	protected Map<String, TableColumnMeta> safeColumnExtras(DbSession db, String tableName) {
		try {
			return columnExtras(db, tableName);
		} catch (Exception e) {
			logger.warning(Msg.get("提取表:","Extract table: ") + tableName + Msg.get(" 列扩展信息(注释/默认值/分区)失败,回退JDBC标准元数据:"," failed to extract column extras (comment/default/partition), falling back to standard JDBC metadata: ") + e.getMessage());
			return null;
		}
	}

	/**
	 * 通用扩展信息行映射:结果集需含COLUMN_NAME/COMMENTS/DATA_DEFAULT/IS_PARTITION_KEY/GENERATED_TYPE
	 */
	protected TableColumnMeta mapExtraRow(ResultSet rs) throws SQLException {
		TableColumnMeta colMeta = new TableColumnMeta();
		colMeta.setColName(rs.getString("COLUMN_NAME"));
		colMeta.setColRemark(StringUtil.clearMistyChars(rs.getString("COMMENTS"), " "));
		colMeta.setColDefault(StringUtil.trim(rs.getString("DATA_DEFAULT")));
		if (rs.getInt("IS_PARTITION_KEY") == 1) {
			colMeta.setPartitionKey(true);
		}
		colMeta.setGeneratedType(rs.getInt("GENERATED_TYPE"));
		return colMeta;
	}

	/** 扩展信息查询结果整表装配 */
	protected Map<String, TableColumnMeta> extrasFromQuery(String sql, PreparedStatement pst) throws SQLException {
		try (PreparedStatement statement = pst; ResultSet rs = statement.executeQuery()) {
			Map<String, TableColumnMeta> extras = new LinkedHashMap<>();
			while (rs.next()) {
				TableColumnMeta colMeta = mapExtraRow(rs);
				extras.put(colMeta.getColName(), colMeta);
			}
			return extras;
		}
	}

	/**
	 * 处理sqlserver default值为((value))以及postgresql的::type强转尾巴
	 */
	protected static String clearDefaultValue(String defaultValue) {
		if (defaultValue == null) {
			return null;
		}
		if (defaultValue.trim().isEmpty()) {
			return "";
		}
		String result = defaultValue.trim();
		// mysql8表达式默认值带字符集引导符:(_utf8mb4'{"a":1}') -> 剥掉_utf8mb4保留字面量
		result = result.replaceFirst("(?i)^_[A-Za-z0-9]+(?=['\"])", "");
		if (result.toUpperCase().startsWith("NULL::") && StringUtil.matches(result, PG_CAST_PATTERN)) {
			return null;
		}
		result = PG_CAST_PATTERN.matcher(result).replaceAll("").trim();
		String[][] wrappers = { { "((", "))" }, { "(", ")" }, { "'", "'" }, { "\"", "\"" } };
		for (String[] wrap : wrappers) {
			if (result.startsWith(wrap[0]) && result.endsWith(wrap[1])) {
				result = result.substring(wrap[0].length(), result.length() - wrap[1].length()).trim();
				break;
			}
		}
		// postgresql 去除::text、::double precision、::char(10)、::numeric(10,2)结尾内容
		return PG_CAST_PATTERN.matcher(result).replaceAll("").trim();
	}
}
