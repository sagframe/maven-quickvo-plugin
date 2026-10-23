package org.sagacity.quickvo.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sagacity.quickvo.model.PrimaryKeyStrategy;
import org.sagacity.quickvo.model.QuickColMeta;

/**
 * 主键策略推导单测:配置策略、精度默认推导、数字类型升格BigInteger
 */
public class PrimaryKeyResolverTest {

	private QuickColMeta pkCol(String dataType, int precision, String resultType) {
		QuickColMeta col = new QuickColMeta();
		col.setColName("ID");
		col.setDataType(dataType);
		col.setPrecision(precision);
		col.setResultType(resultType);
		col.setAutoIncrement("false");
		col.setPrimaryKey(true);
		col.setPkFlag("1");
		return col;
	}

	@Test
	public void findStrategyShouldPreferLastMatch() {
		List<PrimaryKeyStrategy> strategies = new ArrayList<>();
		strategies.add(new PrimaryKeyStrategy("SAG_\\w+", "assign", null, null));
		strategies.add(new PrimaryKeyStrategy("SAG_STAFF", "generator", null, "snowflake"));
		// 后配置的优先
		assertEquals("generator", PrimaryKeyResolver.findStrategy(strategies, "SAG_STAFF").getStrategy());
		assertEquals("assign", PrimaryKeyResolver.findStrategy(strategies, "SAG_ORDER").getStrategy());
	}

	@Test
	public void precisionRulesShouldPickGeneratorByLength() throws Exception {
		// 雪花为63位long(当前19位十进制),列宽<19会溢出,保持assign
		List<QuickColMeta> cols = new ArrayList<>();
		cols.add(pkCol("varchar", 16, "String"));
		cols.add(pkCol("varchar", 18, "String"));
		PrimaryKeyResolver.applyStrategy(cols, true, false, null, "T0", new ArrayList<>());
		assertEquals("assign", cols.get(0).getStrategy());
		assertEquals("assign", cols.get(1).getStrategy());
		// varchar>=19雪花
		cols = new ArrayList<>();
		cols.add(pkCol("varchar", 19, "String"));
		PrimaryKeyResolver.applyStrategy(cols, true, false, null, "T1", new ArrayList<>());
		assertEquals("generator", cols.get(0).getStrategy());
		assertEquals(PrimaryKeyResolver.PK_SNOWFLAKE_GENERATOR, cols.get(0).getGenerator());
		// >=22默认22位
		cols = new ArrayList<>();
		cols.add(pkCol("varchar", 22, "String"));
		PrimaryKeyResolver.applyStrategy(cols, true, false, null, "T2", new ArrayList<>());
		assertEquals(PrimaryKeyResolver.PK_DEFAULT_GENERATOR, cols.get(0).getGenerator());
		// >=26纳秒26位
		cols = new ArrayList<>();
		cols.add(pkCol("BIGINT", 26, "Long"));
		PrimaryKeyResolver.applyStrategy(cols, true, false, null, "T3", new ArrayList<>());
		assertEquals(PrimaryKeyResolver.PK_NANOTIME_ID_GENERATOR, cols.get(0).getGenerator());
	}

	@Test
	public void numericOverflowTypeShouldUpgradeToBigInteger() throws Exception {
		List<String> impList = new ArrayList<>();
		List<QuickColMeta> cols = new ArrayList<>();
		// 雪花算法配int类型必须升格
		cols.add(pkCol("varchar", 19, "Integer"));
		PrimaryKeyResolver.applyStrategy(cols, true, false, null, "T4", impList);
		assertEquals("BigInteger", cols.get(0).getResultType());
		assertTrue(impList.contains("java.math.BigInteger"));
		// 22位默认策略配long类型升格
		impList = new ArrayList<>();
		cols = new ArrayList<>();
		cols.add(pkCol("BIGINT", 22, "Long"));
		PrimaryKeyResolver.applyStrategy(cols, true, false, null, "T5", impList);
		assertEquals("BigInteger", cols.get(0).getResultType());
	}

	@Test
	public void identityColumnShouldUseIdentityStrategy() throws Exception {
		List<QuickColMeta> cols = new ArrayList<>();
		QuickColMeta col = pkCol("BIGINT", 20, "Long");
		col.setAutoIncrement("true");
		cols.add(col);
		// 未配置策略时自增主键走identity
		PrimaryKeyResolver.applyStrategy(cols, true, false, null, "T6", new ArrayList<>());
		assertEquals("identity", col.getStrategy());
		// 配置了策略但非force,自增主键仍走identity
		col = pkCol("BIGINT", 20, "Long");
		col.setAutoIncrement("true");
		cols = new ArrayList<>();
		cols.add(col);
		PrimaryKeyResolver.applyStrategy(cols, true, false,
				new PrimaryKeyStrategy("T7", "generator", null, "snowflake"), "T7", new ArrayList<>());
		assertEquals("identity", col.getStrategy());
	}

	@Test
	public void configuredStrategyShouldApply() throws Exception {
		// sequence策略支持${tableName}占位符
		List<QuickColMeta> cols = new ArrayList<>();
		cols.add(pkCol("varchar", 32, "String"));
		PrimaryKeyResolver.applyStrategy(cols, true, false,
				new PrimaryKeyStrategy("T_SEQ", "sequence", "seq_${tableName}", null), "T_SEQ", new ArrayList<>());
		assertEquals("sequence", cols.get(0).getStrategy());
		assertEquals("seq_T_SEQ", cols.get(0).getSequence());
		// generator策略:default归一为默认生成器
		cols = new ArrayList<>();
		cols.add(pkCol("varchar", 32, "String"));
		PrimaryKeyResolver.applyStrategy(cols, true, false,
				new PrimaryKeyStrategy("T_GEN", "generator", null, "default"), "T_GEN", new ArrayList<>());
		assertEquals(PrimaryKeyResolver.PK_DEFAULT_GENERATOR, cols.get(0).getGenerator());
		// redis
		cols = new ArrayList<>();
		cols.add(pkCol("varchar", 32, "String"));
		PrimaryKeyResolver.applyStrategy(cols, true, false,
				new PrimaryKeyStrategy("T_REDIS", "generator", null, "redis"), "T_REDIS", new ArrayList<>());
		assertEquals(PrimaryKeyResolver.PK_REDIS_ID_GENERATOR, cols.get(0).getGenerator());
	}

	@Test
	public void invalidStrategyShouldThrow() {
		List<QuickColMeta> cols = new ArrayList<>();
		cols.add(pkCol("varchar", 32, "String"));
		assertThrows(Exception.class, () -> PrimaryKeyResolver.applyStrategy(cols, true, false,
				new PrimaryKeyStrategy("T_BAD", "unknown", null, null), "T_BAD", new ArrayList<>()));
	}

	@Test
	public void multiPkWithoutStrategyShouldSkipStrategy() throws Exception {
		// 复合主键未配置策略:不做精度推导(策略仅针对单主键),strategy保持模型默认值assign
		// (模板对复合主键不渲染strategy,此默认值无副作用)
		List<QuickColMeta> cols = new ArrayList<>();
		cols.add(pkCol("varchar", 32, "String"));
		PrimaryKeyResolver.applyStrategy(cols, false, false, null, "T8", new ArrayList<>());
		assertEquals("assign", cols.get(0).getStrategy());
		assertEquals(null, cols.get(0).getGenerator());
	}
}
