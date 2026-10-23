package org.sagacity.quickvo.utils;

import java.util.Locale;

/**
 * 双语消息工具:语言控制两种方式(优先级从高到低):
 * 1. quickvo.xml中&lt;tasks log-language="en|zh|auto"/&gt; 强制指定
 * 2. 自动检测系统Locale(-Duser.language可影响)
 *
 * 用法: Msg.get("开始连接:", "Connecting:") + name
 *
 * @author zhongxuchen
 */
public final class Msg {
	/** xml配置的强制语言(null表示未设置,走自动检测) */
	private static volatile String overrideLang = null;

	/** 是否中文环境(基于自动检测,启动时确定一次) */
	private static final boolean AUTO_ZH = "zh".equalsIgnoreCase(Locale.getDefault().getLanguage());

	private Msg() {
	}

	/**
	 * 设置强制输出语言(由quickvo.xml中tasks的log-language属性调用)
	 *
	 * @param lang "zh"或"en";null或"auto"恢复自动检测
	 */
	public static void setLanguage(String lang) {
		if (lang == null || lang.trim().isEmpty() || "auto".equalsIgnoreCase(lang.trim())) {
			overrideLang = null;
		} else {
			overrideLang = lang.trim().toLowerCase();
		}
	}

	/**
	 * 根据环境返回对应语言的消息
	 *
	 * @param zh 中文消息
	 * @param en 英文消息
	 * @return 当前语言对应的消息
	 */
	public static String get(String zh, String en) {
		return isChinese() ? zh : en;
	}

	/** 是否中文环境 */
	public static boolean isChinese() {
		if (overrideLang != null) {
			return "zh".equals(overrideLang);
		}
		return AUTO_ZH;
	}
}
