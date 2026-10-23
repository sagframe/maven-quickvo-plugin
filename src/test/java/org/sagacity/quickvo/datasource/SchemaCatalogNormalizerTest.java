package org.sagacity.quickvo.datasource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * schema/catalog按数据库类型容错归一单测:位置互换纠正、大小写探测纠正、双值库保持
 */
public class SchemaCatalogNormalizerTest {

	@Test
	public void mysqlShouldMoveMisplacedSchemaToCatalog() {
		// mysql用户把库名配到schema上(常见错误)→挪到catalog,schema清空
		assertArrayEquals(new String[] { "quickvo_demo", null },
				SchemaCatalogNormalizer.normalize(DbType.MYSQL, null, "quickvo_demo", List.of()));
		// 正确配置不受影响
		assertArrayEquals(new String[] { "quickvo_demo", null },
				SchemaCatalogNormalizer.normalize(DbType.MYSQL, "quickvo_demo", null, List.of()));
		// 两者都配:catalog优先,schema清空(避免mysql驱动歧义)
		assertArrayEquals(new String[] { "quickvo_demo", null },
				SchemaCatalogNormalizer.normalize(DbType.MYSQL, "quickvo_demo", "other", List.of()));
	}

	@Test
	public void oceanbaseMySqlModeShouldNormalizeLikeMySql() {
		// OceanBase(mysql模式)与isMySqlFamily()对齐:库址误配到schema同样纠正(历史遗漏导致误配不生效)
		assertArrayEquals(new String[] { "quickvo_demo", null },
				SchemaCatalogNormalizer.normalize(DbType.OCEANBASE, null, "quickvo_demo", List.of()));
		assertArrayEquals(new String[] { "quickvo_demo", null },
				SchemaCatalogNormalizer.normalize(DbType.OCEANBASE, "quickvo_demo", null, List.of()));
	}

	@Test
	public void oracleShouldUppercaseSchemaAndDropCatalog() {
		// oracle配小写system→大写化(无清单时按惯例);catalog误配→挪为schema
		assertArrayEquals(new String[] { null, "SYSTEM" },
				SchemaCatalogNormalizer.normalize(DbType.ORACLE, null, "system", List.of()));
		assertArrayEquals(new String[] { null, "SYSTEM" },
				SchemaCatalogNormalizer.normalize(DbType.ORACLE, "system", null, List.of()));
		// 有清单时以清单实际为准
		assertArrayEquals(new String[] { null, "SYSTEM" },
				SchemaCatalogNormalizer.normalize(DbType.ORACLE, null, "system", List.of("SYS", "SYSTEM")));
		// 清单中不存在:保持原样由精准提示引导
		assertArrayEquals(new String[] { null, "NOT_EXIST" },
				SchemaCatalogNormalizer.normalize(DbType.ORACLE, null, "NOT_EXIST", List.of("SYS", "SYSTEM")));
	}

	@Test
	public void postgresShouldCorrectCaseFromAvailableSchemas() {
		// pg配大写QUICKVO_DEMO,库内实际小写→自动纠正
		assertArrayEquals(new String[] { null, "quickvo_demo" },
				SchemaCatalogNormalizer.normalize(DbType.POSTGRESQL, null, "QUICKVO_DEMO",
						List.of("public", "quickvo_demo")));
		// catalog误配→挪为schema并纠正大小写
		assertArrayEquals(new String[] { null, "quickvo_demo" },
				SchemaCatalogNormalizer.normalize(DbType.POSTGRESQL, "QUICKVO_DEMO", null,
						List.of("public", "quickvo_demo")));
		// 大小写不敏感命中多个:放弃纠正
		assertArrayEquals(new String[] { null, "Foo" },
				SchemaCatalogNormalizer.normalize(DbType.POSTGRESQL, null, "Foo", List.of("foo", "FOO")));
		// 国产pg系同样生效
		assertArrayEquals(new String[] { null, "public" },
				SchemaCatalogNormalizer.normalize(DbType.OPENGAUSS, "PUBLIC", null, List.of("public")));
		// highgo(PG12内核)归pg系:catalog误配挪为schema,大小写以库内实际为准
		assertArrayEquals(new String[] { null, "quickvo_demo" },
				SchemaCatalogNormalizer.normalize(DbType.HIGHGO, "QUICKVO_DEMO", null,
						List.of("public", "quickvo_demo")));
	}

	@Test
	public void dualValueDatabasesShouldKeepBoth() {
		// sqlserver双值有效,仅做清单大小写纠正
		assertArrayEquals(new String[] { "master", "dbo" },
				SchemaCatalogNormalizer.normalize(DbType.SQLSERVER, "master", "DBO", List.of("dbo", "guest")));
	}

	@Test
	public void db2ShouldDropCatalogBecauseDriverIgnoresIt() {
		// DB2驱动传非null catalog时getTables/getExportedKeys返回0行(12.1.5实测),
		// 库名已在连接URL中,归一为只保留schema(用户名)
		assertArrayEquals(new String[] { null, "DB2INST1" },
				SchemaCatalogNormalizer.normalize(DbType.DB2, "SQLTOYDB", "db2inst1", List.of("DB2INST1")));
		assertArrayEquals(new String[] { null, "DB2INST1" },
				SchemaCatalogNormalizer.normalize(DbType.DB2, null, "db2inst1", List.of("DB2INST1")));
	}

	@Test
	public void blankValuesShouldNormalizeToNull() {
		assertNull(SchemaCatalogNormalizer.normalize(DbType.MYSQL, "  ", null, List.of())[0]);
		assertNull(SchemaCatalogNormalizer.normalize(DbType.ORACLE, null, "", List.of())[1]);
	}
}
