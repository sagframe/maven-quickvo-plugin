package org.sagacity.quickvo.core;

import java.util.List;

import org.sagacity.quickvo.model.PrimaryKeyStrategy;
import org.sagacity.quickvo.model.QuickColMeta;
import org.sagacity.quickvo.utils.Msg;
import org.sagacity.quickvo.utils.StringUtil;

/**
 * 主键策略推导:配置策略匹配、未配置时按主键类型与长度的默认推导、
 * 以及数字型主键强制升格BigInteger避免溢出。
 *
 * @author zhongxuchen
 */
public class PrimaryKeyResolver {
	/** 主键默认生成策略(22位长度的主键) */
	public static final String PK_DEFAULT_GENERATOR = "org.sagacity.sqltoy.plugins.id.impl.DefaultIdGenerator";

	/** uuid主键策略 */
	public static final String PK_UUID_GENERATOR = "org.sagacity.sqltoy.plugins.id.impl.UUIDGenerator";

	/** twitter的雪花id算法(63位long型,十进制18~19位) */
	public static final String PK_SNOWFLAKE_GENERATOR = "org.sagacity.sqltoy.plugins.id.impl.SnowflakeIdGenerator";

	/** 26位纳秒时序ID产生策略 */
	public static final String PK_NANOTIME_ID_GENERATOR = "org.sagacity.sqltoy.plugins.id.impl.NanoTimeIdGenerator";

	/** 基于redis产生id */
	public static final String PK_REDIS_ID_GENERATOR = "org.sagacity.sqltoy.plugins.id.impl.RedisIdGenerator";

	/** ULID(时序可排序id,依赖com.github.f4b6a3:ulid-creator) */
	public static final String PK_ULID_GENERATOR = "org.sagacity.sqltoy.plugins.id.impl.ULIDGenerator";

	/**
	 * 匹配表的主键策略配置(多个命中时以最后一个为准,表名为完整匹配)
	 */
	public static PrimaryKeyStrategy findStrategy(List<PrimaryKeyStrategy> strategies, String tableName) {
		PrimaryKeyStrategy result = null;
		for (PrimaryKeyStrategy primaryKeyStrategy : strategies) {
			if (tableName.matches("(?i)".concat(primaryKeyStrategy.getName()))) {
				result = primaryKeyStrategy;
			}
		}
		return result;
	}

	/**
	 * 对首个真实主键列设置主键策略;返回是否命中策略场景(内部已处理列属性)
	 *
	 * @param colList          表列信息
	 * @param onePk            是否单主键
	 * @param filterAssistPK   是否排除分区辅助主键
	 * @param strategy         命中的主键策略配置(可为null)
	 * @param tableName        表名
	 * @param impList          生成文件的import集合(顺序敏感)
	 */
	public static void applyStrategy(List<QuickColMeta> colList, boolean onePk, boolean filterAssistPK,
			PrimaryKeyStrategy strategy, String tableName, List<String> impList) throws Exception {
		for (QuickColMeta quickColMeta : colList) {
			boolean hasRealPK = filterAssistPK
					? quickColMeta.isPrimaryKey() && !quickColMeta.isAssistPartition()
					: quickColMeta.isPrimaryKey();
			if (!hasRealPK) {
				continue;
			}
			boolean isIdentity = onePk && "true".equalsIgnoreCase(quickColMeta.getAutoIncrement());
			if (onePk && strategy != null) {
				String pkStrategy = strategy.getStrategy();
				if (!strategy.isForce() && isIdentity) {
					quickColMeta.setStrategy("identity");
				} else if (pkStrategy.equalsIgnoreCase("assign") || pkStrategy.equalsIgnoreCase("generator")
						|| pkStrategy.equalsIgnoreCase("identity") || pkStrategy.equalsIgnoreCase("sequence")) {
					quickColMeta.setStrategy(pkStrategy);
					if (pkStrategy.equalsIgnoreCase("sequence")) {
						String sequence = strategy.getSequence();
						if (StringUtil.isBlank(sequence)) {
							throw new Exception(
									"please give a sequence for" + tableName + " where primary key strategy is sequence!");
						}
						// 支持sequence命名以seq_${tableName} 跟表名有规则相关模式;
						// 表名可能含$(oracle标识符合法字符)或\,须quoteReplacement避免被当作分组引用/转义
						quickColMeta.setSequence(sequence.replaceFirst("(?i)\\$?\\{\\s*tableName\\s*\\}",
								java.util.regex.Matcher.quoteReplacement(tableName)));
					}
					if (pkStrategy.equalsIgnoreCase("generator")) {
						String generator = strategy.getGenerator();
						if (StringUtil.isNotBlank(generator)) {
							quickColMeta.setGenerator(generator);
						}
						// 设置default generator
						if (StringUtil.isBlank(generator) || generator.equalsIgnoreCase("default")) {
							quickColMeta.setGenerator(PK_DEFAULT_GENERATOR);
						} else if (generator.equalsIgnoreCase("UUID")) {
							quickColMeta.setGenerator(PK_UUID_GENERATOR);
						}
						// 雪花算法
						else if (generator.equalsIgnoreCase("snowflake")) {
							quickColMeta.setGenerator(PK_SNOWFLAKE_GENERATOR);
						}
						// 纳秒
						else if (generator.equalsIgnoreCase("nanotime")) {
							quickColMeta.setGenerator(PK_NANOTIME_ID_GENERATOR);
						}
						// 基于redis的主键策略
						else if (generator.equalsIgnoreCase("redis")) {
							quickColMeta.setGenerator(PK_REDIS_ID_GENERATOR);
						}
						// ULID(xsd定义了但历史版本遗漏映射)
						else if (generator.equalsIgnoreCase("ulid")) {
							quickColMeta.setGenerator(PK_ULID_GENERATOR);
						}
					}
				} else {
					throw new Exception(Msg.get("请检查表:", "Please check primaryKey Strategy for table of ")
							+ tableName
							+ Msg.get(" 的primaryKey Strategy配置,可用值:sequence、assign、generator、identity",
									", valid strategies: sequence/assign/generator/identity"));
				}
			} else if (isIdentity) {
				quickColMeta.setStrategy("identity");
			} else if (onePk) {
				applyPrecisionStrategy(quickColMeta);
			}
			// 当主键是雪花算法、默认的22位、26位数字类型，将java类型改成BigInteger类型
			upgradeNumericType(quickColMeta, impList);
			// 仅处理第一个真实主键列
			return;
		}
	}

