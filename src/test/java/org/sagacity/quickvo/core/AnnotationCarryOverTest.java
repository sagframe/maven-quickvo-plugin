package org.sagacity.quickvo.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.sagacity.quickvo.core.AnnotationCarryOver.CarryOverResult;

/**
 * 重新生成时的注解继承解析单测:白名单/类级/字段级(含带初始化字段)/级联/BusinessId/import
 */
public class AnnotationCarryOverTest {

	private static final String SAMPLE = """
import org.sagacity.sqltoy.config.annotation.Tenant;
import org.sagacity.sqltoy.config.annotation.Partition;
import org.sagacity.sqltoy.config.annotation.OneToMany;

@org.sagacity.sqltoy.config.annotation.DataVersion(field = "entryDate")
public abstract class AbstractStaff implements Serializable {
\t@Tenant(field = "staffId")
\t@org.sagacity.sqltoy.config.annotation.BusinessId(generator = "redis", length = 12)
\tprotected String staffId;

\t@SqlToyFieldAlias("staff_code")
\tprotected String staffCode;

\t@OneToMany(fields={"staffId"},mappedFields={"staffId"},load="DELETED=0",delete=true)
\tprotected List<Order> orders=new ArrayList<Order>();
}
""";

	@Test
	public void shouldParseClassLevelAnnotations() {
		CarryOverResult result = AnnotationCarryOver.parse(SAMPLE);
		assertEquals(1, result.classAnnotations.size());
		assertTrue(result.classAnnotations.get(0).contains("DataVersion"));
	}

	@Test
	public void shouldParseFieldAnnotationsIncludingInitializedFields() {
		CarryOverResult result = AnnotationCarryOver.parse(SAMPLE);
		// staffId两条/staffcode一条/orders一条(带=new ArrayList初始化的字段)
		assertEquals(2, result.fieldAnnotations.get("staffid").size());
		assertEquals(1, result.fieldAnnotations.get("staffcode").size());
		assertTrue(result.fieldAnnotations.containsKey("orders"));
	}

	@Test
	public void shouldExtractOneToManyAndBusinessId() {
		CarryOverResult result = AnnotationCarryOver.parse(SAMPLE);
		assertTrue(result.oneToManyByField.get("orders").contains("load=\"DELETED=0\""));
		assertTrue(result.businessIdByField.get("staffid").contains("BusinessId"));
	}

	@Test
	public void shouldCollectWhitelistImportsOnly() {
		CarryOverResult result = AnnotationCarryOver.parse(SAMPLE);
		// Partition不在白名单,Tenant/OneToMany在
		assertTrue(result.imports.contains("org.sagacity.sqltoy.config.annotation.Tenant"));
		assertFalse(result.imports.toString().contains("Partition"));
	}

	@Test
	public void shouldReturnNullForBlankContent() {
		assertEquals(null, AnnotationCarryOver.parse(""));
		assertEquals(null, AnnotationCarryOver.parse(null));
	}

	@Test
	public void shouldCarryOverMultilineFoldedAnnotation() {
		// 注解参数折行书写:两条续行必须完整合并继承,而非只取首行截断
		String content = "\t@Translate(key=\"dictName\"\r\n\t,type=Dict.class)\r\n\tprotected String status;\r\n";
		CarryOverResult result = AnnotationCarryOver.parse(content);
		List<String> annotations = result.fieldAnnotations.get("status");
		assertEquals(1, annotations.size());
		assertEquals("@Translate(key=\"dictName\",type=Dict.class)", annotations.get(0));
	}

	@Test
	public void shouldCarryOverFoldedContainerAnnotationWithMemberLeadingLines() {
		// 容器注解折行且成员行以@开头(IDE格式化常见):必须整体合并为一条完整注解,
		// 切分会产生"括号未闭合+多余})"的两条残缺注解,继承后无法编译
		String content = "\t@Translates({ @Translate(cacheName=\"staffDict\", keyField=\"typeId\"),\r\n"
				+ "\t\t@Translate(cacheName=\"sexDict\", keyField=\"sex\") })\r\n"
				+ "\tprotected String typeName;\r\n";
		CarryOverResult result = AnnotationCarryOver.parse(content);
		List<String> annotations = result.fieldAnnotations.get("typename");
		assertEquals(1, annotations.size());
		assertEquals("@Translates({ @Translate(cacheName=\"staffDict\", keyField=\"typeId\"),"
				+ "@Translate(cacheName=\"sexDict\", keyField=\"sex\") })", annotations.get(0));
	}

