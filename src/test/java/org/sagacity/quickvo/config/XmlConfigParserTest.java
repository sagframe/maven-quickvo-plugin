package org.sagacity.quickvo.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.quickvo.QuickVoContext;
import org.sagacity.quickvo.config.XmlConfigParser.ParseResult;
import org.sagacity.quickvo.model.CascadeModel;
import org.sagacity.quickvo.model.ConfigModel;
import org.sagacity.quickvo.model.PrimaryKeyStrategy;
import org.sagacity.quickvo.model.QuickModel;

/**
 * quickvo.xml解析单测(无需数据库):以golden配置为样本验证task/主键策略/级联解析语义
 */
public class XmlConfigParserTest {

	private ConfigModel parseGoldenConfig(String database) throws Exception {
		Path projectDir = Path.of(System.getProperty("user.dir"));
		QuickVoContext context = new QuickVoContext(projectDir,
				projectDir.resolve("src/test/resources/golden/quickvo-" + database + ".xml").toString());
		ParseResult result = XmlConfigParser.parse(context);
		return result.config();
	}

	// 用target目录而非@TempDir:LoggerUtil会在baseDir创建quickvo.log并被FileHandler锁定,
	// Windows下@TempDir清理会因文件锁失败
	private final Path tempDir = Path.of(System.getProperty("user.dir"),
			"target", "parser-test-" + System.nanoTime());

	private ConfigModel parse(String xml) throws Exception {
		return parseResult(xml).config();
	}

	private ParseResult parseResult(String xml) throws Exception {
		Files.createDirectories(tempDir);
		Path config = tempDir.resolve("quickvo.xml");
		Files.writeString(config, xml);
		QuickVoContext context = new QuickVoContext(tempDir, config.toString());
		return XmlConfigParser.parse(context);
	}

	@Test
	public void shouldParseTasksWithAttributes() throws Exception {
		ConfigModel config = parseGoldenConfig("mysql");
		assertEquals(2, config.getTasks().size());
		QuickModel task1 = config.getTasks().get(0);
		assertEquals("(?i)^QUICKVO_DEMO_\\w+", task1.getIncludeTables());
		assertEquals("demo", task1.getDataSource());
		assertEquals("swagger-v3", task1.getApiDoc());
		assertTrue(task1.isHasEntity());
		assertTrue(task1.isHasVO());
		assertTrue(task1.isHasAbstractEntity());
		assertEquals("quickvo-test", task1.getAuthor());
		// lombok任务:entity单层+lombok链式
		QuickModel task2 = config.getTasks().get(1);
		assertTrue(task2.isEntityLombok());
		assertTrue(task2.isEntityLombokChain());
		// lombok强制关闭抽象类
		assertTrue(!task2.isHasAbstractEntity());
		assertTrue(!task2.isHasVO());
	}

	@Test
	public void shouldParsePrimaryKeyStrategiesAndCascade() throws Exception {
		ConfigModel config = parseGoldenConfig("mysql");
		List<PrimaryKeyStrategy> strategies = config.getPkGeneratorStrategy();
		assertEquals(2, strategies.size());
		assertEquals("QUICKVO_DEMO_STAFF", strategies.get(0).getName());
		assertEquals("generator", strategies.get(0).getStrategy());
		assertEquals(PrimaryKeyStrategy.class, strategies.get(0).getClass());
		List<CascadeModel> cascades = config.getCascadeConfig();
		assertEquals(1, cascades.size());
		assertEquals("QUICKVO_DEMO_STAFF_LOG", cascades.get(0).getTableName());
		assertEquals("delete", cascades.get(0).getUpdateSql());
		assertEquals("STATUS=1", cascades.get(0).getLoad());
		// golden配置显式声明了 delete="true"(其本意就是级联删除)
		assertEquals(Boolean.TRUE, cascades.get(0).getDelete());
	}