	/**
	 * 未配置主键策略时按主键数据类型和长度推导:>=19雪花算法(63位long当前为19位十进制)、
	 * >=22默认22位、>=26纳秒26位
	 */
	private static void applyPrecisionStrategy(QuickColMeta quickColMeta) {
		String dataType = quickColMeta.getDataType();
		boolean textType = "varchar".equalsIgnoreCase(dataType) || "char".equalsIgnoreCase(dataType);
		boolean numericType = "long".equalsIgnoreCase(dataType) || "integer".equalsIgnoreCase(dataType)
				|| "decimal".equalsIgnoreCase(dataType) || "number".equalsIgnoreCase(dataType)
				|| "NUMERIC".equalsIgnoreCase(dataType) || "BIGINT".equalsIgnoreCase(dataType)
				|| "BIGINTEGER".equalsIgnoreCase(dataType) || "BIGDECIMAL".equalsIgnoreCase(dataType);
		if (!textType && !numericType) {
			return;
		}
		int precision = quickColMeta.getPrecision();
		// 雪花为63位long(当前时代19位十进制),列宽不足19会溢出,故阈值取19
		if (precision >= 19) {
			quickColMeta.setStrategy("generator");
			quickColMeta.setGenerator(PK_SNOWFLAKE_GENERATOR);
		}
		if (precision >= 22) {
			quickColMeta.setStrategy("generator");
			quickColMeta.setGenerator(PK_DEFAULT_GENERATOR);
		}
		if (precision >= 26) {
			quickColMeta.setStrategy("generator");
			quickColMeta.setGenerator(PK_NANOTIME_ID_GENERATOR);
		}
	}

	/**
	 * 雪花算法等长数字主键,将int/short/long等易溢出类型升格为BigInteger
	 */
	private static void upgradeNumericType(QuickColMeta quickColMeta, List<String> impList) {
		String generator = quickColMeta.getGenerator() == null ? "" : quickColMeta.getGenerator();
		String resultType = quickColMeta.getResultType() == null ? "" : quickColMeta.getResultType().toLowerCase();
		boolean upgrade = false;
		// 雪花算法非法类型统一转BigInteger
		if (generator.equals(PK_SNOWFLAKE_GENERATOR)
				&& (resultType.equals("int") || resultType.equals("integer") || resultType.equals("short"))) {
			upgrade = true;
		} else if ((generator.equals(PK_DEFAULT_GENERATOR) || generator.equals(PK_NANOTIME_ID_GENERATOR))
				&& (resultType.equals("int") || resultType.equals("integer") || resultType.equals("short")
						|| resultType.equals("long"))) {
			upgrade = true;
		}
		if (upgrade) {
			quickColMeta.setResultType("BigInteger");
			if (!impList.contains("java.math.BigInteger")) {
				impList.add("java.math.BigInteger");
			}
		}
	}
}
