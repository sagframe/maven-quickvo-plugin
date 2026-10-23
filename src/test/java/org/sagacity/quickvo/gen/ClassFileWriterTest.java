package org.sagacity.quickvo.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.sagacity.quickvo.model.ChangeModel;
import org.sagacity.quickvo.render.TemplateRenderer;
import org.sagacity.quickvo.utils.LoggerUtil;

/**
 * 生成类文件写出单测:自动生成区合并语义(保护区外用户代码)
 */
public class ClassFileWriterTest {

	private final ClassFileWriter writer = new ClassFileWriter(new TemplateRenderer("UTF-8"),
			LoggerUtil.getLogger(Path.of("target")));

	@Test
	public void mergeRegionShouldReplaceOnlyGeneratedArea() {
		String begin = ClassFileWriter.FIELDS_BEGIN;
		String end = ClassFileWriter.FIELDS_END;
		String oldContent = "public class A {\r\n" + begin + "\r\nOLD AREA\r\n" + end
				+ "\r\n// 用户手写方法\r\npublic void mine() {}\r\n}";
		String newContent = "public class A {\r\n" + begin + "\r\nNEW AREA\r\n" + end
				+ "\r\n// 用户手写方法\r\npublic void mine() {}\r\n}";
		ChangeModel result = writer.mergeRegion(newContent, oldContent, begin, end, "A.java");
		assertTrue(result.isModified());
		assertEquals("public class A {\r\n" + begin + "\r\nNEW AREA\r\n" + end
				+ "\r\n// 用户手写方法\r\npublic void mine() {}\r\n}", result.getResult());
	}

	@Test
	public void mergeRegionShouldSkipWhenRegionUnchanged() {
		String begin = ClassFileWriter.CONSTRUCTOR_BEGIN;
		String end = ClassFileWriter.CONSTRUCTOR_END;
		String content = "class B {\r\n" + begin + "\r\nSAME\r\n" + end + "\r\n}";
		// 空白差异视为未变化
		ChangeModel result = writer.mergeRegion(
				"class B {\n" + begin + "\nSAME\n" + end + "\n}", content, begin, end, "B.java");
		assertFalse(result.isModified());
	}

	@Test
	public void mergeRegionShouldKeepFileWhenMarkersMissing() {
		// 视图场景:新模板无构造器区,历史版本此处抛异常中断任务,现应保留原文件
		String oldContent = "class C {\r\n" + ClassFileWriter.CONSTRUCTOR_BEGIN + "\r\nOLD\r\n"
				+ ClassFileWriter.CONSTRUCTOR_END + "\r\n}";
		String newContent = "class C {\r\n// no markers\r\n}";
		ChangeModel result = writer.mergeRegion(newContent, oldContent, ClassFileWriter.CONSTRUCTOR_BEGIN,
				ClassFileWriter.CONSTRUCTOR_END, "C.java");
		assertFalse(result.isModified());
		// 用户误删标记:同样保留原文件并告警,不再抛异常
		ChangeModel oldBroken = writer.mergeRegion(newContent, "class C {\r\n// markers deleted\r\n}",
				ClassFileWriter.CONSTRUCTOR_BEGIN, ClassFileWriter.CONSTRUCTOR_END, "C.java");
		assertFalse(oldBroken.isModified());
	}

	@Test
	public void mergeRegionShouldBeQuietWhenBothSidesLackMarker() {
		// 视图parent重生成:旧文件(首次生成)与新模板均无构造器区标记,属预期形态——
		// 保留原文件且不应每次打"已保留原文件内容(请删除该文件)"的误导性告警
		String noMarker = "class V {\r\n// view has no constructor area\r\n}";
		ChangeModel result = writer.mergeRegion(noMarker, noMarker, ClassFileWriter.CONSTRUCTOR_BEGIN,
				ClassFileWriter.CONSTRUCTOR_END, "VParent.java");
		assertFalse(result.isModified());
		assertFalse(result.isModified() && result.getResult() != null);
	}

	@Test
	public void mergeImportsShouldReplaceManagedArea() {
		String oldContent = "package a;\r\n" + ClassFileWriter.IMPORTS_BEGIN + "\r\nimport java.math.BigDecimal;\r\n"
				+ ClassFileWriter.IMPORTS_END + "\r\nimport user.Custom;\r\nclass A {}";
		String newContent = "package a;\r\n" + ClassFileWriter.IMPORTS_BEGIN
				+ "\r\nimport java.time.LocalDate;\r\n" + ClassFileWriter.IMPORTS_END + "\r\nclass A {}";
		ChangeModel result = writer.mergeImports(newContent, oldContent, "A.java");
		// 标记区整体替换:BigDecimal移除,LocalDate进入;标记外用户import保留
		assertTrue(result.isModified());
		assertTrue(result.getResult().contains("import java.time.LocalDate;"));
		assertFalse(result.getResult().contains("java.math.BigDecimal"));
		assertTrue(result.getResult().contains("import user.Custom;"));
	}

