package org.sagacity.quickvo.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.quickvo.QuickVoContext;
import org.sagacity.quickvo.config.XmlConfigParser.ParseResult;
import org.sagacity.quickvo.core.PrimaryKeyResolver;
import org.sagacity.quickvo.model.ConfigModel;
import org.sagacity.quickvo.model.ExtraAnnotationConfig;

/**
 * extra-annotations解析与ULID主键策略单测(无数据库依赖)
 */
public class ExtraAnnotationsParserTest {

	// 用target目录而非@TempDir:LoggerUtil会在baseDir创建quickvo.log并被FileHandler锁定,
	// Windows下@TempDir清理会因文件锁失败
	private final Path tempDir = Path.of(System.getProperty("user.dir"),
			"target", "parser-test-" + System.nanoTime());

	private ConfigModel parse(String xml) throws Exception {
		Files.createDirectories(tempDir);
		Path config = tempDir.resolve("quickvo.xml");
		Files.writeString(config, xml);
		QuickVoContext context = new QuickVoContext(tempDir, config.toString());
		ParseResult result = XmlConfigParser.parse(context);
		return result.config();
	}

	@Test
	public void shouldParseExtraAnnotations() throws Exception {
		ConfigModel config = parse("""
				<?xml version="1.0" encoding="UTF-8"?>
				<quickvo xmlns="http://www.sagframe.com/schema/quickvo">
				<datasource name="d" url="jdbc:x" driver="x.X" username="u" password="p"/>
				<tasks dist="target/g"><task active="true" include="^A" datasource="d">
				<entity package="com.a" substr="A" name="#{subName}"/></task></tasks>
				<extra-annotations>
				<table name="SAG_ORDER_\\w+">
				<class-annotation>@org.sagacity.sqltoy.config.annotation.Sharding(fields="orderId")</class-annotation>
				<field-annotation column="DATA_VERSION">@org.sagacity.sqltoy.config.annotation.DataVersion</field-annotation>
				<imports value="org.sagacity.sqltoy.config.annotation.Sharding,org.sagacity.sqltoy.config.annotation.DataVersion"/>
				</table>
				</extra-annotations>
				</quickvo>
				""");
		List<ExtraAnnotationConfig> configs = config.getExtraAnnotationList();
		assertEquals(1, configs.size());
		ExtraAnnotationConfig extra = configs.get(0);
		assertEquals("SAG_ORDER_\\w+", extra.getTableName());
		assertEquals(1, extra.getClassAnnotations().size());
		assertTrue(extra.getClassAnnotations().get(0).contains("Sharding"));
		// 字段注解key做列名归一
		assertEquals(1, extra.getFieldAnnotations().size());
		assertTrue(extra.getFieldAnnotations().containsKey("dataversion"));
		assertEquals(2, extra.getImports().size());
	}

	@Test
	public void shouldParseUlidGeneratorStrategy() throws Exception {
		ConfigModel config = parse("""
				<?xml version="1.0" encoding="UTF-8"?>
				<quickvo xmlns="http://www.sagframe.com/schema/quickvo">
				<datasource name="d" url="jdbc:x" driver="x.X" username="u" password="p"/>
				<tasks dist="target/g"><task active="true" include="^A" datasource="d">
				<entity package="com.a" substr="A" name="#{subName}"/></task></tasks>
				<primary-key><table name="A" strategy="generator" generator="ulid"/></primary-key>
				</quickvo>
				""");
		// 解析器保留原始名,类名映射在PrimaryKeyResolver.applyStrategy中完成
		assertEquals("ulid", config.getPkGeneratorStrategy().get(0).getGenerator());
		// resolver映射到sqltoy的ULIDGenerator实现
		List<org.sagacity.quickvo.model.QuickColMeta> cols = new java.util.ArrayList<>();
		org.sagacity.quickvo.model.QuickColMeta col = new org.sagacity.quickvo.model.QuickColMeta();
		col.setColName("ID");
		col.setDataType("varchar");
		col.setPrecision(32);
		col.setResultType("String");
		col.setAutoIncrement("false");
		col.setPrimaryKey(true);
		cols.add(col);
		PrimaryKeyResolver.applyStrategy(cols, true, false, config.getPkGeneratorStrategy().get(0), "A",
				new java.util.ArrayList<>());
		assertEquals(PrimaryKeyResolver.PK_ULID_GENERATOR, col.getGenerator());
	}
}
