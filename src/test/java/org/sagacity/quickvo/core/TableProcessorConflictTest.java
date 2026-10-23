package org.sagacity.quickvo.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.quickvo.model.QuickColMeta;
import org.sagacity.quickvo.utils.StringUtil;

/**
 * 驼峰冲突修正单测:以最终java字段名(含fields-rid-prefix剥离)为基准检测冲突,
 * 冲突组中(剥前缀后)含下划线的成员改为保留下划线形态消歧;
 * 配置前缀后不得误报改名(无冲突被改)也不得漏判真冲突
 */
public class TableProcessorConflictTest {

	@Test
	public void underscoreVariantShouldBeRenamedWhenJavaNamesCollide() {
		// reciveTime与recive_time并存→java名冲突,下划线列改为保留Underscore形态
		List<QuickColMeta> colList = cols(null, "reciveTime", "recive_time");
		TableProcessor.adjustCamelConflict(colList, null);
		assertEquals("ReciveTime", colList.get(0).getColJavaName());
		assertEquals("Recive_Time", colList.get(1).getColJavaName());
	}

	@Test
	public void ridPrefixRealConflictShouldStillBeDetected() {
		// 前缀sys_下两列java名同为StaffName:真冲突不得漏判(历史实现键空间错位导致双方均含_而漏判)
		List<QuickColMeta> colList = cols("sys_", "sys_staffName", "sys_staff_name");
		TableProcessor.adjustCamelConflict(colList, "sys_");
		assertEquals("StaffName", colList.get(0).getColJavaName());
		assertEquals("Staff_Name", colList.get(1).getColJavaName());
	}

	@Test
	public void ridPrefixShouldNotTriggerFalseRename() {
		// 前缀user_剥离后java名Name/UserName本无冲突:不得因原始名形态误改(历史误报改名)
		List<QuickColMeta> colList = cols("user_", "user_name", "userName");
		TableProcessor.adjustCamelConflict(colList, "user_");
		assertEquals("Name", colList.get(0).getColJavaName());
		assertEquals("UserName", colList.get(1).getColJavaName());
	}

	@Test
	public void noConflictShouldKeepAllNames() {
		List<QuickColMeta> colList = cols(null, "user_name", "nick_name");
		TableProcessor.adjustCamelConflict(colList, null);
		assertEquals("UserName", colList.get(0).getColJavaName());
		assertEquals("NickName", colList.get(1).getColJavaName());
	}

	/** 按processTableCols的规则预置colJavaName(剥前缀+去下划线驼峰) */
	private static List<QuickColMeta> cols(String ridPrefix, String... colNames) {
		List<QuickColMeta> colList = new ArrayList<>();
		for (String colName : colNames) {
			QuickColMeta colMeta = new QuickColMeta();
			colMeta.setColName(colName);
			String effective = colName;
			if (ridPrefix != null && colName.toLowerCase(java.util.Locale.ROOT).startsWith(ridPrefix)) {
				effective = colName.substring(ridPrefix.length());
			}
			colMeta.setColJavaName(StringUtil.toHumpStr(effective, true, true));
			colList.add(colMeta);
		}
		return colList;
	}
}
