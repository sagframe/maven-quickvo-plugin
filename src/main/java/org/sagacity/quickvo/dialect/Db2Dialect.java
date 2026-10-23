package org.sagacity.quickvo.dialect;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.sagacity.quickvo.datasource.DbSession;
import org.sagacity.quickvo.datasource.DbType;
import org.sagacity.quickvo.model.PartitionDef;
import org.sagacity.quickvo.model.TableColumnMeta;
import org.sagacity.quickvo.utils.Msg;
import org.sagacity.quickvo.utils.StringUtil;

/**
 * db2方言:主体继承通用JdbcDialect(列信息/主键/索引等JDBC元数据均正常),
 * 仅覆写分区提取——DB2 LUW表分区(2026-10-13真库探针定界):分区键列在
 * SYSCAT.DATAPARTITIONEXPRESSION(每键列一行,含DATAPARTITIONKEYSEQ序号;仅分区表有行,
 * syscat.tables.npartitions对普通表也返回-1不可作判据,syscat.columns.partkeyseq是DPF
 * 哈希分区的序号、范围分区恒0)。DB2表分区仅RANGE形态,据此组装"RANGE (col)"表达式
 * 并在列上回标partitionKey(辅助主键推导依赖该标记)。
 *
 * @author zhongxuchen
 */
public class Db2Dialect extends JdbcDialect {

	public Db2Dialect(DbType dbType) {
		super(dbType);
	}

	/**
	 * 分区策略:syscat.datapartitionexpression按序取键列(仅分区表有行,非分区表查询落空),
	 * DB2表分区仅RANGE,组装"RANGE (col1, col2)"表达式;
	 * 分区明细从syscat.datapartitions按seqno取(真库实测:lowvalue/highvalue已存DDL就绪字面量
	 * ——日期带引号/MAXVALUE为裸关键字,直接透传;highinclusive='N'即ENDING EXCLUSIVE),
	 * 供sqltoy还原DB2的"PARTITION p STARTING (low) ENDING (high) [EXCLUSIVE]"双边界语法
	 */
	@Override
	public TablePartition getTablePartition(DbSession db, String tableName) throws SQLException {
		String schema = db.getSchema();
		// db2元数据schema大写存储(SQLTOYDB),配置小写时对齐
		String realSchema = StringUtil.isNotBlank(schema) ? schema.toUpperCase(Locale.ROOT) : null;
		String sql = "SELECT datapartitionexpression FROM syscat.datapartitionexpression WHERE tabname = ?";
		if (realSchema != null) {
			sql = sql.concat(" AND tabschema = ?");
		}
		sql = sql.concat(" ORDER BY datapartitionkeyseq");
		try (PreparedStatement pst = db.getConnection().prepareStatement(sql)) {
			pst.setString(1, tableName.toUpperCase(Locale.ROOT));
			if (realSchema != null) {
				pst.setString(2, realSchema);
			}
			try (ResultSet rs = pst.executeQuery()) {
				StringBuilder keyColumns = new StringBuilder();
				while (rs.next()) {
					if (keyColumns.length() > 0) {
						keyColumns.append(", ");
					}
					keyColumns.append(rs.getString(1));
				}
				if (keyColumns.length() == 0) {
					return null;
				}
				return new TablePartition("RANGE",
						"RANGE (" + keyColumns.toString().toLowerCase(Locale.ROOT) + ")",
						partitionDefs(db, tableName, realSchema));
			}
		} catch (Exception e) {
			logger.warning(Msg.get("提取表:", "Extract table: ") + tableName
					+ Msg.get(" 分区策略失败:", " failed to extract partition strategy: ") + e.getMessage());
			return null;
		}
	}

	/** 分区明细清单(syscat.datapartitions按seqno序;边界值为DDL就绪字面量直接透传) */
	private List<PartitionDef> partitionDefs(DbSession db, String tableName, String realSchema) throws SQLException {
		String sql = "SELECT datapartitionname, lowvalue, highvalue, highinclusive"
				+ " FROM syscat.datapartitions WHERE tabname = ?";
		if (realSchema != null) {
			sql = sql.concat(" AND tabschema = ?");
		}
		sql = sql.concat(" ORDER BY seqno");
		List<PartitionDef> defs = new ArrayList<>();
		try (PreparedStatement pst = db.getConnection().prepareStatement(sql)) {
			pst.setString(1, tableName.toUpperCase(Locale.ROOT));
			if (realSchema != null) {
				pst.setString(2, realSchema);
			}
			try (ResultSet rs = pst.executeQuery()) {
				while (rs.next()) {
					String name = rs.getString(1);
					String low = rs.getString(2) == null ? null : rs.getString(2).trim();
					String high = rs.getString(3) == null ? null : rs.getString(3).trim();
					boolean endInclusive = "Y".equalsIgnoreCase(rs.getString(4));
					defs.add(new PartitionDef(name, low, null, high, endInclusive));
				}
			}
		}
		return defs;
	}

	/**
	 * 列信息:super走JDBC提取,之后按分区表达式的键列清单回标partitionKey
	 * (db2的分区键标记无extras通道,JDBC元数据也不含此信息)
	 */
	@Override
	public List<TableColumnMeta> getColumns(DbSession db, String tableName, boolean toUpperCase) throws SQLException {
		List<TableColumnMeta> columns = super.getColumns(db, tableName, toUpperCase);
		TablePartition partition = getTablePartition(db, tableName);
		if (partition == null || partition.expression() == null) {
			return columns;
		}
		for (String keyColumn : TablePartition.keyColumns(partition.expression())) {
			for (TableColumnMeta colMeta : columns) {
				if (colMeta.getColName() != null && colMeta.getColName().equalsIgnoreCase(keyColumn)) {
					colMeta.setPartitionKey(true);
				}
			}
		}
		return columns;
	}
}
