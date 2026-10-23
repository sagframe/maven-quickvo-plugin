package org.sagacity.quickvo.datasource;

import java.util.List;
import java.util.Locale;

/**
 * schema/catalog按数据库类型的容错归一:已识别DbType后,依据方言语义自动纠正常见配置错误——
 * 1) mysql系:仅catalog(库名)有效,用户把库名配到schema上→挪到catalog,schema清空;
 * 2) oracle/dm系:仅schema(用户名)有效,catalog误配→挪到schema,并大写化(元数据全大写);
 * 3) postgresql系:仅schema(模式名)有效,catalog误配→挪到schema,大小写以库内实际清单为准
 *    (用户配大写PUBLIC而库内是public时自动纠正);
 * 4) sqlserver/db2:双值有效,大小写由库排序规则自然容错,仅做schema的清单大小写纠正;
 * 5) 其它类型:保持原样。
 * 探测不到可用schema清单或无唯一命中时保持原样,绝不因容错引入新的失败
 *
 * @author zhongxuchen
 */
public final class SchemaCatalogNormalizer {

	private SchemaCatalogNormalizer() {
	}

	/**
	 * @param dbType           已识别的数据库类型
	 * @param catalog          用户配置的catalog(可null)
	 * @param schema           用户配置的schema(可null)
	 * @param availableSchemas 库内实际可用的schema清单(可空,探测失败场景)
	 * @return [归一后catalog, 归一后schema]
	 */
	public static String[] normalize(DbType dbType, String catalog, String schema, List<String> availableSchemas) {
		String cat = trimToNull(catalog);
		String sch = trimToNull(schema);
		switch (dbType) {
		case MYSQL, MYSQL57, TIDB, OCEANBASE: {
			// mysql系(含OceanBase mysql模式):库名只有catalog语义,schema误配挪到catalog
			if (cat == null && sch != null) {
				cat = sch;
			}
			return new String[] { cat, null };
		}
		case ORACLE, ORACLE11, DM: {
			// oracle/dm系:仅schema(用户名),catalog误配挪为schema,元数据全大写
			if (sch == null && cat != null) {
				sch = cat;
			}
			return new String[] { null, matchCase(sch, availableSchemas, true) };
		}
		case POSTGRESQL, POSTGRESQL14, GAUSSDB, OPENGAUSS, MOGDB, VASTBASE, STARDB, OSCAR, KINGBASE, HIGHGO: {
			// pg系:仅schema(模式名),catalog误配挪为schema,大小写以库内实际为准
			if (sch == null && cat != null) {
				sch = cat;
			}
			return new String[] { null, matchCase(sch, availableSchemas, false) };
		}
		case SQLSERVER: {
			// 双值均有效,仅做schema大小写清单纠正
			return new String[] { cat, matchCase(sch, availableSchemas, false) };
		}
		case DB2: {
			// DB2的JDBC元数据不接受catalog:传非null(库名)时getTables/getExportedKeys均返回0行
			// (DB2 12.1.5实测),库名已在连接URL中,故只保留schema(用户名)并纠正大小写
			return new String[] { null, matchCase(sch, availableSchemas, false) };
		}
		default:
			return new String[] { cat, sch };
		}
	}

	/**
	 * 依据库内实际schema清单纠正大小写:大小写不敏感唯一命中时用真实值;
	 * oracle系无清单时默认大写化(惯例全大写);其余场景保持原样
	 */
	private static String matchCase(String schema, List<String> availableSchemas, boolean upperByDefault) {
		if (schema == null) {
			return null;
		}
		if (availableSchemas != null && !availableSchemas.isEmpty()) {
			String hit = null;
			for (String candidate : availableSchemas) {
				if (schema.equalsIgnoreCase(candidate)) {
					if (hit != null) {
						// 大小写不敏感命中多个(异常场景),放弃纠正保持原样
						return schema;
					}
					hit = candidate;
				}
			}
			if (hit != null) {
				return hit;
			}
			// 清单中不存在:保持原样,后续按"取不到表"的精准提示引导
			return schema;
		}
		// 无清单:oracle系按惯例大写化,其它保持
		return upperByDefault ? schema.toUpperCase(Locale.ROOT) : schema;
	}

	private static String trimToNull(String value) {
		if (value == null || value.trim().isEmpty()) {
			return null;
		}
		return value.trim();
	}
}
