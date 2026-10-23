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
 * oracle方言:表注释走user_tab_comments,列扩展信息走ALL_COL_COMMENTS+ALL_TAB_COLS。
 *
 * @author zhongxuchen
 */
public class OracleDialect extends JdbcDialect {

	public OracleDialect(DbType dbType) {
		super(dbType);
	}

	@Override
	public List<TableColumnMeta> getColumns(DbSession db, String tableName, boolean toUpperCase) throws SQLException {
		// Oracle 12c+的invisible列经JDBC getColumns仍会返回(实测23.x驱动),而数据字典HIDDEN_COLUMN='YES';
		// 隐藏列进入实体会被sqltoy纳入insert/update,但该列不在SQL/DDL声明中,故按数据字典剔除
		return removeHiddenColumns(tableName, super.getColumns(db, tableName, toUpperCase),
				queryHiddenColumns(db, tableName, db.getSchema()));
	}

	@Override
	public List<TableMeta> listTables(DbSession db) throws SQLException {
		if (db.isPolardb()) {
			return super.listTables(db);
		}
		// 配置了schema时按all_tab_comments.owner过滤,保证表清单与列查询(JdbcDialect按schema过滤)
		// 视角一致,支持DBA账号为其它用户schema生成;未配置时保持user_tab_comments当前用户视角
		String schema = db.getSchema();
		String sql = schema == null ? "select * from user_tab_comments"
				: "select * from all_tab_comments where owner = ?";
		try (PreparedStatement pst = db.getConnection().prepareStatement(sql)) {
			if (schema != null) {
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
	 * 分区策略:ALL_PART_TABLES.PARTITIONING_TYPE(RANGE/LIST/HASH/AUTO等);
	 * 补充分区键列(ALL_PART_KEY_COLUMNS)与分区明细(ALL_TAB_PARTITIONS.HIGH_VALUE),
	 * 供sqltoy的DDL同步还原真实分区子句(oracle的RANGE/LIST必须显式给出分区清单才能建表)
	 */
	@Override
	public TablePartition getTablePartition(DbSession db, String tableName) throws SQLException {
		String schema = db.getSchema();
		if (StringUtil.isBlank(schema)) {
			schema = db.getCatalog();
		}
		try {
			String type = null;
			try (PreparedStatement pst = db.getConnection().prepareStatement(
					"SELECT PARTITIONING_TYPE FROM ALL_PART_TABLES WHERE TABLE_NAME = ?"
							+ (StringUtil.isNotBlank(schema) ? " AND OWNER = ?" : ""))) {
				pst.setString(1, tableName);
				if (StringUtil.isNotBlank(schema)) {
					pst.setString(2, schema);
				}
				try (ResultSet rs = pst.executeQuery()) {
					if (rs.next()) {
						type = rs.getString(1);
					}
				}
			}
			if (StringUtil.isBlank(type)) {
				return null;
			}
			// 分区键列拼接为表达式
			List<String> keyColumns = new ArrayList<>();
			try (PreparedStatement pst = db.getConnection().prepareStatement(
					"SELECT COLUMN_NAME FROM ALL_PART_KEY_COLUMNS WHERE NAME = ?"
							+ (StringUtil.isNotBlank(schema) ? " AND OWNER = ?" : "") + " ORDER BY COLUMN_POSITION")) {
				pst.setString(1, tableName);
				if (StringUtil.isNotBlank(schema)) {
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
	 * 分区明细:ALL_TAB_PARTITIONS的PARTITION_NAME+HIGH_VALUE。HIGH_VALUE为oracle规范字面量
	 * (日期形如TO_DATE('2026-02-01 00:00:00',...),上界为MAXVALUE),原样存入@PartitionDef.value,
	 * 由sqltoy渲染为VALUES LESS THAN (..);INTERVAL等无显式分区场景返回空表
	 */
	private List<org.sagacity.quickvo.model.PartitionDef> loadPartitionDefs(DbSession db, String tableName,
			String schema) {
		List<org.sagacity.quickvo.model.PartitionDef> defs = new ArrayList<>();
		String sql = "SELECT PARTITION_NAME, HIGH_VALUE FROM ALL_TAB_PARTITIONS WHERE TABLE_NAME = ?"
				+ (StringUtil.isNotBlank(schema) ? " AND TABLE_OWNER = ?" : "") + " ORDER BY PARTITION_POSITION";
		try (PreparedStatement pst = db.getConnection().prepareStatement(sql)) {
			pst.setString(1, tableName);
			if (StringUtil.isNotBlank(schema)) {
				pst.setString(2, schema);
			}
			try (ResultSet rs = pst.executeQuery()) {
				while (rs.next()) {
					// 先读PARTITION_NAME(列1)再读HIGH_VALUE(列2,LONG):Oracle的LONG列要求按升序访问,逆序有ORA-17027风险
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

	@Override
	protected Map<String, TableColumnMeta> columnExtras(DbSession db, String tableName) throws SQLException {
		if (db.isPolardb()) {
			return null;
		}
		String schema = db.getSchema();
		String catalog = db.getCatalog();
		if (StringUtil.isBlank(schema)) {
			schema = catalog;
		}
		String sql = """
				SELECT
				    t.COLUMN_NAME,
				    t.COMMENTS,
				    CASE WHEN c.VIRTUAL_COLUMN = 'YES' THEN 1 ELSE 0 END AS GENERATED_TYPE,
				    CASE WHEN p.COLUMN_NAME IS NOT NULL THEN 1 ELSE 0 END AS IS_PARTITION_KEY,
				    c.DATA_DEFAULT
				FROM ALL_COL_COMMENTS t
				JOIN ALL_TAB_COLS c
				    ON t.OWNER = c.OWNER
				    AND t.TABLE_NAME = c.TABLE_NAME
				    AND t.COLUMN_NAME = c.COLUMN_NAME
				LEFT JOIN ALL_PART_KEY_COLUMNS p
				    ON t.OWNER = p.OWNER
				    AND t.TABLE_NAME = p.NAME
				    AND t.COLUMN_NAME = p.COLUMN_NAME
				WHERE
				    t.TABLE_NAME = ?""";
		if (StringUtil.isNotBlank(schema)) {
			sql = sql.concat(" and t.OWNER=? ");
		}
		sql = sql.concat(" ORDER BY c.COLUMN_ID");
		try (PreparedStatement pst = db.getConnection().prepareStatement(sql)) {
			pst.setString(1, tableName);
			if (StringUtil.isNotBlank(schema)) {
				pst.setString(2, schema);
			}
			return extrasFromQuery(sql, pst);
		}
	}
}
