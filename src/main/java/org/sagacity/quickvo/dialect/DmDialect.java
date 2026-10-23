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
import org.sagacity.quickvo.utils.Msg;

/**
 * 达梦方言:基础元数据走JDBC(驱动完整支持REMARKS/COLUMN_DEF),
 * 补充oracle风格系统视图提取表分区策略与分区键(ALL_PART_TABLES/ALL_PART_KEY_COLUMNS)。
 *
 * @author zhongxuchen
 */
public class DmDialect extends JdbcDialect {

	public DmDialect(DbType dbType) {
		super(dbType);
	}

	@Override
	public List<TableColumnMeta> getColumns(DbSession db, String tableName, boolean toUpperCase) throws SQLException {
		// 与oracle一致:达梦的隐藏列(invisible)HIDDEN_COLUMN='YES',JDBC仍会返回,须按数据字典剔除
		String schema = db.getSchema() != null ? db.getSchema() : db.getCatalog();
		return removeHiddenColumns(tableName, super.getColumns(db, tableName, toUpperCase),
				queryHiddenColumns(db, tableName, schema));
	}

	@Override
	protected Map<String, TableColumnMeta> columnExtras(DbSession db, String tableName) throws SQLException {
		String schema = db.getSchema() != null ? db.getSchema() : db.getCatalog();
		// 默认值/生成表达式仍走JDBC的COLUMN_DEF(DATA_DEFAULT置NULL,避免ALL_TAB_COLS.DATA_DEFAULT为LONG的逆序读取问题);
		// 此处补注释、分区键,以及生成列标记(ALL_TAB_COLS.VIRTUAL_COLUMN='YES',与oracle一致),
		// 否则DM的GENERATED ALWAYS AS列会被当普通可写列,sqltoy将其纳入insert导致写生成列被库拒绝
		String sql = """
				SELECT
				    t.COLUMN_NAME,
				    t.COMMENTS,
				    CAST(NULL AS varchar(200)) AS DATA_DEFAULT,
				    CASE WHEN p.COLUMN_NAME IS NOT NULL THEN 1 ELSE 0 END AS IS_PARTITION_KEY,
				    CASE WHEN c.VIRTUAL_COLUMN = 'YES' THEN 1 ELSE 0 END AS GENERATED_TYPE
				FROM ALL_COL_COMMENTS t
				LEFT JOIN ALL_TAB_COLS c
				    ON t.OWNER = c.OWNER
				    AND t.TABLE_NAME = c.TABLE_NAME
				    AND t.COLUMN_NAME = c.COLUMN_NAME
				LEFT JOIN ALL_PART_KEY_COLUMNS p
				    ON t.OWNER = p.OWNER
				    AND t.TABLE_NAME = p.NAME
				    AND t.COLUMN_NAME = p.COLUMN_NAME
				WHERE t.TABLE_NAME = ?""";
		if (schema != null) {
			sql = sql.concat(" AND t.OWNER = ?");
		}
		sql = sql.concat(" ORDER BY t.COLUMN_NAME");
		try (PreparedStatement pst = db.getConnection().prepareStatement(sql)) {
			pst.setString(1, tableName);
			if (schema != null) {
				pst.setString(2, schema);
			}
			return extrasFromQuery(sql, pst);
		}
	}

	@Override
	public TablePartition getTablePartition(DbSession db, String tableName) throws SQLException {
		String schema = db.getSchema() != null ? db.getSchema() : db.getCatalog();
		try {
			String type = null;
			try (PreparedStatement pst = db.getConnection().prepareStatement(
					"SELECT PARTITIONING_TYPE FROM ALL_PART_TABLES WHERE TABLE_NAME = ?"
							+ (schema != null ? " AND OWNER = ?" : ""))) {
				pst.setString(1, tableName);
				if (schema != null) {
					pst.setString(2, schema);
				}
				try (ResultSet rs = pst.executeQuery()) {
					if (rs.next()) {
						type = rs.getString(1);
					}
				}
			}
			if (type == null || type.trim().isEmpty()) {
				return null;
			}
			// 分区键列拼接为表达式
			List<String> keyColumns = new ArrayList<>();
			try (PreparedStatement pst = db.getConnection().prepareStatement(
					"SELECT COLUMN_NAME FROM ALL_PART_KEY_COLUMNS WHERE NAME = ?"
							+ (schema != null ? " AND OWNER = ?" : "") + " ORDER BY COLUMN_POSITION")) {
				pst.setString(1, tableName);
				if (schema != null) {
					pst.setString(2, schema);
				}
				try (ResultSet rs = pst.executeQuery()) {
					while (rs.next()) {
						keyColumns.add(rs.getString(1));
					}
				}
			}
			return new TablePartition(type.trim().toUpperCase(), String.join(",", keyColumns),
					loadPartitionDefs(db, tableName, schema));
		} catch (Exception e) {
			logger.warning(Msg.get("提取表:", "Extract table: ") + tableName
					+ Msg.get(" 分区策略失败:", " failed to extract partition strategy: ") + e.getMessage());
			return null;
		}
	}

	/**
	 * 分区明细:ALL_TAB_PARTITIONS的PARTITION_NAME+HIGH_VALUE(达梦RANGE/LIST必须显式给出分区清单才能建表)。
	 * HIGH_VALUE为达梦规范字面量(日期形如DATE'2026-02-01',上界为MAXVALUE),原样存入@PartitionDef.value,
	 * 由sqltoy的DDL渲染为VALUES LESS THAN (DATE'..')/VALUES IN (..);无明细返回空表(hash/list自动分区场景)
	 */
	private List<org.sagacity.quickvo.model.PartitionDef> loadPartitionDefs(DbSession db, String tableName,
			String schema) {
		List<org.sagacity.quickvo.model.PartitionDef> defs = new ArrayList<>();
		String sql = "SELECT PARTITION_NAME, HIGH_VALUE FROM ALL_TAB_PARTITIONS WHERE TABLE_NAME = ?"
				+ (schema != null ? " AND TABLE_OWNER = ?" : "") + " ORDER BY PARTITION_POSITION";
		try (PreparedStatement pst = db.getConnection().prepareStatement(sql)) {
			pst.setString(1, tableName);
			if (schema != null) {
				pst.setString(2, schema);
			}
			try (ResultSet rs = pst.executeQuery()) {
				while (rs.next()) {
					// 先读PARTITION_NAME(列1)再读HIGH_VALUE(列2,LONG):Oracle系LONG列要求按升序访问,逆序有ORA-17027风险
					String name = rs.getString(1);
					String highValue = rs.getString(2);
					if (highValue != null) {
						highValue = highValue.trim();
					}
					defs.add(new org.sagacity.quickvo.model.PartitionDef(name, highValue, null));
				}
			}
		} catch (Exception e) {
			logger.warning(Msg.get("提取表:", "Extract table: ") + tableName
					+ Msg.get(" 分区明细失败:", " failed to extract partition definitions: ") + e.getMessage());
		}
		return defs;
	}
}
