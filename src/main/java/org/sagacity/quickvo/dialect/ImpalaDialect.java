package org.sagacity.quickvo.dialect;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.sagacity.quickvo.datasource.DbSession;
import org.sagacity.quickvo.datasource.DbType;
import org.sagacity.quickvo.utils.Msg;

/**
 * impala方言:主键信息通过DESCRIBE提取。
 *
 * @author zhongxuchen
 */
public class ImpalaDialect extends JdbcDialect {

	public ImpalaDialect(DbType dbType) {
		super(dbType);
	}

	@Override
	public List<String> getPrimaryKeys(DbSession db, String tableName) throws SQLException {
		try (Statement st = db.getConnection().createStatement();
				ResultSet rs = st.executeQuery("DESCRIBE " + tableName)) {
			List<String> result = new ArrayList<>();
			while (rs.next()) {
				if (rs.getBoolean("PRIMARY_KEY")) {
					result.add(rs.getString("NAME"));
				}
			}
			return result;
		} catch (Exception e) {
			logger.warning(Msg.get("提取表:","Extract table: ") + tableName + Msg.get(" 主键信息失败:"," failed to extract primary keys: ") + e.getMessage());
			return new ArrayList<>();
		}
	}

	@Override
	public String getPkConstraint(DbSession db, String tableName) {
		return null;
	}
}
