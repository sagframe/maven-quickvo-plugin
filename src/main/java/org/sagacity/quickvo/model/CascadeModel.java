/**
 * 
 */
package org.sagacity.quickvo.model;

import java.io.Serializable;

/**
 * @project sagacity-quickvo
 * @description 级联关系模型
 * @author zhongxuchen
 * @version v1.0,Date:2016年12月1日
 */
public class CascadeModel implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 5784993887439945140L;

	/**
	 * 级联的字表名称，正则表达式
	 */
	private String tableName;

	/**
	 * 主表删除子表是否删除;null=未配置:跟随外键ON DELETE规则推导(仅ON DELETE CASCADE才级联删除),
	 * 只有显式delete="true"/"false"才覆盖——避免"仅为配置load/orderBy"的cascade条目误开级联删除
	 */
	private Boolean delete;

	/**
	 * 主表保存，子表是否保存
	 */
	private boolean save = true;

	/**
	 * 主表加载，子表是否自动加载
	 */
	private String load;

	/**
	 * 修改主表时对于字表级联保存时是否先做删除操作
	 */
	private String updateSql;
	
	private String orderBy;

	/**
	 * 判断join子表是否存在数据的字段(@OneToMany(notNullField=..))
	 */
	private String notNullField;

	/**
	 * @return the tableName
	 */
	public String getTableName() {
		return tableName;
	}

	/**
	 * @param tableName the tableName to set
	 */
	public void setTableName(String tableName) {
		this.tableName = tableName;
	}

	/**
	 * @return the delete(null=未配置,跟随外键推导)
	 */
	public Boolean getDelete() {
		return delete;
	}

	/**
	 * @param delete the delete to set
	 */
	public void setDelete(Boolean delete) {
		this.delete = delete;
	}

	/**
	 * @return the save
	 */
	public boolean isSave() {
		return save;
	}

	/**
	 * @param save the save to set
	 */
	public void setSave(boolean save) {
		this.save = save;
	}

	/**
	 * @return the load
	 */
	public String getLoad() {
		return load;
	}

	/**
	 * @param load the load to set
	 */
	public void setLoad(String load) {
		this.load = load;
	}

	/**
	 * @return the updateSql
	 */
	public String getUpdateSql() {
		return updateSql;
	}

	/**
	 * @param updateSql the updateSql to set
	 */
	public void setUpdateSql(String updateSql) {
		this.updateSql = updateSql;
	}

	/**
	 * @return the orderBy
	 */
	public String getOrderBy() {
		return orderBy;
	}

	/**
	 * @param orderBy the orderBy to set
	 */
	public void setOrderBy(String orderBy) {
		this.orderBy = orderBy;
	}


	/**
	 * @return the notNullField
	 */
	public String getNotNullField() {
		return notNullField;
	}

	/**
	 * @param notNullField the notNullField to set
	 */
	public void setNotNullField(String notNullField) {
		this.notNullField = notNullField;
	}
}
