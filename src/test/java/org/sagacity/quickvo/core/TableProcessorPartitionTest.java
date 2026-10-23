package org.sagacity.quickvo.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.quickvo.QuickVoContext;
import org.sagacity.quickvo.datasource.DbType;
import org.sagacity.quickvo.model.ConfigModel;
import org.sagacity.quickvo.model.QuickColMeta;
import org.sagacity.quickvo.model.QuickModel;
import org.sagacity.quickvo.model.TableColumnMeta;

/**
 * include.partition开关与主键推导解耦单测:开关只门控@PartitionKey注解渲染与import,
 * partitionKey模型标记必须保留——processPartitionAndPk依赖它从分区表复合主键中剔除
 * 分区列推导真实主键(历史回归:开关关闭曾连带清掉标记,分区表singlePk由'1'变'0',
 * @Id的strategy/generator静默丢失)
 */
public class TableProcessorPartitionTest {
	private static final String PARTITION_KEY_IMPORT = "org.sagacity.sqltoy.config.annotation.PartitionKey";

	// 不用@TempDir:QuickVoContext会在目录内打开quickvo.log的FileHandler,
	// Windows下文件被锁导致TempDir清理失败;统一放target下免清理
	private static QuickVoContext newContext() {
		return new QuickVoContext(java.nio.file.Path.of("target"), "quickvo.xml");
	}

	@Test
	public void partitionKeyFlagMustSurviveWhenIncludePartitionOff() throws Exception {
		QuickVoContext context = newContext();
		context.getProperties().override("include.partition", "false");
		List<String> impList = new ArrayList<>();
		List<QuickColMeta> colList = processTradeCols(context, impList);
		// 模型标记保留(主键推导依据),import不输出(渲染关闭)
		assertTrue(colList.get(1).getPartitionKey());
		assertFalse(impList.contains(PARTITION_KEY_IMPORT));
		// 主键推导不受开关影响:分区列从主键中剔除为辅助主键,真实主键数=1(而非2)
		TableProcessor.setPk(colList, List.of("TRADE_ID", "TRADE_DATE"));
		assertEquals(1, TableProcessor.processPartitionAndPk(colList));
		assertTrue(colList.get(1).isAssistPartition());
		assertFalse(colList.get(0).isAssistPartition());
	}

	@Test
	public void includePartitionDefaultOnKeepsFlagAndImport() throws Exception {
		QuickVoContext context = newContext();
		List<String> impList = new ArrayList<>();
		List<QuickColMeta> colList = processTradeCols(context, impList);
		assertTrue(colList.get(1).getPartitionKey());
		assertTrue(impList.contains(PARTITION_KEY_IMPORT));
		TableProcessor.setPk(colList, List.of("TRADE_ID", "TRADE_DATE"));
		assertEquals(1, TableProcessor.processPartitionAndPk(colList));
	}

	private static List<QuickColMeta> processTradeCols(QuickVoContext context, List<String> impList) throws Exception {
		return TableProcessor.processTableCols(context, new ConfigModel(), new QuickModel(), "QUICKVO_DEMO_TRADE",
				tradeCols(), null, impList, DbType.MYSQL, "mysql", false, null);
	}

	/** 分区表典型两列:业务主键列 + 分区键列(纳入复合主键) */
	private static List<TableColumnMeta> tradeCols() {
		TableColumnMeta tradeId = new TableColumnMeta();
		tradeId.setColName("TRADE_ID");
		tradeId.setTypeName("VARCHAR");
		tradeId.setNullable(false);
		tradeId.setPrecision(32);
		TableColumnMeta tradeDate = new TableColumnMeta();
		tradeDate.setColName("TRADE_DATE");
		tradeDate.setTypeName("DATE");
		tradeDate.setNullable(false);
		tradeDate.setPartitionKey(true);
		List<TableColumnMeta> cols = new ArrayList<>();
		cols.add(tradeId);
		cols.add(tradeDate);
		return cols;
	}
}
