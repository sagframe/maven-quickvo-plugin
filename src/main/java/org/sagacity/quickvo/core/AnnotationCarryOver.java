package org.sagacity.quickvo.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 重新生成时的注解继承:解析已生成的实体文件,提取无法从表结构推导的sqltoy语义注解,
 * 随下次生成稳定输出,避免改表重新生成冲掉手工配置。
 *
 * 继承范围(字段级):Tenant、Translate/Translates、SqlToyFieldAlias、DataVersion、OneToOne;
 * 继承范围(类级):DataVersion、Secure、SecureConfig、Tenant;
 * 特殊规则:OneToMany/BusinessId 由quickvo.xml配置优先(cascade/business-primary-key命中时以配置为准),
 * 未配置时继承旧文件手工调优结果
 *
 * 解析容错:兼容tab/空格混合缩进(IDE重排格式化)、注解参数跨行折行书写;
 * 注解行按结构识别(行首@起始),不再依赖匹配@Column参数内容,备注含分号等特殊符号不受影响
 *
 * @author zhongxuchen
 */
public class AnnotationCarryOver {
	/** 继承白名单注解(简单名,类级+字段级);DOTALL支持跨行折行的注解参数 */
	private static final Pattern WHITELIST_ANNOTATION = Pattern.compile(
			"^@(?:org\\.sagacity\\.sqltoy\\.config\\.annotation\\.)?(DataVersion|Secure|SecureConfig|Tenant|Sharding"
					+ "|Translate|Translates|SqlToyFieldAlias|OneToOne|OneToMany|BusinessId)\\b.*",
			Pattern.DOTALL);

	/**
	 * 注解单元:行首@起始的一行 + 其后缩进的续行(折行注解参数);续行不能以@或/开头
	 * (避免吞并下一注解和javadoc),也不能以Java修饰符开头(避免把字段声明行误认为折行续文)
	 */
	private static final String ANNOTATION_UNIT = "[\\t ]*@[^\\r\\n]*(?:\\r?\\n[\\t ]+"
			+ "(?!(?:public|protected|private|static|final|transient|volatile)\\b)"
			+ "[^@/\\r\\n \\t][^\\r\\n]*)*\\r?\\n";

	/**
	 * 注解单元之间、以及注解块与字段/类声明之间允许的"间隔行":空行或单行注释(行注释//或单行的块注释)。
	 * 容忍夹注释(修复M1:此前注释行会中断匹配,导致@Tenant等手工注解在重生成时丢失);
	 * 多行块注释通常位于注解之前而非夹在中间,故此处只处理单行注释,规避跨行匹配的回溯风险
	 */
	private static final String GAP_LINES = "(?:[\\t ]*\\r?\\n|[\\t ]*(?://[^\\r\\n]*|/\\*[^\\r\\n]*\\*/)[\\t ]*\\r?\\n)*";

	/**
	 * 字段块:连续注解单元(单元间允许空行/单行注释)+字段声明(兼容带初始化赋值,如List字段=new ArrayList),
	 * 捕获组1=注解块原文、捕获组2=字段名
	 */
	private static final Pattern FIELD_BLOCK = Pattern.compile(
			"((?:" + ANNOTATION_UNIT + GAP_LINES + ")+)" + GAP_LINES
					+ "[\\t ]*(?:protected|private)\\s+[\\w.<>\\[\\]]+\\s+(\\w+)\\s*(?:=[^;]*)?;");

	/** 类级注解块:类声明前的注解行(兼容缩进、折行、夹注释,以及abstract/final/static修饰符) */
	private static final Pattern CLASS_ANNOTATIONS = Pattern
			.compile("((?:" + ANNOTATION_UNIT + GAP_LINES + ")+)public\\s+(?:(?:abstract|final|static)\\s+)*class\\s",
					Pattern.MULTILINE);

	/** 白名单相关import;捕获组1=完整import路径(用\s+兼容import后多空格,避免substring硬编码偏移带入前导空格) */
	private static final Pattern WHITELIST_IMPORT = Pattern.compile(
			"^import\\s+(org\\.sagacity\\.sqltoy\\.config\\.annotation\\.(?:DataVersion|Secure|SecureConfig|Tenant|Sharding"
					+ "|Translate|Translates|SqlToyFieldAlias|OneToOne|OneToMany|BusinessId));");

	/** 解析结果 */
	public static class CarryOverResult {
		/** 字段级注解:字段名(小写)->注解原文列表 */
		public final Map<String, List<String>> fieldAnnotations = new LinkedHashMap<>();
		/** 类级注解原文 */
		public final List<String> classAnnotations = new ArrayList<>();
		/** 级联字段名(小写)->@OneToMany注解原文(供级联覆盖判断) */
		public final Map<String, String> oneToManyByField = new LinkedHashMap<>();
		/** BusinessId字段名(小写)->注解原文 */
		public final Map<String, String> businessIdByField = new LinkedHashMap<>();
		/** 白名单注解的import */
		public final List<String> imports = new ArrayList<>();
	}

