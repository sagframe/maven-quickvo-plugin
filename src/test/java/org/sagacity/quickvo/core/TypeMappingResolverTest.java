package org.sagacity.quickvo.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.quickvo.datasource.DbType;
import org.sagacity.quickvo.model.ColumnTypeMapping;

/**
 * 类型映射单测:native类型归一、默认映射、自定义映射精度/小数位匹配
 */
public class TypeMappingResolverTest {

	@Test
	public void normalizeNativeTypeShouldStripNoise() {
		assertEquals("INT", TypeMappingResolver.normalizeNativeType("INT UNSIGNED"));
		assertEquals("BIGINT", TypeMappingResolver.normalizeNativeType("BIGINT"));
		assertEquals("NUMBER", TypeMappingResolver.normalizeNativeType("NUMBER IDENTITY"));
		assertEquals("VARCHAR", TypeMappingResolver.normalizeNativeType("com.mysql.VARCHAR"));
	}

	@Test
	public void toJdbcTypeShouldMapNativeTypes() {
		assertEquals("TIMESTAMP", TypeMappingResolver.toJdbcType("datetime", DbType.CLICKHOUSE));
		assertEquals("VARCHAR", TypeMappingResolver.toJdbcType("TEXT", DbType.MYSQL));
		// 未定义类型原样返回
		assertEquals("weirdtype", TypeMappingResolver.toJdbcType("weirdtype", DbType.MYSQL));
	}

	@Test
	public void defaultJavaTypeShouldHonorDialectColumn() {
		// CLOB:通用"Clob"行排在oracle限定"CLOB"行之前且忽略大小写先命中,
		// 故CLOB在任何方言下都映射String;import列已清空(String/byte[]无需import,
		// 历史的java.sql.Clob/oracle.sql.BLOB import会致生成代码编译失败)
		String[] clob = TypeMappingResolver.defaultJavaType("CLOB", "oracle", DbType.ORACLE);
		assertEquals("String", clob[0]);
		assertNull(clob[1]);
		String[] genericClob = TypeMappingResolver.defaultJavaType("CLOB", "mysql", DbType.MYSQL);
		assertEquals("String", genericClob[0]);
		assertNull(genericClob[1]);
		// BLOB仅oracle方言命中(映射byte[]),其它方言命中通用Blob行
		assertEquals("byte[]", TypeMappingResolver.defaultJavaType("BLOB", "oracle", DbType.ORACLE)[0]);
		assertNull(TypeMappingResolver.defaultJavaType("BLOB", "mysql", DbType.MYSQL)[1]);
		// 常规映射
		String[] varchar = TypeMappingResolver.defaultJavaType("VARCHAR", "mysql", DbType.MYSQL);
		assertEquals("String", varchar[0]);
		assertNull(varchar[1]);
	}

	@Test
	public void toJdbcTypeShouldNormalizeTimezoneAndPrecision() {
		// oracle/h2上报带空格和精度的标准名,不归一会落入原样返回拼出非法JdbcTypes常量
		assertEquals("TIMESTAMP_WITH_TIMEZONE",
				TypeMappingResolver.toJdbcType("TIMESTAMP(6) WITH TIME ZONE", DbType.ORACLE));
		assertEquals("TIMESTAMP_WITH_TIMEZONE",
				TypeMappingResolver.toJdbcType("TIMESTAMP WITH LOCAL TIME ZONE", DbType.ORACLE));
		// 老驱动bigint(20) unsigned经normalizeNativeType剥unsigned后括号残留
		assertEquals("BIGINT", TypeMappingResolver.toJdbcType("bigint(20)", DbType.MYSQL));
		// 常见缺失类型补齐
		assertEquals("VARCHAR", TypeMappingResolver.toJdbcType("uniqueidentifier", DbType.SQLSERVER));
		assertEquals("VARCHAR", TypeMappingResolver.toJdbcType("enum", DbType.MYSQL));
	}

	@Test
	public void isKnownJdbcTypeShouldRejectUnknownNames() {
		assertTrue(TypeMappingResolver.isKnownJdbcType("VARCHAR"));
		assertTrue(TypeMappingResolver.isKnownJdbcType("TIMESTAMP_WITH_TIMEZONE"));
		assertTrue(TypeMappingResolver.isKnownJdbcType("1111"));
		assertFalse(TypeMappingResolver.isKnownJdbcType("WEIRD_TYPE"));
		assertFalse(TypeMappingResolver.isKnownJdbcType("TIMESTAMP WITH TIME ZONE"));
	}

	@Test
	public void customMappingShouldMatchPrecisionAndScale() {
		List<ColumnTypeMapping> mappings = new ArrayList<>();
		ColumnTypeMapping integerRange = new ColumnTypeMapping();
		integerRange.putNativeTypes(new String[] { "decimal" });
		integerRange.setPrecisionMin(1);
		integerRange.setPrecisionMax(8);
		integerRange.setScaleMin(0);
		integerRange.setScaleMax(0);
		integerRange.setJavaType("Integer");
		integerRange.setResultType("Integer");
		mappings.add(integerRange);
		// 精度1..8且无小数位命中
		assertEquals(integerRange,
				TypeMappingResolver.matchCustomMapping(mappings, "t.a", "decimal", 8, 0, -1));
		// 精度超界不命中
		assertNull(TypeMappingResolver.matchCustomMapping(mappings, "t.a", "decimal", 9, 0, -1));
		// 带小数位不命中
		assertNull(TypeMappingResolver.matchCustomMapping(mappings, "t.a", "decimal", 5, 2, -1));
		// table-field限定:其它表字段不命中
		ColumnTypeMapping tableField = new ColumnTypeMapping();
		tableField.putNativeTypes(new String[] { "json" });
		tableField.setTableField("sqltoy_jsontype_showcae.staff_set");
		tableField.setJavaType("List<StaffInfoVO>");
		tableField.setResultType("List<StaffInfoVO>");
		mappings.add(tableField);
		assertEquals(tableField, TypeMappingResolver.matchCustomMapping(mappings,
				"sqltoy_jsontype_showcae.staff_set", "json", 0, 0, -1));
		assertNull(TypeMappingResolver.matchCustomMapping(mappings, "other.table.col", "json", 0, 0, -1));
	}

}