	@Test
	public void cascadeDeleteShouldStayUnsetWhenAbsent() throws Exception {
		// 未配置delete时为null:运行期跟随外键ON DELETE规则,显式true/false才覆盖
		String xml = """
				<quickvo xmlns="http://www.sagframe.com/schema/quickvo">
				<datasource name="d" url="jdbc:h2:mem:t" driver="org.h2.Driver" username="sa" password=""/>
				<tasks><task active="true" include=".*"><vo package="com.t"/></task></tasks>
				<cascade><table name="T_CHILD" load="STATUS=1"/></cascade>
				</quickvo>
				""";
		ConfigModel config = parse(xml);
		assertEquals(1, config.getCascadeConfig().size());
		assertNull(config.getCascadeConfig().get(0).getDelete());
	}

	@Test
	public void shouldParseTypeMappingWithPrecisionRange() throws Exception {
		ConfigModel config = parseGoldenConfig("mysql");
		// 默认两条(NUMBER按精度区分Integer/Long)+配置一条
		assertTrue(config.getTypeMapping().size() >= 3);
		var custom = config.getTypeMapping().get(config.getTypeMapping().size() - 1);
		assertEquals(1, custom.getPrecisionMin());
		assertEquals(8, custom.getPrecisionMax());
		assertEquals("java.math.BigDecimal".equals(custom.getJavaType()) ? "BigDecimal" : custom.getResultType(),
				custom.getResultType());
	}

	@Test
	public void dataVersionShouldParseAndMatchByNameOrRegex() throws Exception {
		String xml = """
				<quickvo xmlns="http://www.sagframe.com/schema/quickvo">
				<datasource name="d" url="jdbc:h2:mem:t" driver="org.h2.Driver" username="sa" password=""/>
				<tasks><task active="true" include=".*"><vo package="com.t" substr="T_"/></task></tasks>
				<data-version>
				<table name="sag_order" column="LAST_UPDATE"/>
				<table name="SYS_\\w+" column="DATA_VERSION" start-date="true"/>
				<table name="SYS_LOG" column="OTHER"/>
				</data-version>
				</quickvo>
				""";
		ConfigModel config = parse(xml);
		// 纯名称:字面精确匹配忽略大小写
		var byPlain = config.getDataVersion("SAG_ORDER");
		assertTrue(byPlain != null && byPlain.startDate() == false);
		// 正则元字符:按正则完整匹配
		var byRegex = config.getDataVersion("SYS_CONFIG");
		assertTrue(byRegex != null && byRegex.column().equals("DATA_VERSION") && byRegex.startDate());
		// 未命中返回null
		assertTrue(config.getDataVersion("OTHER_TABLE") == null);
	}

	@Test
	public void malformedPrecisionShouldFailFast() throws Exception {
		// 单点误写"1.8"历史行为静默解析为[1](精确等于1),必须改为显式报错
		String xml = """
				<quickvo xmlns="http://www.sagframe.com/schema/quickvo">
				<datasource name="d" url="jdbc:h2:mem:t" driver="org.h2.Driver" username="sa" password=""/>
				<tasks><task active="true" include=".*"><vo package="com.t" substr="T_"/></task></tasks>
				<type-mapping><sql-type native-types="NUMBER" precision="1.8" java-type="Integer"/></type-mapping>
				</quickvo>
				""";
		assertThrows(IllegalArgumentException.class, () -> parse(xml));
	}

	@Test
	public void openEndedRangeAndSingleValueShouldParse() throws Exception {
		// "10.."=≥10不限上限;单值=精确等于
		String xml = """
				<quickvo xmlns="http://www.sagframe.com/schema/quickvo">
				<datasource name="d" url="jdbc:h2:mem:t" driver="org.h2.Driver" username="sa" password=""/>
				<tasks><task active="true" include=".*"><vo package="com.t" substr="T_"/></task></tasks>
				<type-mapping>
				<sql-type native-types="NUMBER" precision="10.." java-type="Long"/>
				<sql-type native-types="DECIMAL" scale="2" java-type="Integer"/>
				</type-mapping>
				</quickvo>
				""";
		ConfigModel config = parse(xml);
		// 默认映射排在列表前部,自定义条目按配置顺序追加在尾部
		var mappings = config.getTypeMapping();
		var openEnded = mappings.get(mappings.size() - 2);
		var single = mappings.get(mappings.size() - 1);
		assertEquals(10, openEnded.getPrecisionMin());
		assertEquals(-1, openEnded.getPrecisionMax());
		assertEquals(2, single.getScaleMin());
		assertEquals(2, single.getScaleMax());
		// 收尾还原语言设置,避免污染同JVM的后续测试
		org.sagacity.quickvo.utils.Msg.setLanguage(null);
	}