	/**
	 * 解析旧生成文件中的可继承注解;文件不存在或无内容返回null
	 */
	public static CarryOverResult parse(String existingContent) {
		if (existingContent == null || existingContent.trim().isEmpty()) {
			return null;
		}
		CarryOverResult result = new CarryOverResult();
		for (String line : existingContent.split("\\r\\n|\\n")) {
			String trimmed = line.trim();
			Matcher importMatcher = WHITELIST_IMPORT.matcher(trimmed);
			if (importMatcher.matches()) {
				result.imports.add(importMatcher.group(1));
			}
		}
		// 类级注解
		Matcher classMatcher = CLASS_ANNOTATIONS.matcher(existingContent);
		if (classMatcher.find()) {
			for (String annotation : mergeAnnotationLines(classMatcher.group(1))) {
				collect(annotation, result.classAnnotations);
			}
		}
		// 字段级注解
		Matcher fieldMatcher = FIELD_BLOCK.matcher(existingContent);
		while (fieldMatcher.find()) {
			String fieldName = fieldMatcher.group(2).toLowerCase();
			for (String annotation : mergeAnnotationLines(fieldMatcher.group(1))) {
				if (!collect(annotation, null)) {
					continue;
				}
				result.fieldAnnotations.computeIfAbsent(fieldName, k -> new ArrayList<>()).add(annotation);
				if (annotation.startsWith("@OneToMany") || annotation.startsWith("@org.sagacity.sqltoy.config.annotation.OneToMany")) {
					result.oneToManyByField.put(fieldName, annotation);
				}
				if (annotation.startsWith("@BusinessId") || annotation.startsWith("@org.sagacity.sqltoy.config.annotation.BusinessId")) {
					result.businessIdByField.put(fieldName, annotation);
				}
			}
		}
		return result;
	}

	/**
	 * 注解块原文→注解条目列表:以行首@起始新条目,不以@开头的缩进行视为上一注解的折行续文合并;
	 * 保证跨行书写的注解(如@Translate(key="x"\n\t,type=Y.class))完整继承而非截断。
	 * 前一注解括号未闭合时,行首@开头的续行是容器注解的折行成员(如
	 * @Translates({@Translate(..),\n\t@Translate(..)}),IDE格式化常见形态),
	 * 同样并入上一注解——切分会产生括号不完整的残缺注解,继承后无法编译
	 */
	private static List<String> mergeAnnotationLines(String block) {
		List<String> merged = new ArrayList<>();
		StringBuilder current = null;
		boolean unbalanced = false;
		for (String line : block.split("\\r\\n|\\n")) {
			String trimmed = line.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			// 跳过夹在注解间/注解与字段间的注释行(单行//、块注释/*..*/、javadoc续行*),
			// 它们是GAP_LINES放行的"间隔"而非注解折行续文,不能被并入注解原文
			if (trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")) {
				continue;
			}
			if (trimmed.startsWith("@")) {
				if (current != null && unbalanced) {
					current.append(trimmed);
					unbalanced = isUnbalanced(current);
					continue;
				}
				if (current != null) {
					merged.add(current.toString());
				}
				current = new StringBuilder(trimmed);
				unbalanced = isUnbalanced(current);
			} else if (current != null) {
				current.append(trimmed);
				unbalanced = isUnbalanced(current);
			}
		}
		if (current != null) {
			merged.add(current.toString());
		}
		return merged;
	}

	/**
	 * 注解文本的圆/花/方括号是否未闭合:先剔除字符串与字符字面量,
	 * 避免字面量内的括号(如cacheName="dict(x)")干扰计数;提前多闭视为完整(保守切分)
	 */
	private static boolean isUnbalanced(CharSequence annotationText) {
		String cleaned = annotationText.toString()
				.replaceAll("\"(?:\\\\.|[^\"\\\\])*\"", "\"\"")
				.replaceAll("'(?:\\\\.|[^'\\\\])*'", "''");
		int depth = 0;
		for (int i = 0; i < cleaned.length(); i++) {
			char c = cleaned.charAt(i);
			if (c == '(' || c == '{' || c == '[') {
				depth++;
			} else if (c == ')' || c == '}' || c == ']') {
				depth--;
				if (depth < 0) {
					return false;
				}
			}
		}
		return depth > 0;
	}

	/** 是否白名单注解;收集目标非null时同时收集 */
	private static boolean collect(String annotationText, List<String> target) {
		Matcher matcher = WHITELIST_ANNOTATION.matcher(annotationText);
		if (!matcher.matches()) {
			return false;
		}
		if (target != null) {
			target.add(annotationText);
		}
		return true;
	}
}
