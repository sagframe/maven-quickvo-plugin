package org.sagacity.quickvo.core;

import java.util.List;

import org.sagacity.quickvo.datasource.DbType;
import org.sagacity.quickvo.model.ColumnTypeMapping;
import org.sagacity.quickvo.utils.StringUtil;

/**
 * 数据类型映射:native类型归一、默认类型映射、自定义type-mapping按精度/小数位匹配。
 *
 * @author zhongxuchen
 */
public class TypeMappingResolver {
	/**
	 * 归一native类型:剔除UNSIGNED、包名前缀、identity尾巴
	 */
	public static String normalizeNativeType(String typeName) {
		// 剔除掉UNSIGNED对类型的干扰
		String jdbcType = typeName.replaceFirst("(?i)\\sUNSIGNED", "");
		if (typeName.indexOf(".") != -1) {
			jdbcType = typeName.substring(typeName.lastIndexOf(".") + 1);
		}
		// sqlserver和sybase数据库identity主键类别包含identity字符
		return jdbcType.replaceFirst("(?i)\\s*identity", "").trim();
	}

	/**
	 * native类型转标准jdbc类型(clickhouse的datetime转TIMESTAMP等)
	 */
	public static String toJdbcType(String nativeType, DbType dbType) {
		if (dbType == DbType.CLICKHOUSE && nativeType.equalsIgnoreCase("datetime")) {
			return "TIMESTAMP";
		}
		String lookupName = normalizeJdbcLookupName(nativeType);
		for (String[] types : dbType.jdbcTypeAry()) {
			if (lookupName.equalsIgnoreCase(types[0])) {
				return types[1];
			}
		}
		return nativeType;
	}

	/**
	 * 归一jdbc查找名:剔除精度括号、统一时区后缀空白形态——oracle/h2上报的标准名
	 * "TIMESTAMP(6) WITH TIME ZONE"与映射表的"TIMESTAMP WITH TIMEZONE"差括号和空白,
	 * 不归一则落入原样返回,模板拼出含空格的非法JdbcTypes常量
	 */
	private static String normalizeJdbcLookupName(String nativeType) {
		return nativeType.replaceAll("\\(\\d+\\)", "").replaceAll("\\s+", " ")
				.replace(" WITH LOCAL TIME ZONE", " WITH TIMEZONE").replace(" WITH TIME ZONE", " WITH TIMEZONE")
				.trim();
	}

	/**
	 * 是否已知jdbc类型名(各方言映射目标并集或纯数字):模板对非数字dataType拼JdbcTypes.<名称>,
	 * 未知名称会产出无法解析的常量导致生成物编译失败,调用方据此兜底Types.OTHER
	 */
	public static boolean isKnownJdbcType(String dataType) {
		if (dataType == null || dataType.matches("\\d+")) {
			return true;
		}
		for (DbType type : DbType.values()) {
			for (String[] types : type.jdbcTypeAry()) {
				if (dataType.equalsIgnoreCase(types[1])) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * 默认native类型到java类型映射;返回[0]=java类型,[1]=需import类型(空表示无需import),未命中返回null
	 */
	public static String[] defaultJavaType(String sqlType, String dialect, DbType dbType) {
		for (String[] jdbcTypeMap : dbType.jdbcTypeMapping()) {
			if (sqlType.equalsIgnoreCase(jdbcTypeMap[0])) {
				// 针对一些数据库要求提供数据库类型和数据类型双重判断
				if ((jdbcTypeMap.length == 4 && dialect.equals(jdbcTypeMap[3])) || jdbcTypeMap.length == 3) {
					return new String[] { jdbcTypeMap[1], StringUtil.isBlank(jdbcTypeMap[2]) ? null
							: jdbcTypeMap[2] };
				}
			}
		}
		return null;
	}

	/**
	 * 自定义类型映射(逆向匹配,后配置优先);命中返回映射,未命中返回null。
	 * tableField为表.字段小写形式,precision/scale为列实际精度(scale超出maxScale时截断)
	 */
	public static ColumnTypeMapping matchCustomMapping(List<ColumnTypeMapping> typeMappings, String tableField,
			String sqlType, int precision, int scale, int maxScale) {
		if (typeMappings == null || typeMappings.isEmpty()) {
			return null;
		}
		if (maxScale != -1 && scale > maxScale) {
			scale = maxScale;
		}
		// 逆向进行匹配
		for (int j = typeMappings.size() - 1; j >= 0; j--) {
			ColumnTypeMapping colTypeMapping = typeMappings.get(j);
			if (StringUtil.isBlank(colTypeMapping.getTableField())
					|| colTypeMapping.getTableField().equals(tableField)) {
				// 类型一致(小写)
				if (colTypeMapping.getNativeTypes().containsKey(sqlType) && precisionScaleMatch(colTypeMapping,
						precision, scale)) {
					return colTypeMapping;
				}
			}
		}
		return null;
	}

	/**
	 * 精度/小数位区间匹配:min..max、min..、..max、单值(=min==max)四种形式统一按上下界独立判断,
	 * -1表示该侧不设限。修复历史缺陷——旧实现以max==-1为"不判断"开关,导致"min.."开区间的min被忽略
	 * (如precision="10.."会错误匹配precision=1)
	 */
	private static boolean precisionScaleMatch(ColumnTypeMapping mapping, int precision, int scale) {
		boolean precisionOk = (mapping.getPrecisionMin() == -1 || precision >= mapping.getPrecisionMin())
				&& (mapping.getPrecisionMax() == -1 || precision <= mapping.getPrecisionMax());
		boolean scaleOk = (mapping.getScaleMin() == -1 || scale >= mapping.getScaleMin())
				&& (mapping.getScaleMax() == -1 || scale <= mapping.getScaleMax());
		return precisionOk && scaleOk;
	}

}