	@Test
	public void logLanguageAttributeShouldApply() throws Exception {
		// tasks上log-language属性(2.0新增,唯一写法):强制英文,即使系统Locale为中文也生效
		String xmlEn = """
				<quickvo xmlns="http://www.sagframe.com/schema/quickvo">
				<datasource name="d" url="jdbc:h2:mem:t" driver="org.h2.Driver" username="sa" password=""/>
				<tasks log-language="en"><task active="true" include=".*"><vo package="com.t" substr="T_"/></task></tasks>
				</quickvo>
				""";
		parse(xmlEn);
		assertTrue(!org.sagacity.quickvo.utils.Msg.isChinese());
		String xmlZh = """
				<quickvo xmlns="http://www.sagframe.com/schema/quickvo">
				<datasource name="d" url="jdbc:h2:mem:t" driver="org.h2.Driver" username="sa" password=""/>
				<tasks log-language="zh"><task active="true" include=".*"><vo package="com.t" substr="T_"/></task></tasks>
				</quickvo>
				""";
		parse(xmlZh);
		assertTrue(org.sagacity.quickvo.utils.Msg.isChinese());
		org.sagacity.quickvo.utils.Msg.setLanguage(null);
	}

	/** 最小可用配置(数据源+单任务) */
	private static final String MINIMAL_XML = """
			<quickvo xmlns="http://www.sagframe.com/schema/quickvo">
			<datasource name="demo" url="jdbc:h2:mem:t" driver="org.h2.Driver" username="sa" password=""/>
			<tasks><task active="true" include=".*" datasource="demo"><vo package="com.t"/></task></tasks>
			</quickvo>
			""";

	@Test
	public void classpathConfigShouldFallBackToProjectResources() throws Exception {
		// maven插件执行期TCCL是插件自身realm,工程的src/main/resources不在其中:
		// 默认值classpath:quickvo.xml必须回退到工程资源目录,否则不显式配置configFile的用法直接失败
		Path resources = tempDir.resolve("src/main/resources");
		Files.createDirectories(resources);
		Files.writeString(resources.resolve("quickvo-fallback-probe.xml"), MINIMAL_XML);
		QuickVoContext context = new QuickVoContext(tempDir, "classpath:quickvo-fallback-probe.xml");
		ParseResult result = XmlConfigParser.parse(context);
		assertEquals(1, result.config().getTasks().size());
		assertEquals("demo", result.config().getTasks().get(0).getDataSource());
	}

	@Test
	public void classpathConfigShouldFallBackToBaseDir() throws Exception {
		// 配置文件放在执行根路径(常见于项目根下直接执行mvn quickvo:quickvo)
		Files.createDirectories(tempDir);
		Files.writeString(tempDir.resolve("quickvo-root-probe.xml"), MINIMAL_XML);
		QuickVoContext context = new QuickVoContext(tempDir, "classpath:quickvo-root-probe.xml");
		assertEquals(1, XmlConfigParser.parse(context).config().getTasks().size());
	}

	@Test
	public void doctypeShouldBeRejected() {
		// XXE加固:配置文件携带DOCTYPE/实体声明应被直接拒绝,而非尝试解析外部实体
		String xml = """
				<!DOCTYPE quickvo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
				<quickvo xmlns="http://www.sagframe.com/schema/quickvo">
				<datasource name="demo" url="jdbc:h2:mem:t" driver="org.h2.Driver" username="sa" password=""/>
				<tasks><task active="true" include=".*" datasource="demo"><entity package="p"/></task></tasks>
				</quickvo>
				""";
		assertThrows(Exception.class, () -> parse(xml));
	}

