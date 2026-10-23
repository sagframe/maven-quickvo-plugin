package org.sagacity.quickvo.datasource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.sagacity.quickvo.typemapping.DefaultTypeMappings;
import org.sagacity.quickvo.typemapping.PostgresTypeMappings;

/**
 * 数据库类型识别单测:产品名→类型、版本折叠、方言名折叠(类型映射第4列匹配依赖)
 */
public class DbTypeTest {

	@Test
	public void resolveShouldIdentifyMainstreamDatabases() {
		assertEquals(DbType.MYSQL, DbType.resolve("MySQL", 9));
		assertEquals(DbType.MYSQL57, DbType.resolve("MySQL", 5));
		// mariadb按mysql处理
		assertEquals(DbType.MYSQL, DbType.resolve("MariaDB", 11));
		assertEquals(DbType.ORACLE, DbType.resolve("Oracle", 19));
		assertEquals(DbType.ORACLE11, DbType.resolve("Oracle", 11));
		assertEquals(DbType.POSTGRESQL14, DbType.resolve("PostgreSQL", 14));
		assertEquals(DbType.POSTGRESQL, DbType.resolve("PostgreSQL", 16));
		// greenplum按postgresql处理
		assertEquals(DbType.POSTGRESQL, DbType.resolve("Greenplum", 6));
		assertEquals(DbType.SQLSERVER, DbType.resolve("Microsoft SQL Server", 16));
		assertEquals(DbType.CLICKHOUSE, DbType.resolve("ClickHouse", 24));
		assertEquals(DbType.DM, DbType.resolve("DM DBMS", 8));
		assertEquals(DbType.KINGBASE, DbType.resolve("KingbaseES", 9));
		// zenith为gaussdb的历史产品名
		assertEquals(DbType.GAUSSDB, DbType.resolve("zenith", 0));
		assertEquals(DbType.UNDEFINE, DbType.resolve("UnknownDB", 1));
		assertEquals(DbType.UNDEFINE, DbType.resolve(null, 1));
	}

	@Test
	public void versionProbeFailureShouldNotFoldToLegacyVersion() {
		// 主版本探测失败(-1/0)不得误折叠为旧版本(按当前版本处理,与URL路径foldVersion守卫一致)
		assertEquals(DbType.ORACLE, DbType.resolve("Oracle", -1));
		assertEquals(DbType.ORACLE, DbType.resolve("Oracle", 0));
		assertEquals(DbType.MYSQL, DbType.resolve("MySQL", -1));
		assertEquals(DbType.MYSQL, DbType.resolve("MariaDB", -1));
		// 已知旧版本折叠不受影响
		assertEquals(DbType.ORACLE11, DbType.resolve("Oracle", 11));
		assertEquals(DbType.MYSQL57, DbType.resolve("MySQL", 5));
	}

	@Test
	public void dialectNameShouldFoldVersionedTypes() {
		// 类型映射表第4列的方言名匹配:版本差异型必须折叠为基础方言名(历史行为)
		assertEquals("oracle", DbType.ORACLE11.dialectName());
		assertEquals("oracle", DbType.ORACLE.dialectName());
		assertEquals("mysql", DbType.MYSQL57.dialectName());
		assertEquals("mysql", DbType.MYSQL.dialectName());
		assertEquals("postgresql", DbType.POSTGRESQL14.dialectName());
		assertEquals("mogdb", DbType.MOGDB.dialectName());
		// 新增库方言名与sqltoy的Dialect常量一致(type-mapping第4列跨项目对齐)
		assertEquals("highgo", DbType.HIGHGO.dialectName());
	}

