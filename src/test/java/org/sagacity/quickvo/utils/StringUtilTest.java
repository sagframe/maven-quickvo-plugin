package org.sagacity.quickvo.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 字符串工具单测:驼峰转换、转义、hash稳定性(serialVersionUID依赖,值不可漂移)
 */
public class StringUtilTest {

	@Test
	public void toHumpStrShouldConvertSnakeCase() {
		assertEquals("QuickvoDemoStaff", StringUtil.toHumpStr("QUICKVO_DEMO_STAFF", true, true));
		assertEquals("QuickvoDemoStaff", StringUtil.toHumpStr("quickvo_demo_staff", true, true));
		assertEquals("quickvoDemoStaff", StringUtil.toHumpStr("quickvo_demo_staff", false, true));
		assertEquals("StaffInfo", StringUtil.toHumpStr("STAFF_INFO", true, true));
		assertEquals("staffId", StringUtil.toHumpStr("STAFF_ID", false, true));
		// 全大写单元格仅首字母大写
		assertEquals("Id", StringUtil.toHumpStr("ID", true, true));
	}

	@Test
	public void matchesShouldUseFindSemantics() {
		// 历史行为:matches内部是find(包含即命中),include正则依赖此语义
		assertTrue(StringUtil.matches("X_SAG_STAFF_INFO", "SAG_\\w+"));
		assertFalse(StringUtil.matches("SYS_USER", "^SAG_\\w+"));
		assertTrue(StringUtil.matches("SAG_STAFF", "^SAG_\\w+"));
	}

	@Test
	public void escapeCommentShouldEscapeQuotesAndBackslash() {
		assertEquals(null, StringUtil.escapeComment(null));
		assertEquals("", StringUtil.escapeComment(""));
		assertEquals("普通注释", StringUtil.escapeComment("普通注释"));
		assertEquals("带\\\"引号\\\"", StringUtil.escapeComment("带\"引号\""));
		assertEquals("单\\\\反斜杠", StringUtil.escapeComment("单\\反斜杠"));
		// 已成对的\\保持原样
		assertEquals("a\\\\b", StringUtil.escapeComment("a\\\\b"));
	}

	@Test
	public void hashValuesMustStayStable() {
		// serialVersionUID来源,算法值不可变更(历史生成文件兼容的红线)
		assertEquals(2666769912017185456L, StringUtil.hash("com.quickvo.demo.mysql.entity.base.AbstractStaff"));
		assertEquals(7667616859969572298L, StringUtil.hash("com.quickvo.demo.pg.entity.Staff"));
		assertEquals(6517480159545499023L, StringUtil.hash("com.quickvo.demo.mysql.vo.StaffVO"));
		assertEquals(4350162337392911942L, StringUtil.hash("中文包名.测试"));
	}

	@Test
	public void subStartShouldRemoveCaseInsensitivePrefix() {
		assertEquals("Staff", StringUtil.subStart("QuickvoDemoStaff", "quickvodemo"));
		assertEquals("QuickvoDemoStaff", StringUtil.subStart("QuickvoDemoStaff", "other"));
		assertEquals("source", StringUtil.subStart("source", ""));
	}

	@Test
	public void replaceAllStrShouldReplaceAllOccurrences() {
		assertEquals("a-b-c", StringUtil.replaceAllStr("a_b_c", "_", "-"));
		assertEquals("xyz", StringUtil.replaceAllStr("abc", "abc", "xyz"));
		// 替换值包含原模板时不能死循环
		assertEquals("aa", StringUtil.replaceAllStr("a", "a", "aa"));
	}
}
