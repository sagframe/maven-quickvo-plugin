package org.sagacity.quickvo.dialect;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.sagacity.quickvo.datasource.DbSession;
import org.sagacity.quickvo.datasource.DbType;
import org.sagacity.quickvo.model.PartitionDef;
import org.sagacity.quickvo.model.TableColumnMeta;
import org.sagacity.quickvo.utils.Msg;
import org.sagacity.quickvo.utils.StringUtil;

/**
 * sqlserver方言:列基础信息走sp_columns(注释/默认值/分区/生成列来自sys.columns),
 * 表注释走sys.extended_properties。
 *
 * @author zhongxuchen
 */
public class SqlServerDialect extends JdbcDialect {

	public SqlServerDialect(DbType dbType) {
		super(dbType);
	}

	@Override
	protected Map<String, TableColumnMeta> columnExtras(DbSession db, String tableName) throws SQLException {
		if (db.isPolardb()) {
			return null;
		}
		// SQLServer的catalog是数据库名、schema才是架构名(如dbo),二者语义不同不可混用;
		// 未配置schema时不按架构过滤(连接已限定当前库,与JDBC getColumns的null schemaPattern一致),
		// 避免历史上误把库名当架构名过滤sys.schemas导致零行、整表列被丢
		String schema = db.getSchema();
		String sql = """
				SELECT
				    c.name AS COLUMN_NAME,
				    ISNULL(ep.value, '') AS COMMENTS,
				    ISNULL(dc.definition, '') AS DEFAULT_VALUE,
				    CASE
				        WHEN c.is_computed = 0 THEN 0
				        WHEN cc.is_persisted = 1 THEN 2
				        ELSE 1
				    END AS GENERATED_TYPE,
				    CASE WHEN EXISTS (
				        SELECT 1 FROM sys.index_columns ic
				        WHERE ic.object_id = c.object_id
				        AND ic.column_id = c.column_id
				        AND ic.partition_ordinal > 0
				    ) THEN 1 ELSE 0 END AS IS_PARTITION_KEY,
				    t.name AS TYPE_NAME,
				    c.precision AS COLUMN_PRECISION,
				    c.scale AS COLUMN_SCALE,
				    c.is_nullable AS IS_NULLABLE,
				    c.is_identity AS IS_IDENTITY
				FROM sys.columns c
				JOIN sys.objects t0
				    ON t0.object_id = c.object_id
				    AND t0.type IN ('U', 'V')
				JOIN sys.schemas s
				    ON s.schema_id = t0.schema_id
				JOIN sys.types t
				    ON t.user_type_id = c.user_type_id
				LEFT JOIN sys.extended_properties ep
				    ON ep.major_id = c.object_id
				    AND ep.minor_id = c.column_id
				    AND ep.name = 'MS_Description'
				LEFT JOIN sys.computed_columns cc
				    ON c.object_id = cc.object_id
				    AND c.column_id = cc.column_id
				LEFT JOIN sys.default_constraints dc
				    ON c.default_object_id = dc.object_id
				WHERE t0.name  = ?""";
		if (StringUtil.isNotBlank(schema)) {
			sql = sql.concat(" and s.name=? ");
		}
		sql = sql.concat(" ORDER BY c.COLUMN_ID");
		try (PreparedStatement pst = db.getConnection().prepareStatement(sql)) {
			pst.setString(1, tableName);
			if (StringUtil.isNotBlank(schema)) {
				pst.setString(2, schema);
			}
			try (ResultSet rs = pst.executeQuery()) {
				Map<String, TableColumnMeta> extras = new LinkedHashMap<>();
				while (rs.next()) {
					TableColumnMeta colMeta = new TableColumnMeta();
					colMeta.setColName(rs.getString("COLUMN_NAME"));
					// 换行归一,避免多行注释破坏单行注解
					colMeta.setColRemark(StringUtil.clearMistyChars(rs.getString("COMMENTS"), " "));
					if (rs.getInt("IS_PARTITION_KEY") == 1) {
						colMeta.setPartitionKey(true);
					}
					colMeta.setGeneratedType(rs.getInt("GENERATED_TYPE"));
					// 无默认值时sys.default_constraints.definition为null,ISNULL转成了空串,还原为null
					String defaultValue = rs.getString("DEFAULT_VALUE");
					colMeta.setColDefault(defaultValue == null || defaultValue.isEmpty() ? null : defaultValue);
					// 类型信息(补JDBC驱动尚未支持的列类型,如sqlserver2025的VECTOR)
					colMeta.setTypeName(rs.getString("TYPE_NAME"));
					colMeta.setPrecision(rs.getInt("COLUMN_PRECISION"));
					colMeta.setLength(colMeta.getPrecision());
					colMeta.setScale(rs.getInt("COLUMN_SCALE"));
					colMeta.setNullable(rs.getInt("IS_NULLABLE") == 1);
					colMeta.setAutoIncrement(rs.getInt("IS_IDENTITY") == 1);
					extras.put(colMeta.getColName(), colMeta);
				}
				return extras;
			}
		}
	}

	/**
	 * 列信息走标准JDBC getColumns+sys.columns扩展信息合并(历史版本用sp_columns,
	 * 但sp_columns和旧驱动getColumns均不支持sqlserver2025的VECTOR类型会丢列,
	 * 故再用sys.columns的类型信息补齐JDBC结果中缺失的列)
	 */
	@Override
	public List<TableColumnMeta> getColumns(DbSession db, String tableName, boolean toUpperCase)
			throws SQLException {
		List<TableColumnMeta> result = super.getColumns(db, tableName, toUpperCase);
		Map<String, TableColumnMeta> extras = safeColumnExtras(db, tableName);
		if (extras != null) {
			java.util.Set<String> present = new java.util.HashSet<>();
			for (TableColumnMeta col : result) {
				present.add(col.getColName().toLowerCase());
			}
			for (TableColumnMeta extra : extras.values()) {
				if (!present.contains(extra.getColName().toLowerCase())) {
					result.add(extra);
				}
			}
		}
		return result;
	}

