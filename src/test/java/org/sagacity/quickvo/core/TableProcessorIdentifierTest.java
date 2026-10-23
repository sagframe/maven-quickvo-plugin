package org.sagacity.quickvo.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.quickvo.QuickVoContext;
import org.sagacity.quickvo.model.QuickColMeta;
import org.sagacity.quickvo.utils.StringUtil;

/**
 * Java字段名合法性校验单测:关键字/非法字符/同表重名应在插件阶段汇总为错误并指明表与列,
 * 让失败发生在生成阶段而不是使用方的mvn compile阶段;合法形态(中文标识符、
 * record/var等受限标识符、含$的标识符)不得误报。校验只报告,不修改字段命名。
 */
public class TableProcessorIdentifierTest {

	// 不用@TempDir:QuickVoContext会在目录内打开quickvo.log的FileHandler,
	// Windows下文件被锁导致TempDir清理失败;统一放target下免清理
	private static QuickVoContext newContext() {
		return new QuickVoContext(Path.of("target"), "quickvo.xml");
	}

	@Test
	public void javaKeywordShouldBeReported() {
		QuickVoContext context = newContext();
		TableProcessor.validateJavaFieldNames(context, "T_PROBE", cols("CLASS", "NEW"));
		assertEquals(2, context.getErrors().size());
		assertTrue(context.getErrors().get(0).contains("T_PROBE"));
		assertTrue(context.getErrors().get(0).contains("CLASS"));
		assertTrue(context.getErrors().get(1).contains("NEW"));
	}

	@Test
	public void illegalIdentifierShouldBeReported() {
		QuickVoContext context = newContext();
		TableProcessor.validateJavaFieldNames(context, "T_KW", cols("A-B", "USER NAME"));
		assertEquals(2, context.getErrors().size());
		assertTrue(context.getErrors().get(0).contains("A-B"));
		assertTrue(context.getErrors().get(1).contains("USER NAME"));
	}

	@Test
	public void emptyFieldNameShouldBeReported() {
		// 列名"_"清洗后字段名称为空,模板会输出"private String ;"
		QuickVoContext context = newContext();
		TableProcessor.validateJavaFieldNames(context, "T_EMPTY", cols("_"));
		assertEquals(1, context.getErrors().size());
	}

	@Test
	public void duplicatedFieldNameShouldBeReported() {
		// 两列都不含下划线时adjustCamelConflict无法消歧,驼峰名完全相同(仅数据库侧大小写不同)即重复字段
		QuickVoContext context = newContext();
		TableProcessor.validateJavaFieldNames(context, "T_DUP", cols("ReciveTime", "reciveTime"));
		assertEquals(1, context.getErrors().size());
		assertTrue(context.getErrors().get(0).contains("reciveTime"));
	}

	@Test
	public void threeWayConflictShouldBeReportedAfterDisambiguation() {
		// adjustCamelConflict只能改名一个成员,三列归一的情况仍会剩下两个同名字段
		QuickVoContext context = newContext();
		List<QuickColMeta> colList = cols("recive_time", "RECIVE_TIME", "reciveTime");
		TableProcessor.adjustCamelConflict(colList, null);
		TableProcessor.validateJavaFieldNames(context, "T_DUP3", colList);
		assertEquals(1, context.getErrors().size());
	}

	@Test
	public void disambiguatedColumnsShouldNotBeReported() {
		// 经adjustCamelConflict消歧(Recive_Time/ReciveTime)后不得误报重名
		QuickVoContext context = newContext();
		List<QuickColMeta> colList = cols("reciveTime", "recive_time");
		TableProcessor.adjustCamelConflict(colList, null);
		TableProcessor.validateJavaFieldNames(context, "T_OK", colList);
		assertFalse(context.hasErrors(), () -> String.join("\n", context.getErrors()));
	}

	@Test
	public void legalNamesShouldNotBeReported() {
		// 中文标识符合法;record/var/sealed/yield是受限标识符而非关键字,作字段名合法
		QuickVoContext context = newContext();
		TableProcessor.validateJavaFieldNames(context, "T_OK",
				cols("用户名", "record", "var", "sealed", "CLASS_NAME", "A$B"));
		assertFalse(context.hasErrors(), () -> String.join("\n", context.getErrors()));
	}

	@Test
	public void fieldKeyMustMatchFreemarkerUncapFirst() throws Exception {
		// 校验键须与模板?uncap_first同算法:U+0130(İ)首列与ID列经模板都输出字段"id",
		// 用String.toLowerCase(ROOT)会得到"i+组合点"形态,与生成物不一致而漏报重复
		QuickVoContext context = newContext();
		List<QuickColMeta> colList = new ArrayList<>();
		QuickColMeta dotted = new QuickColMeta();
		dotted.setColName("İD");
		dotted.setColJavaName("İd");
		colList.add(dotted);
		QuickColMeta plain = new QuickColMeta();
		plain.setColName("ID");
		plain.setColJavaName("Id");
		colList.add(plain);
		// 模板真值:FreeMarker ?uncap_first对"İd"/"Id"均输出"id",两个字段重复
		freemarker.template.Configuration cfg = new freemarker.template.Configuration(
				freemarker.template.Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
		assertEquals(uncap(cfg, "İd"), uncap(cfg, "Id"));
		TableProcessor.validateJavaFieldNames(context, "T_DOT_I", colList);
		assertEquals(1, context.getErrors().size());
	}

	private static String uncap(freemarker.template.Configuration cfg, String s) throws Exception {
		StringWriter writer = new StringWriter();
		new freemarker.template.Template("probe", "${s?uncap_first}", cfg).process(java.util.Map.of("s", s), writer);
		return writer.toString();
	}

	/** 按processTableCols的规则预置colJavaName(去下划线驼峰,与模板?uncap_first配套) */
	private static List<QuickColMeta> cols(String... colNames) {
		List<QuickColMeta> colList = new ArrayList<>();
		for (String colName : colNames) {
			QuickColMeta colMeta = new QuickColMeta();
			colMeta.setColName(colName);
			colMeta.setColJavaName(StringUtil.toHumpStr(colName, true, true));
			colList.add(colMeta);
		}
		return colList;
	}
}