	@Test
	public void invalidTableRegexShouldFailWithGuidance() {
		// 通配符风格"*_LOG"不是合法正则:应在解析期报错并给出写法指引,而非提取表时裸PatternSyntaxException
		IllegalArgumentException excludeError = assertThrows(IllegalArgumentException.class,
				() -> parse(taskXml(" include=\".*\" exclude=\"*_LOG\"")));
		assertTrue(excludeError.getMessage().contains(".*_LOG"), excludeError.getMessage());
		assertTrue(excludeError.getMessage().contains("exclude"), excludeError.getMessage());
		IllegalArgumentException includeError = assertThrows(IllegalArgumentException.class,
				() -> parse(taskXml(" include=\"(STAFF\"")));
		assertTrue(includeError.getMessage().contains("include"), includeError.getMessage());
	}

	private String taskXml(String taskAttrs) {
		return """
				<quickvo xmlns="http://www.sagframe.com/schema/quickvo">
				<datasource name="demo" url="jdbc:h2:mem:t" driver="org.h2.Driver" username="sa" password=""/>
				<tasks><task active="true" datasource="demo"%s><entity package="p"/></task></tasks>
				</quickvo>
				""".formatted(taskAttrs);
	}

	@Test
	public void placeholdersShouldApplyToTaskAndDatasourceNames() throws Exception {
		// L13:author/datasource/api-doc/fields-rid-prefix与<datasource name>此前不走${}解析,
		// 导致<task author="${proj.author}">原样输出到生成物、任务与数据源注册名对不上
		String xml = """
				<quickvo xmlns="http://www.sagframe.com/schema/quickvo">
				<property name="proj.author" value="zhongxuchen"/>
				<property name="proj.ds" value="demo"/>
				<property name="proj.doc" value="v3"/>
				<property name="proj.prefix" value="F_"/>
				<property name="proj.pkg" value="com.probe.entity"/>
				<datasource name="${proj.ds}" url="jdbc:h2:mem:t" driver="org.h2.Driver" username="sa" password=""/>
				<tasks><task active="true" include=".*" datasource="${proj.ds}" author="${proj.author}"
				      api-doc="${proj.doc}" fields-rid-prefix="${proj.prefix}">
				<entity package="${proj.pkg}"/>
				</task></tasks>
				</quickvo>
				""";
		ParseResult result = parseResult(xml);
		QuickModel task = result.config().getTasks().get(0);
		assertEquals("zhongxuchen", task.getAuthor());
		assertEquals("demo", task.getDataSource());
		// api-doc别名归一位于占位符解析之后,同样生效
		assertEquals("swagger-v3", task.getApiDoc());
		// fields-rid-prefix按列比较惯例小写化
		assertEquals("f_", task.getFieldRidPrefix());
		assertEquals("com.probe.entity", task.getEntityPackage());
		// 数据源注册名与任务引用名同步解析
		assertTrue(result.dataSources().contains("demo"));
		assertTrue(!result.dataSources().contains("${proj.ds}"));
	}

	@Test
	public void logLanguageResetsToAutoWhenAbsent() throws Exception {
		// 未配置时重置为自动检测(清除同JVM前一用例的遗留覆盖)
		String xmlNone = """
				<quickvo xmlns="http://www.sagframe.com/schema/quickvo">
				<datasource name="d" url="jdbc:h2:mem:t" driver="org.h2.Driver" username="sa" password=""/>
				<tasks><task active="true" include=".*"><vo package="com.t" substr="T_"/></task></tasks>
				</quickvo>
				""";
		parse(xmlNone);
		assertEquals("zh".equalsIgnoreCase(java.util.Locale.getDefault().getLanguage()),
				org.sagacity.quickvo.utils.Msg.isChinese());
		org.sagacity.quickvo.utils.Msg.setLanguage(null);
	}
}
