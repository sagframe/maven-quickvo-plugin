package org.sagacity.quickvo.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.sagacity.quickvo.model.CascadeModel;
import org.sagacity.quickvo.model.QuickModel;
import org.sagacity.quickvo.model.QuickVO;
import org.sagacity.quickvo.model.TableConstractModel;
import org.sagacity.quickvo.utils.StringUtil;

/**
 * 级联模型构建:主键被其它表作为外键引用时,构造OneToMany关联及级联配置。
 *
 * @author zhongxuchen
 */
public class CascadeResolver {

	/**
	 * 处理主键被其它表作为外键关联(exported keys),合并同表多列外键并附加cascade配置
	 */
	public static void processExportTables(QuickVO quickVO, List<TableConstractModel> exportKeys,
			QuickModel quickModel, List<CascadeModel> cascadeModels) {
		if (exportKeys == null || exportKeys.isEmpty()) {
			return;
		}
		HashMap<String, TableConstractModel> subTablesMap = new HashMap<>();
		String fieldPrefix = quickModel.getFieldRidPrefix();
		for (TableConstractModel exportKey : exportKeys) {
			// 外键ON DELETE规则推导级联删除:JDBC DELETE_RULE(0=CASCADE,1=RESTRICT,2=SET NULL,
			// 3=NO ACTION,4=SET DEFAULT)归一为模板通道的布尔语义(1=级联删除子表,0=不删除),
			// 仅库端声明ON DELETE CASCADE时才生成delete=true,与库端约束意图一致;
			// 历史行为把原始规则值直通模板(deleteRule==1→delete=true),导致默认RESTRICT外键
			// 反而被级联删除、真CASCADE反而不输出,且同一约束在不同驱动下产物不一致
			exportKey.setDeleteRule(
					exportKey.getDeleteRule() == java.sql.DatabaseMetaData.importedKeyCascade ? 1 : 0);
			String refTable = exportKey.getPkRefTableName();
			String pkColJavaName = humpWithoutPrefix(exportKey.getPkColName(), fieldPrefix, false);
			String pkRefColJavaName = humpWithoutPrefix(exportKey.getPkRefColName(), fieldPrefix, false);
			if (subTablesMap.containsKey(refTable)) {
				// 同一子表多列外键,拼接列和关联条件
				TableConstractModel subTable = subTablesMap.get(refTable);
				subTable.setPkColName(subTable.getPkColName() + ",\"" + exportKey.getPkColName() + "\"");
				subTable.setPkColJavaName(subTable.getPkColJavaName() + ",\"" + pkColJavaName + "\"");
				subTable.setPkRefColName(subTable.getPkRefColName() + ",\"" + exportKey.getPkRefColName() + "\"");
				subTable.setPkRefColJavaName(subTable.getPkRefColJavaName() + ",\"" + pkRefColJavaName + "\"");
				subTable.setPkEqualsFkStr(subTable.getPkEqualsFkStr().concat("&& main.get")
						.concat(StringUtil.firstToUpperCase(pkColJavaName)).concat("().equals(item.get")
						.concat(StringUtil.firstToUpperCase(pkRefColJavaName)).concat("())"));
				continue;
			}
			subTablesMap.put(refTable, exportKey);
			String refJavaTable = StringUtil.toHumpStr(refTable, true, true);
			if (quickModel.isHasEntity()) {
				refJavaTable = StringUtil.firstToUpperCase(StringUtil.replaceStr(quickModel.getEntityName(),
						"#{subName}", StringUtil.subStart(refJavaTable, quickModel.getEntitySubstr())));
			} else {
				refJavaTable = StringUtil.firstToUpperCase(StringUtil.replaceStr(quickModel.getVoName(), "#{subName}",
						StringUtil.subStart(refJavaTable, quickModel.getVoSubstr())));
			}
			exportKey.setPkRefTableJavaName(refJavaTable);
			exportKey.setPkColName("\"" + exportKey.getPkColName() + "\"");
			exportKey.setPkColJavaName("\"" + pkColJavaName + "\"");
			exportKey.setPkRefColName("\"" + exportKey.getPkRefColName() + "\"");
			exportKey.setPkRefColJavaName("\"" + pkRefColJavaName + "\"");
			exportKey.setPkEqualsFkStr("main.get".concat(StringUtil.firstToUpperCase(pkColJavaName))
					.concat("().equals(item.get").concat(StringUtil.firstToUpperCase(pkRefColJavaName)).concat("())"));
			if (cascadeModels != null) {
				for (CascadeModel cascadeModel : cascadeModels) {
					if (refTable.equalsIgnoreCase(cascadeModel.getTableName())) {
						exportKey.setLoad(cascadeModel.getLoad());
						exportKey.setAutoSave(cascadeModel.isSave() ? 1 : 0);
						// delete走模板实际读取的deleteRule通道(0/1);只有显式配置delete时才覆盖外键推导结果,
						// 未配置(null)则保持库端ON DELETE规则——避免"仅为配置load/orderBy"的cascade条目
						// 把默认RESTRICT外键误开成级联删除
						Boolean configuredDelete = cascadeModel.getDelete();
						if (configuredDelete != null) {
							exportKey.setDeleteRule(configuredDelete ? 1 : 0);
						}
						exportKey.setUpdateSql(cascadeModel.getUpdateSql());
						exportKey.setOrderBy(cascadeModel.getOrderBy());
						exportKey.setNotNullField(cascadeModel.getNotNullField());
					}
				}
			}
		}
		quickVO.setExportTables(new ArrayList<>(subTablesMap.values()));
	}

	/** 列名转驼峰,存在统一前缀时剔除前缀 */
	private static String humpWithoutPrefix(String colName, String fieldPrefix, boolean firstUpper) {
		if (StringUtil.isNotBlank(fieldPrefix)
				&& colName.toLowerCase(java.util.Locale.ROOT).startsWith(fieldPrefix)) {
			return StringUtil.toHumpStr(colName.substring(fieldPrefix.length()), firstUpper, true);
		}
		return StringUtil.toHumpStr(colName, firstUpper, true);
	}
}