	/**
	 * 表注释走sys.extended_properties(JDBC元数据不返回表注释)
	 */
	@Override
	public String getTableRemark(DbSession db, String tableName) throws SQLException {
		// nvarchar保留中文注释(历史版本cast成varchar会丢失非ASCII字符)
		String sql = "select cast(isnull(f.value,'') as nvarchar(1000)) COMMENTS"
				+ " from syscolumns a"
				+ " inner join sysobjects d on a.id=d.id and d.xtype in ('U','V') and d.name<>'dtproperties'"
				+ " left join sys.extended_properties f on d.id=f.major_id and f.minor_id=0"
				+ " where a.colorder=1 and d.name=?";
		try (PreparedStatement pst = db.getConnection().prepareStatement(sql)) {
			pst.setString(1, tableName);
			try (ResultSet rs = pst.executeQuery()) {
				// 换行归一,避免多行注释破坏单行注解
				return rs.next() ? org.sagacity.quickvo.utils.StringUtil.clearMistyChars(rs.getString("COMMENTS"), " ")
						: null;
			}
		}
	}

	/**
	 * 分区策略(2026-10-13):sqlserver分区=分区函数+分区方案,表/索引经data_space_id挂在方案上;
	 * 键列取聚集索引/堆的分区列(index_columns.partition_ordinal>0,按序拼接——与columnExtras的
	 * IS_PARTITION_KEY同源);方向取分区函数boundary_value_on_right(RANGE RIGHT/LEFT,
	 * sqltoy的SqlServerDDLGenerator据此还原CREATE PARTITION FUNCTION);
	 * 边界值明细取sys.partition_range_values(sqltoy还原FOR VALUES清单,分区数=边界数+1)。
	 * 非分区表(无分区方案)关联落空返回null
	 */
	@Override
	public TablePartition getTablePartition(DbSession db, String tableName) throws SQLException {
		String sql = """
				SELECT c.name, pf.function_id, pf.boundary_value_on_right
				FROM sys.tables t
				JOIN sys.indexes i ON i.object_id = t.object_id AND i.index_id IN (0, 1)
				JOIN sys.partition_schemes ps ON i.data_space_id = ps.data_space_id
				JOIN sys.partition_functions pf ON ps.function_id = pf.function_id
				JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
				     AND ic.partition_ordinal > 0
				JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
				WHERE t.name = ?""";
		String schema = db.getSchema();
		if (StringUtil.isNotBlank(schema)) {
			sql = sql.concat(" AND t.schema_id = SCHEMA_ID(?)");
		}
		sql = sql.concat(" ORDER BY ic.partition_ordinal");
		try (PreparedStatement pst = db.getConnection().prepareStatement(sql)) {
			pst.setString(1, tableName);
			if (StringUtil.isNotBlank(schema)) {
				pst.setString(2, schema);
			}
			try (ResultSet rs = pst.executeQuery()) {
				StringBuilder keyColumns = new StringBuilder();
				int functionId = -1;
				boolean boundaryOnRight = true;
				while (rs.next()) {
					if (keyColumns.length() > 0) {
						keyColumns.append(", ");
					}
					keyColumns.append(rs.getString(1));
					functionId = rs.getInt(2);
					boundaryOnRight = rs.getBoolean(3);
				}
				if (keyColumns.length() == 0) {
					return null;
				}
				String strategy = boundaryOnRight ? "RANGE RIGHT" : "RANGE LEFT";
				return new TablePartition(strategy, "RANGE (" + keyColumns + ")",
						boundaryDefs(db, functionId));
			}
		} catch (Exception e) {
			logger.warning(Msg.get("提取表:", "Extract table: ") + tableName
					+ Msg.get(" 分区策略失败:", " failed to extract partition strategy: ") + e.getMessage());
			return null;
		}
	}

	/**
	 * 分区函数边界值明细(sys.partition_range_values按boundary_id序):
	 * sqlserver分区匿名(无分区名),def名按边界序号b<n>派生;值CONVERT为ISO8601文本(style 126,
	 * 裸CAST对date产出"Feb  1 2026"语言相关格式,回填FOR VALUES不可靠)后格式化
	 * (日期/字符串加引号,数值原样——与openGauss系共用TablePartition.formatBoundaryValue)
	 */
	private List<PartitionDef> boundaryDefs(DbSession db, int functionId) throws SQLException {
		List<PartitionDef> defs = new ArrayList<>();
		if (functionId < 0) {
			return defs;
		}
		String sql = "SELECT boundary_id, CONVERT(nvarchar(200), value, 126) AS bound_value"
				+ " FROM sys.partition_range_values WHERE function_id = ? ORDER BY boundary_id";
		try (PreparedStatement pst = db.getConnection().prepareStatement(sql)) {
			pst.setInt(1, functionId);
			try (ResultSet rs = pst.executeQuery()) {
				while (rs.next()) {
					defs.add(new PartitionDef("b" + rs.getInt(1),
							TablePartition.formatBoundaryValue(rs.getString(2)), null));
				}
			}
		}
		return defs;
	}
}
