package org.sagacity.quickvo.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * extra-annotations表级配置:为匹配的表附加无法从表结构推导的注解
 * (如@Sharding、@DataVersion、脱敏@Secure等),随每次重新生成稳定输出,
 * 避免手工加在生成类上的注解被下次生成覆盖。
 *
 * @author zhongxuchen
 */
public class ExtraAnnotationConfig {
	/** 表名匹配正则(完整匹配,忽略大小写,与primary-key策略一致) */
	private String tableName;
	/** 类级注解(逐行原文输出) */
	private List<String> classAnnotations = new ArrayList<>();
	/** 字段级注解:列名(忽略大小写与_\-差异)->注解原文(多行) */
	private Map<String, String> fieldAnnotations = new LinkedHashMap<>();
	/** 需要额外import的类型(逗号分隔,全限定名) */
	private List<String> imports = new ArrayList<>();

	public String getTableName() {
		return tableName;
	}

	public void setTableName(String tableName) {
		this.tableName = tableName;
	}

	public List<String> getClassAnnotations() {
		return classAnnotations;
	}

	public void addClassAnnotation(String annotation) {
		classAnnotations.add(annotation);
	}

	public Map<String, String> getFieldAnnotations() {
		return fieldAnnotations;
	}

	public void addFieldAnnotation(String column, String annotation) {
		// 列名归一:剔除_和-,统一小写,与业务主键列匹配逻辑一致
		fieldAnnotations.put(column.replaceAll("\\_|\\-", "").toLowerCase(), annotation);
	}

	public List<String> getImports() {
		return imports;
	}

	public void addImports(List<String> imports) {
		this.imports.addAll(imports);
	}
}