	@Test
	public void shouldCarryOverFoldedClassLevelSecureConfig() {
		// 类级容器注解折行(@Secure成员行以@开头)同样整体继承
		String content = "@SecureConfig(secures = {\r\n\t\t@Secure(field = \"staffName\") })\r\n"
				+ "public abstract class AbstractStaff implements Serializable {\r\n}\r\n";
		CarryOverResult result = AnnotationCarryOver.parse(content);
		assertEquals(1, result.classAnnotations.size());
		assertEquals("@SecureConfig(secures = {@Secure(field = \"staffName\") })", result.classAnnotations.get(0));
	}

	@Test
	public void shouldNotMergeBalancedAnnotations() {
		// 已闭合的容器注解后跟行首@的新注解:不得因合并逻辑过度吞并
		String content = "\t@Translates({ @Translate(cacheName=\"a\") })\r\n\t@Tenant\r\n\tprotected String status;\r\n";
		CarryOverResult result = AnnotationCarryOver.parse(content);
		assertEquals(2, result.fieldAnnotations.get("status").size());
		assertTrue(result.fieldAnnotations.get("status").get(0).startsWith("@Translates"));
		assertTrue(result.fieldAnnotations.get("status").get(1).startsWith("@Tenant"));
	}

	@Test
	public void shouldIgnoreParenthesesInsideStringLiteral() {
		// 字符串字面量内的括号不得干扰闭合判断(如cacheName="dict(x)")
		String content = "\t@Translate(cacheName=\"dict(x)\",\r\n\t\tkeyField=\"typeId\")\r\n\tprotected String typeName;\r\n";
		CarryOverResult result = AnnotationCarryOver.parse(content);
		List<String> annotations = result.fieldAnnotations.get("typename");
		assertEquals(1, annotations.size());
		assertEquals("@Translate(cacheName=\"dict(x)\",keyField=\"typeId\")", annotations.get(0));
	}

	@Test
	public void shouldTolerateSpaceIndentFromIdeReformat() {
		// IDE重排为4空格缩进后继承不应失效
		String content = "    @DataVersion(field=\"lastUpdate\")\n    private Date lastUpdate;\n";
		CarryOverResult result = AnnotationCarryOver.parse(content);
		assertEquals(1, result.fieldAnnotations.get("lastupdate").size());
	}

	@Test
	public void shouldTolerateColumnCommentWithSemicolon() {
		// @Column参数中含分号(列备注"状态;1有效")时字段块匹配不受影响,白名单注解正常继承
		String content = "\t@Column(name=\"STATUS\",comment=\"状态;1有效\")\r\n\t@Tenant\r\n\tprotected Integer status;\r\n";
		CarryOverResult result = AnnotationCarryOver.parse(content);
		assertEquals(1, result.fieldAnnotations.get("status").size());
		assertTrue(result.fieldAnnotations.get("status").get(0).startsWith("@Tenant"));
	}

	@Test
	public void shouldCarryOverAnnotationsWithInterleavedComments() {
		// M1:注解之间夹单行//注释时,前后手工注解都不应丢失
		String c1 = "\t@Tenant(field=\"x\")\r\n\t// TODO verify\r\n\t@Translate(cacheName=\"dict\")\r\n\tprotected String status;\r\n";
		CarryOverResult r1 = AnnotationCarryOver.parse(c1);
		assertEquals(2, r1.fieldAnnotations.get("status").size());
		assertTrue(r1.fieldAnnotations.get("status").get(0).startsWith("@Tenant"));
		assertTrue(r1.fieldAnnotations.get("status").get(1).startsWith("@Translate"));

		// M1:注解与字段声明之间夹单行块注释/** */时,注解不应丢失,且注释不被并入注解原文
		String c2 = "\t@DataVersion(field=\"d\")\n\t/** remark */\n\tprivate Date d;\n";
		CarryOverResult r2 = AnnotationCarryOver.parse(c2);
		assertEquals(1, r2.fieldAnnotations.get("d").size());
		assertEquals("@DataVersion(field=\"d\")", r2.fieldAnnotations.get("d").get(0));
	}
}