	@Test
	public void mergeImportsShouldAppendMissingForLegacyFiles() {
		// 历史版本生成的文件无标记:只补缺失import,不动既有内容
		String oldContent = "package a;\r\nimport java.io.Serializable;\r\nimport user.Custom;\r\nclass A {}";
		String newContent = "package a;\r\n" + ClassFileWriter.IMPORTS_BEGIN
				+ "\r\nimport java.io.Serializable;\r\nimport java.math.BigDecimal;\r\n"
				+ ClassFileWriter.IMPORTS_END + "\r\nclass A {}";
		ChangeModel result = writer.mergeImports(newContent, oldContent, "A.java");
		assertTrue(result.isModified());
		String merged = result.getResult();
		assertTrue(merged.contains("import java.math.BigDecimal;"));
		assertTrue(merged.contains("import user.Custom;"));
		// 补充位置在最后一条import之后
		assertTrue(merged.indexOf("import java.math.BigDecimal;") > merged.indexOf("import user.Custom;"));
	}

	@Test
	public void mergeClassAnnotationsShouldMigrateLegacyFileWithoutMarkers() {
		// 老项目升级:旧文件无类头标记区,应按结构(类javadoc的*/到class声明)迁移,刷新数据库生成注解并补标记
		String head = "package a;\r\n" + ClassFileWriter.IMPORTS_BEGIN + "\r\nimport x.Y;\r\n"
				+ ClassFileWriter.IMPORTS_END + "\r\n/**\r\n * 类说明\r\n */\r\n";
		String tail = "public class A implements Serializable {\r\n}";
		String oldContent = head
				+ "@SqlToyEntity\r\n@Entity(tableName=\"t\")\r\n@Indexes(indexes={@Index(name=\"OLD\")})\r\n" + tail;
		String newContent = head + ClassFileWriter.CLASS_ANNOTATIONS_BEGIN
				+ "\r\n@SqlToyEntity\r\n@Entity(tableName=\"t\")\r\n@Indexes(indexes={@Index(name=\"NEW\")})\r\n"
				+ ClassFileWriter.CLASS_ANNOTATIONS_END + "\r\n" + tail;
		ChangeModel result = writer.mergeClassAnnotations(newContent, oldContent, "A.java");
		assertTrue(result.isModified());
		String merged = result.getResult();
		// 迁移后补上标记区,数据库生成注解被刷新(OLD→NEW),类javadoc与class声明保留
		assertTrue(merged.contains(ClassFileWriter.CLASS_ANNOTATIONS_BEGIN));
		assertTrue(merged.contains(ClassFileWriter.CLASS_ANNOTATIONS_END));
		assertTrue(merged.contains("@Index(name=\"NEW\")"));
		assertFalse(merged.contains("@Index(name=\"OLD\")"));
		assertTrue(merged.contains("类说明"));
		assertTrue(merged.contains("public class A implements Serializable {"));
	}

	@Test
	public void mergeClassAnnotationsShouldReplaceMarkedRegion() {
		// 新格式:旧文件已有标记,按区替换刷新数据库生成注解
		String head = "/**\r\n * 类说明\r\n */\r\n";
		String tail = "public class A {\r\n}";
		String oldContent = head + ClassFileWriter.CLASS_ANNOTATIONS_BEGIN
				+ "\r\n@Indexes(indexes={@Index(name=\"OLD\")})\r\n" + ClassFileWriter.CLASS_ANNOTATIONS_END + "\r\n"
				+ tail;
		String newContent = head + ClassFileWriter.CLASS_ANNOTATIONS_BEGIN
				+ "\r\n@Indexes(indexes={@Index(name=\"NEW\")})\r\n" + ClassFileWriter.CLASS_ANNOTATIONS_END + "\r\n"
				+ tail;
		ChangeModel result = writer.mergeClassAnnotations(newContent, oldContent, "A.java");
		assertTrue(result.isModified());
		assertTrue(result.getResult().contains("@Index(name=\"NEW\")"));
		assertFalse(result.getResult().contains("@Index(name=\"OLD\")"));
	}

	@Test
	public void mergeClassAnnotationsShouldSkipWhenTemplateHasNoRegion() {
		// DTO/parent模板无类头标记区:直接跳过,不修改、不告警
		String content = "package a;\r\npublic class A {\r\n}";
		ChangeModel result = writer.mergeClassAnnotations(content, content, "A.java");
		assertFalse(result.isModified());
	}
}
