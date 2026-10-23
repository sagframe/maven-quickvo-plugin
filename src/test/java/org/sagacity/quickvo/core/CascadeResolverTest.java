package org.sagacity.quickvo.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.quickvo.model.CascadeModel;
import org.sagacity.quickvo.model.QuickModel;
import org.sagacity.quickvo.model.QuickVO;
import org.sagacity.quickvo.model.TableConstractModel;

/**
 * 级联删除语义单测:JDBC DELETE_RULE(0=CASCADE,1=RESTRICT,3=NO ACTION)必须归一为
 * 模板通道的布尔语义(1=级联删除子表),仅ON DELETE CASCADE推导delete=true;
 * cascade配置的delete为三态(null=未配置,跟随外键;true/false显式覆盖),
 * 避免"仅为配置load/orderBy"的cascade条目把RESTRICT外键误开成级联删除
 */
public class CascadeResolverTest {

	@Test
	public void restrictAndNoActionMustNotCascadeDelete() {
		List<TableConstractModel> exportKeys = new ArrayList<>();
		exportKeys.add(exportKey("QUICKVO_ORDER", java.sql.DatabaseMetaData.importedKeyRestrict));
		exportKeys.add(exportKey("QUICKVO_TRADE", java.sql.DatabaseMetaData.importedKeyNoAction));
		exportKeys.add(exportKey("QUICKVO_BILL", java.sql.DatabaseMetaData.importedKeySetNull));
		QuickVO quickVO = new QuickVO();
		CascadeResolver.processExportTables(quickVO, exportKeys, quickModel(), null);
		assertEquals(0, byTable(quickVO, "QUICKVO_ORDER").getDeleteRule());
		assertEquals(0, byTable(quickVO, "QUICKVO_TRADE").getDeleteRule());
		assertEquals(0, byTable(quickVO, "QUICKVO_BILL").getDeleteRule());
	}

	@Test
	public void onDeleteCascadeMustCascadeDelete() {
		List<TableConstractModel> exportKeys = new ArrayList<>();
		exportKeys.add(exportKey("QUICKVO_ORDER", java.sql.DatabaseMetaData.importedKeyCascade));
		QuickVO quickVO = new QuickVO();
		CascadeResolver.processExportTables(quickVO, exportKeys, quickModel(), null);
		assertEquals(1, byTable(quickVO, "QUICKVO_ORDER").getDeleteRule());
	}

	@Test
	public void cascadeConfigWithoutDeleteShouldFollowFkRule() {
		// 未写delete(如只为配置load/orderBy):保持外键推导结果,不得默认打开级联删除
		CascadeModel orderCascade = cascade("QUICKVO_ORDER", null);
		orderCascade.setLoad("STATUS=1");
		List<TableConstractModel> exportKeys = new ArrayList<>();
		exportKeys.add(exportKey("QUICKVO_ORDER", java.sql.DatabaseMetaData.importedKeyRestrict));
		exportKeys.add(exportKey("QUICKVO_TRADE", java.sql.DatabaseMetaData.importedKeyCascade));
		QuickVO quickVO = new QuickVO();
		CascadeResolver.processExportTables(quickVO, exportKeys, quickModel(),
				List.of(orderCascade, cascade("QUICKVO_TRADE", null)));
		assertEquals(0, byTable(quickVO, "QUICKVO_ORDER").getDeleteRule());
		assertEquals(1, byTable(quickVO, "QUICKVO_TRADE").getDeleteRule());
		// cascade条目的其它配置(load)仍正常生效
		assertEquals("STATUS=1", byTable(quickVO, "QUICKVO_ORDER").getLoad());
	}

	@Test
	public void cascadeConfigShouldOverrideFkRule() {
		// 配置显式delete=true:RESTRICT外键也级联删除(应用层主动决策)
		List<TableConstractModel> exportKeys = new ArrayList<>();
		exportKeys.add(exportKey("QUICKVO_ORDER", java.sql.DatabaseMetaData.importedKeyRestrict));
		exportKeys.add(exportKey("QUICKVO_TRADE", java.sql.DatabaseMetaData.importedKeyCascade));
		QuickVO quickVO = new QuickVO();
		CascadeResolver.processExportTables(quickVO, exportKeys, quickModel(),
				List.of(cascade("QUICKVO_ORDER", true), cascade("QUICKVO_TRADE", false)));
		assertEquals(1, byTable(quickVO, "QUICKVO_ORDER").getDeleteRule());
		// 配置显式delete=false:CASCADE外键也不级联删除
		assertEquals(0, byTable(quickVO, "QUICKVO_TRADE").getDeleteRule());
	}

	@Test
	public void multiColumnFkMergeKeepsBooleanSemantics() {
		// 同一子表多列外键合并:第二列走merge路径,布尔语义不被原始规则覆盖
		TableConstractModel first = exportKey("QUICKVO_ORDER", java.sql.DatabaseMetaData.importedKeyRestrict);
		first.setPkColName("STAFF_ID");
		first.setPkRefColName("STAFF_ID");
		TableConstractModel second = exportKey("QUICKVO_ORDER", java.sql.DatabaseMetaData.importedKeyCascade);
		second.setPkColName("STAFF_CODE");
		second.setPkRefColName("STAFF_CODE");
		List<TableConstractModel> exportKeys = new ArrayList<>();
		exportKeys.add(first);
		exportKeys.add(second);
		QuickVO quickVO = new QuickVO();
		CascadeResolver.processExportTables(quickVO, exportKeys, quickModel(), null);
		assertEquals(0, byTable(quickVO, "QUICKVO_ORDER").getDeleteRule());
	}

	private static TableConstractModel exportKey(String refTable, int deleteRule) {
		TableConstractModel model = new TableConstractModel();
		model.setPkRefTableName(refTable);
		model.setPkColName("STAFF_ID");
		model.setPkRefColName("STAFF_ID");
		model.setDeleteRule(deleteRule);
		return model;
	}

	private static CascadeModel cascade(String tableName, Boolean delete) {
		CascadeModel model = new CascadeModel();
		model.setTableName(tableName);
		model.setDelete(delete);
		return model;
	}

	private static QuickModel quickModel() {
		QuickModel quickModel = new QuickModel();
		quickModel.setHasEntity(true);
		quickModel.setEntityName("#{subName}");
		return quickModel;
	}

	private static TableConstractModel byTable(QuickVO quickVO, String refTable) {
		for (Object export : quickVO.getExportTables()) {
			TableConstractModel model = (TableConstractModel) export;
			if (refTable.equalsIgnoreCase(model.getPkRefTableName())) {
				return model;
			}
		}
		throw new AssertionError("export table not found: " + refTable);
	}
}
