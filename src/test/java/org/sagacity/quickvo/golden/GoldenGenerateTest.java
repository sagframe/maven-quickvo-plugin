package org.sagacity.quickvo.golden;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * golden基线测试:用demo库表完整跑一遍代码生成,与expected目录逐文件比对。
 * 数据库不可达时自动跳过(本地需启动sqltoy-mysql9/sqltoy-pgvector容器)。
 */
public class GoldenGenerateTest {

	private static final String MYSQL_URL = "jdbc:mysql://localhost:3307/quickvo_demo"
			+ "?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true";
	private static final String PG_URL = "jdbc:postgresql://localhost:5433/postgres";

	/**
	 * 生成产物必须与expected基线一致(规范化换行后逐文件字节比对)
	 */
	@ParameterizedTest
	@ValueSource(strings = { "mysql", "postgresql", "fullmysql", "fullpg" })
	public void generatedFilesMatchGoldenBaseline(String database) throws Exception {
		boolean full = database.startsWith("full");
		String physical = full ? (database.equals("fullmysql") ? "mysql" : "postgresql") : database;
		Assumptions.assumeTrue(databaseReachable(physical), "数据库 " + physical + " 不可达,跳过golden测试");
		// full模式在测试内自建全类型覆盖表
		if (full) {
			applyDdl(physical, projectSqlDir().resolve(
					database.equals("fullmysql") ? "demo-fullcoverage-mysql.sql" : "demo-fullcoverage-postgresql.sql"));
		}
		Path projectDir = Path.of(System.getProperty("user.dir"));
		Path expectedRoot = projectDir.resolve("src/test/resources/golden/expected/" + database);
		Path workRoot = projectDir.resolve("target/golden/verify/" + database);
		deleteRecursively(workRoot);
		Files.createDirectories(workRoot.getParent());

		Path config = projectDir.resolve((full ? "src/test/resources/fullcoverage/quickvo-fullcoverage-"
				: "src/test/resources/golden/quickvo-") + physical + ".xml");
		QuickVoTestRunner.run(config, workRoot);

		Path genRoot = workRoot.resolve("target/golden/gen/" + database);
		assertTrue(Files.isDirectory(genRoot), "生成目录不存在: " + genRoot);
		List<String> mismatches = new ArrayList<>();
		compareDirectories(expectedRoot, genRoot, mismatches);
		assertTrue(mismatches.isEmpty(),
				() -> "golden比对失败(" + database + "):\n" + String.join("\n", mismatches));

		// 二次运行走增量合并路径:不抛异常且产物零变化(视图无区域标记等场景不可中断任务)
		QuickVoTestRunner.run(config, workRoot);
		List<String> rerunMismatches = new ArrayList<>();
		compareDirectories(expectedRoot, genRoot, rerunMismatches);
		assertTrue(rerunMismatches.isEmpty(),
				() -> "二次运行后产物发生变化(" + database + "):\n" + String.join("\n", rerunMismatches));
	}

	private Path projectSqlDir() {
		return Path.of(System.getProperty("user.dir")).resolve("src/test/resources/sql");
	}

	/** 通过JDBC执行DDL建表脚本(语句以;结尾分隔,容忍单条失败) */
	private void applyDdl(String database, Path sqlFile) throws Exception {
		String url = "mysql".equals(database) ? MYSQL_URL : PG_URL;
		String user = "mysql".equals(database) ? "root" : "postgres";
		String sql = Files.readString(sqlFile);
		try (Connection conn = DriverManager.getConnection(url, user, "Sqltoy@2026");
				java.sql.Statement st = conn.createStatement()) {
			for (String part : sql.split(";\\s*[\\r\\n]")) {
				String stmt = part.replaceAll("(?m)^\\s*--.*$", "").trim();
				if (!stmt.isEmpty()) {
					try {
						st.execute(stmt);
					} catch (SQLException tolerated) {
						// DROP不存在等场景容忍
					}
				}
			}
		}
	}

	private boolean databaseReachable(String database) {
		String url = "mysql".equals(database) ? MYSQL_URL : PG_URL;
		String user = "mysql".equals(database) ? "root" : "postgres";
		try (Connection conn = DriverManager.getConnection(url, user, "Sqltoy@2026")) {
			return conn.isValid(2);
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * 递归比对两个目录:文件集合一致且内容(换行规范化后)一致
	 */
	private void compareDirectories(Path expected, Path actual, List<String> mismatches) throws IOException {
		List<Path> expectedFiles = listFiles(expected);
		List<Path> actualFiles = listFiles(actual);
		List<String> expectedNames = expectedFiles.stream()
				.map(p -> expected.relativize(p).toString().replace('\\', '/')).sorted().toList();
		List<String> actualNames = actualFiles.stream()
				.map(p -> actual.relativize(p).toString().replace('\\', '/')).sorted().toList();
		if (!expectedNames.equals(actualNames)) {
			List<String> missing = new ArrayList<>(expectedNames);
			missing.removeAll(actualNames);
			List<String> extra = new ArrayList<>(actualNames);
			extra.removeAll(expectedNames);
			if (!missing.isEmpty()) {
				mismatches.add("缺失文件: " + missing);
			}
			if (!extra.isEmpty()) {
				mismatches.add("多余文件: " + extra);
			}
			return;
		}
		for (Path expectedFile : expectedFiles) {
			String relative = expected.relativize(expectedFile).toString();
			Path actualFile = actual.resolve(relative);
			String expectedContent = Files.readString(expectedFile).replace("\r\n", "\n");
			String actualContent = Files.readString(actualFile).replace("\r\n", "\n");
			if (!expectedContent.equals(actualContent)) {
				mismatches.add("内容不一致: " + relative);
			}
		}
	}

	private List<Path> listFiles(Path root) throws IOException {
		try (Stream<Path> stream = Files.walk(root)) {
			return stream.filter(Files::isRegularFile).toList();
		}
	}

	private void deleteRecursively(Path path) throws IOException {
		if (!Files.exists(path)) {
			return;
		}
		try (Stream<Path> stream = Files.walk(path)) {
			stream.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
				try {
					Files.delete(p);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
		}
	}
}
