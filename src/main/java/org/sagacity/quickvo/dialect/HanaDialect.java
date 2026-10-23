package org.sagacity.quickvo.dialect;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import org.sagacity.quickvo.datasource.DbSession;
import org.sagacity.quickvo.datasource.DbType;
import org.sagacity.quickvo.model.TableColumnMeta;
import org.sagacity.quickvo.utils.Msg;

/**
 * SAP HANA方言:基础元数据走JDBC(驱动完整支持REMARKS/COLUMN_DEF),
 * 补充SYS.TABLE_COLUMNS提取计算列——HANA支持SQL存储型生成列(GENERATED ALWAYS AS),
 * 表达式在TABLE_COLUMNS.GENERATED_ALWAYS_AS列,JDBC的COLUMN_DEF不返回(实测null),
 * 故必须走系统视图;HANA无SQL层虚拟计算列(仅CDS视图层有),GENERATION_TYPE恒为ALWAYS AS。
 *
 * @author zhongxuchen
 */
public class HanaDialect extends JdbcDialect {

	public HanaDialect(DbType dbType) {
		super(dbType);
	}

	@Override
	protected Map<String, TableColumnMeta> columnExtras(DbSession db, String tableName) throws SQLException {
		String schema = db.getSchema() != null ? db.getSchema() : db.getCatalog();
		// 默认值仍走JDBC的COLUMN_DEF(此处置NULL),此处补注释、计算列标记,以及分区键标记
		// (分区键来自SYS.PARTITIONED_TABLES.LEVEL_1_EXPRESSION,其值为带双引号的标识符如"TRADE_ID")
		String sql = """
				SELECT
				    t.COLUMN_NAME,
				    t.COMMENTS,
				    CAST(NULL AS NVARCHAR(200)) AS DATA_DEFAULT,
				    CASE WHEN '"' || t.COLUMN_NAME || '"' = (SELECT p.LEVEL_1_EXPRESSION FROM SYS.PARTITIONED_TABLES p
				         WHERE p.TABLE_NAME = t.TABLE_NAME AND p.SCHEMA_NAME = t.SCHEMA_NAME) THEN 1 ELSE 0 END AS IS_PARTITION_KEY,
				    CASE WHEN t.GENERATED_ALWAYS_AS IS NOT NULL THEN 2 ELSE 0 END AS GENERATED_TYPE
				FROM SYS.TABLE_COLUMNS t
				WHERE t.TABLE_NAME = ?""";
		if (schema != null) {
			sql = sql.concat(" AND t.SCHEMA_NAME = ?");
		}
		sql = sql.concat(" ORDER BY t.POSITION");
		try (PreparedStatement pst = db.getConnection().prepareStatement(sql)) {
			pst.setString(1, tableName);
			if (schema != null) {
				pst.setString(2, schema);
			}
			return extrasFromQuery(sql, pst);
		} catch (Exception e) {
			logger.warning(Msg.get("提取表:", "Extract table: ") + tableName
					+ Msg.get(" 列扩展信息(注释/计算列)失败,回退JDBC标准元数据:", " failed to extract column extras (comment/generated), falling back to standard JDBC metadata: ")
					+ e.getMessage());
			return null;
		}
	}

	/**
	 * 分区策略:直接取SYS.PARTITIONED_TABLES.PARTITION_DEFINITION——它是HANA原生分区子句全文
	 * (含类型/分区键/分区清单,如"PARTITION BY RANGE ("SALE_DATE") (PARTITION '20240101' <= VALUES < '20250101', PARTITION OTHERS)")。
	 * 去掉"PARTITION BY "前缀与标识符双引号后作为expression透传,sqltoy据"表达式以策略名开头"走原生表达式渲染分支,
	 * 于是HASH/RANGE/ROUNDROBIN一律原样还原——RANGE的"min <= VALUES < max"/OTHERS、ROUNDROBIN无键等HANA特有语法
	 * 都无需逐类型拼装(此前误判HANA RANGE不可回写,实为分区语法用错;透传原生定义天然规避方言差异)。仅处理一级分区
	 */
	@Override
	public TablePartition getTablePartition(DbSession db, String tableName) throws SQLException {
		String schema = db.getSchema() != null ? db.getSchema() : db.getCatalog();
		String sql = "SELECT LEVEL_1_TYPE, PARTITION_DEFINITION FROM SYS.PARTITIONED_TABLES WHERE TABLE_NAME = ?"
				+ (schema != null ? " AND SCHEMA_NAME = ?" : "");
		try (PreparedStatement pst = db.getConnection().prepareStatement(sql)) {
			pst.setString(1, tableName);
			if (schema != null) {
				pst.setString(2, schema);
			}
			try (ResultSet rs = pst.executeQuery()) {
				if (!rs.next()) {
					return null;
				}
				String type = rs.getString(1);
				String definition = rs.getString(2);
				if (type == null || type.trim().isEmpty() || definition == null || definition.trim().isEmpty()) {
					return null;
				}
				// 去掉"PARTITION BY "前缀,使expression以策略名(RANGE/HASH/ROUNDROBIN)开头,命中sqltoy原生表达式渲染分支
				String expr = definition.trim();
				if (expr.toUpperCase(java.util.Locale.ROOT).startsWith("PARTITION BY ")) {
					expr = expr.substring("PARTITION BY ".length()).trim();
				}
				// 去掉标识符双引号:HANA字符串字面量用单引号,双引号仅包裹标识符;去引号后标识符随大小写策略转换
				// (如SALE_DATE→sale_date)仍被HANA折叠回大写,不会错列;日期/数值字面量不含双引号故不受影响
				expr = expr.replace("\"", "");
				return new TablePartition(type.trim().toUpperCase(), expr, java.util.List.of());
			}
		} catch (Exception e) {
			logger.warning(Msg.get("提取表:", "Extract table: ") + tableName
					+ Msg.get(" 分区策略失败:", " failed to extract partition strategy: ") + e.getMessage());
			return null;
		}
	}
}