	@Test
	public void typeMappingArraysShouldFollowDialect() {
		// pg系(含mogdb)用postgres映射,oracle用oracle映射(oracle的DATE映射LocalDateTime)
		assertEquals(PostgresTypeMappings.jdbcTypeMapping, DbType.MOGDB.jdbcTypeMapping());
		String[] oracleDate = TypeMappingArrayFirst("DATE", DbType.ORACLE.jdbcTypeMapping());
		assertEquals("LocalDateTime", oracleDate[1]);
		assertEquals(DefaultTypeMappings.jdbcTypeMapping, DbType.MYSQL.jdbcTypeMapping());
		// highgo(PG12内核)归pg血缘:元数据走pg系统表,类型映射用postgres表
		assertTrue(DbType.HIGHGO.isPgFamilyMetadata());
		assertEquals(PostgresTypeMappings.jdbcTypeMapping, DbType.HIGHGO.jdbcTypeMapping());
		assertEquals(PostgresTypeMappings.jdbcAry, DbType.HIGHGO.jdbcTypeAry());
		// kingbase(V9,PG内核)2026-10-13对齐sqltoy归pg血缘(真库有pg_partitioned_table,
		// 无openGauss系pg_partition):元数据走pg系统表,类型映射用postgres表
		assertTrue(DbType.KINGBASE.isPgFamilyMetadata());
		assertEquals(PostgresTypeMappings.jdbcTypeMapping, DbType.KINGBASE.jdbcTypeMapping());
		assertEquals(PostgresTypeMappings.jdbcAry, DbType.KINGBASE.jdbcTypeAry());
	}

	private String[] TypeMappingArrayFirst(String nativeType, String[][] mapping) {
		for (String[] row : mapping) {
			if (row[0].equalsIgnoreCase(nativeType)) {
				return row;
			}
		}
		return new String[0];
	}
	@Test
	public void urlSchemaShouldOverrideMisreportedProductName() {
		// openGauss 5.0实测上报PostgreSQL 9.2.4,URL特征纠正
		assertEquals(DbType.OPENGAUSS,
				DbType.resolve("jdbc:opengauss://localhost:5432/postgres", "PostgreSQL", 9));
		// 达梦compatibleMode=oracle时产品名伪装Oracle,URL特征纠正
		assertEquals(DbType.DM, DbType.resolve("jdbc:dm://localhost:5236?schema=SYSDBA", "Oracle", 11));
		// OceanBase产品名"MySQL 5.7.25-OceanBase"会被mysql分支先命中,URL特征纠正
		assertEquals(DbType.OCEANBASE, DbType.resolve("jdbc:oceanbase://localhost:2881/test", "MySQL", 5));
		// 国产PG系/金仓/其它schema特征
		assertEquals(DbType.VASTBASE, DbType.resolve("jdbc:vastbase://localhost:5436/postgres", "PostgreSQL", 13));
		assertEquals(DbType.MOGDB, DbType.resolve("jdbc:mogdb://localhost:5432/postgres", "PostgreSQL", 9));
		assertEquals(DbType.KINGBASE, DbType.resolve("jdbc:kingbase8://localhost:54321/kingbase", "PostgreSQL", 12));
		assertEquals(DbType.GAUSSDB, DbType.resolve("jdbc:gaussdb://localhost:5432/postgres", "PostgreSQL", 9));
		// 瀚高官方驱动URL形态jdbc:highgo://(产品名伪装vanilla PostgreSQL,仅URL特征可识别,PG12内核)
		assertEquals(DbType.HIGHGO, DbType.resolve("jdbc:highgo://localhost:5867/highgo", "PostgreSQL", 12));
		// URL命中后版本折叠依旧生效
		assertEquals(DbType.ORACLE11, DbType.resolve("jdbc:oracle:thin:@//db:1521/XE", "Oracle", 11));
		assertEquals(DbType.ORACLE, DbType.resolve("jdbc:oracle:thin:@//db:1521/XE", "Oracle", 19));
		assertEquals(DbType.MYSQL57, DbType.resolve("jdbc:mysql://localhost:3306/db", "MySQL", 5));
	}

	@Test
	public void urlMissShouldFallBackToProductName() {
		// URL无特征映射或非jdbc前缀时回退产品名链
		assertEquals(DbType.MYSQL, DbType.resolve("jdbc:mysql://localhost:3307/db?x=y", "MySQL", 9));
		assertEquals(DbType.UNDEFINE, DbType.resolve("jdbc:unknown://localhost/db", "Whatever", 1));
		assertEquals(DbType.MYSQL, DbType.resolve(null, "MySQL", 9));
		assertEquals(DbType.POSTGRESQL14, DbType.resolve("jdbc:postgresql://localhost:5433/postgres", "PostgreSQL", 14));
		assertEquals(DbType.POSTGRESQL, DbType.resolve("jdbc:postgresql://localhost:5433/postgres", "PostgreSQL", 16));
	}
}
