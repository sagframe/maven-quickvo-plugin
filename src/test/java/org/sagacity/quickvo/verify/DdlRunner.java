package org.sagacity.quickvo.verify;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 方言验证辅助:按JDBC执行SQL文件(语句以;结尾分隔,--为行注释),单条失败打印后继续(容忍DROP不存在)
 */
public class DdlRunner {
	public static void main(String[] args) throws Exception {
		Class.forName(args[0]);
		int failed = 0;
		try (Connection conn = DriverManager.getConnection(args[1], args[2], args[3])) {
			String sql = Files.readString(Path.of(args[4]));
			for (String part : sql.split(";\\s*[\\r\\n]")) {
				String stmt = part.replaceAll("(?m)^\\s*--.*$", "").trim();
				if (stmt.isEmpty()) {
					continue;
				}
				String head = stmt.substring(0, Math.min(60, stmt.length())).replace('\n', ' ').replace('\r', ' ');
				try (Statement st = conn.createStatement()) {
					st.execute(stmt);
					System.out.println("OK   " + head);
				} catch (SQLException e) {
					failed++;
					String msg = e.getMessage();
					SQLException next = e.getNextException() != null ? e.getNextException() : e;
					if (next.getMessage() != null && !next.getMessage().equals(msg)) {
						msg = msg + " | " + next.getMessage();
					}
					System.out.println("FAIL " + head + " ==> " + msg);
				}
			}
		}
		System.out.println("done, failed=" + failed);
	}
}
