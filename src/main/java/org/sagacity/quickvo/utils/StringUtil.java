package org.sagacity.quickvo.utils;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 字符串处理工具(驼峰转换/占位符/正则匹配/hash)
 *
 * @author zhongxuchen
 */
public class StringUtil {

	private StringUtil() {
	}

	/**
	 * 数据库备注 → 转成可直接放入 Java 双引号字符串字面量的内容:
	 * 转义反斜杠、双引号,以及换行/制表/回车等控制字符(避免多行备注导致生成的实体无法编译)。
	 * 已成对的 \\ 保持原样(避免对已转义内容二次转义);单引号、正斜杠等原样保留。
	 */
	public static String escapeComment(String commentStr) {
		if (commentStr == null || commentStr.isEmpty()) {
			return commentStr;
		}
		StringBuilder sb = new StringBuilder(commentStr.length() + 20);
		char[] chars = commentStr.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			char c = chars[i];
			if (c == '\\' && i < chars.length - 1 && chars[i + 1] == '\\') {
				sb.append("\\\\");
				i++;
			} else if (c == '\\') {
				sb.append("\\\\");
			} else if (c == '"') {
				sb.append("\\\"");
			} else if (c == '\n') {
				sb.append("\\n");
			} else if (c == '\r') {
				sb.append("\\r");
			} else if (c == '\t') {
				sb.append("\\t");
			} else if (c == '\b') {
				sb.append("\\b");
			} else if (c == '\f') {
				sb.append("\\f");
			} else if (c < 0x20) {
				sb.append(String.format("\\u%04x", (int) c));
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	public static boolean isNotBlank(Object str) {
		return !isBlank(str);
	}

	public static boolean isBlank(Object str) {
		return str == null || str.toString().trim().isEmpty();
	}

	public static String trim(String str) {
		return str == null ? null : str.trim();
	}

	/**
	 * 替换换行、回车、tab符
	 */
	public static String clearMistyChars(String source, String target) {
		return source == null ? null : source.replaceAll("\t|\r|\n", target);
	}

	public static String firstToUpperCase(String sourceStr) {
		if (isBlank(sourceStr)) {
			return sourceStr;
		}
		if (sourceStr.length() == 1) {
			return sourceStr.toUpperCase(Locale.ROOT);
		}
		return sourceStr.substring(0, 1).toUpperCase(Locale.ROOT)
				.concat(sourceStr.substring(1));
	}

	public static String firstToLowerCase(String sourceStr) {
		if (isBlank(sourceStr)) {
			return sourceStr;
		}
		if (sourceStr.length() == 1) {
			return sourceStr.toLowerCase(Locale.ROOT);
		}
		return sourceStr.substring(0, 1).toLowerCase(Locale.ROOT)
				.concat(sourceStr.substring(1));
	}

	private static String firstToUpperOtherToLower(String sourceStr) {
		if (sourceStr.isEmpty()) {
			return sourceStr;
		}
		return sourceStr.substring(0, 1).toUpperCase(Locale.ROOT)
				.concat(sourceStr.substring(1).toLowerCase(Locale.ROOT));
	}

	public static int indexOfIgnoreCase(String source, String pattern) {
		if (source == null || pattern == null) {
			return -1;
		}
		return source.toLowerCase(Locale.ROOT).indexOf(pattern.toLowerCase(Locale.ROOT));
	}

	public static String replaceStr(String source, String template, String target) {
		return replaceStr(source, template, target, 0);
	}

	public static String replaceStr(String source, String template, String target, int fromIndex) {
		if (source == null || template == null) {
			return source;
		}
		int index = source.indexOf(template, fromIndex);
		if (index != -1) {
			source = source.substring(0, index).concat(target)
					.concat(source.substring(index + template.length()));
		}
		return source;
	}

	public static String replaceAllStr(String source, String template, String target) {
		if (source == null || template == null || template.equals(target)) {
			return source;
		}
		return source.replace(template, target);
	}

	/**
	 * 正则匹配(匹配语义为"包含",即find,非full match)
	 */
	public static boolean matches(String source, String regex) {
		return matches(source, Pattern.compile(regex));
	}

	public static boolean matches(String source, Pattern p) {
		return p.matcher(source).find();
	}

	/**
	 * 将下划线分隔的字符串转换为驼峰形式
	 *
	 * @param source           原始字符串(如 STAFF_ID)
	 * @param firstIsUpperCase 首字母是否大写
	 * @param removeUnderscore 是否移除下划线(true=STAFF_ID→StaffId;false=STAFF_ID→Staff_Id)
	 */
	public static String toHumpStr(String source, boolean firstIsUpperCase, boolean removeUnderscore) {
		if (isBlank(source)) {
			return source;
		}
		String[] humpAry = source.split("_");
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < humpAry.length; i++) {
			String cell = humpAry[i];
			if (!removeUnderscore && i > 0) {
				result.append("_");
			}
			if (cell.toUpperCase(Locale.ROOT).equals(cell)) {
				result.append(firstToUpperOtherToLower(cell));
			} else {
				result.append(firstToUpperCase(cell));
			}
		}
		return firstIsUpperCase ? firstToUpperCase(result.toString())
				: firstToLowerCase(result.toString());
	}

	public static String subStart(String source, String start) {
		if (isBlank(start)) {
			return source;
		}
		if (source.toLowerCase(Locale.ROOT).startsWith(start.toLowerCase(Locale.ROOT))) {
			return source.substring(start.length());
		}
		return source;
	}

	/**
	 * 字符串hash(用于serialVersionUID,算法值不可变更)
	 */
	public static long hash(String key) {
		java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(
				key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		int seed = 0x1234ABCD;
		java.nio.ByteOrder byteOrder = buf.order();
		buf.order(java.nio.ByteOrder.LITTLE_ENDIAN);
		long m = 0xc6a4a7935bd1e995L;
		int r = 47;
		long h = seed ^ (buf.remaining() * m);
		long k;
		while (buf.remaining() >= 8) {
			k = buf.getLong();
			k *= m;
			k ^= k >>> r;
			k *= m;
			h ^= k;
			h *= m;
		}
		if (buf.remaining() > 0) {
			java.nio.ByteBuffer finish = java.nio.ByteBuffer.allocate(8)
					.order(java.nio.ByteOrder.LITTLE_ENDIAN);
			finish.put(buf).rewind();
			h ^= finish.getLong();
			h *= m;
		}
		h ^= h >>> r;
		h *= m;
		h ^= h >>> r;
		buf.order(byteOrder);
		return Math.abs(h);
	}

	public static int matchCnt(String source, String regex) {
		return matchCnt(source, Pattern.compile(regex));
	}

	public static int matchCnt(String source, Pattern p) {
		Matcher m = p.matcher(source);
		int count = 0;
		while (m.find()) {
			count++;
		}
		return count;
	}
}
