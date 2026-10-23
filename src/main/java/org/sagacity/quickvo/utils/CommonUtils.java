package org.sagacity.quickvo.utils;

import java.util.Date;
import java.util.regex.Pattern;

/**
 * 通用工具(数字判断/日期格式化)
 *
 * @author zhongxuchen
 */
public class CommonUtils {
	private static final Pattern NUMBER_PATTERN = Pattern.compile("^[+-]?[\\d]+(\\.\\d+)?$");

	private CommonUtils() {
	}

	/**
	 * 判断字符串是否为数字(整数或小数)
	 */
	public static boolean isNumber(String numberStr) {
		return numberStr != null && NUMBER_PATTERN.matcher(numberStr).matches();
	}

	/**
	 * 格式化日期
	 */
	public static String formatDate(Date dt, String fmt) {
		return new java.text.SimpleDateFormat(fmt).format(dt);
	}

	public static Date getNowTime() {
		return new Date();
	}
}
