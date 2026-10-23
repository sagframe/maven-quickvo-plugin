package org.sagacity.quickvo.utils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * 基于jdk自带日志框架,避免给插件使用方带入额外日志依赖,同时在执行目录留档quickvo.log便于排查。
 *
 * @author zhongxuchen
 */
public class LoggerUtil {
	private static final Object LOCK = new Object();
	private static Logger logger = null;
	private static Path handlerDir = null;
	/** 日志文件编码,可由mojo参数loggerFileEncoding覆盖 */
	private static String fileEncoding = "UTF-8";

	private LoggerUtil() {
	}

	/** 设置quickvo.log文件编码(默认UTF-8) */
	public static void setFileEncoding(String encoding) {
		if (encoding != null && !encoding.trim().isEmpty()) {
			fileEncoding = encoding.trim();
		}
	}

	/**
	 * 获取quickvo日志;同JVM内baseDir变化时(如maven多模块连续执行)切换日志文件,
	 * 避免上一个模块的日志句柄一直占用旧目录。
	 */
	public static Logger getLogger(Path baseDir) {
		synchronized (LOCK) {
			if (logger == null || handlerDir == null || !handlerDir.equals(baseDir)) {
				Logger newLogger = Logger.getLogger("sagacity.quickvo");
				newLogger.setLevel(Level.ALL);
				Handler oldHandler = findFileHandler(newLogger);
				if (oldHandler != null) {
					newLogger.removeHandler(oldHandler);
					oldHandler.close();
				}
				try {
					Path dir = baseDir == null ? Path.of(System.getProperty("user.dir")) : baseDir;
					// FileHandler不会自动创建父目录
					java.nio.file.Files.createDirectories(dir);
					FileHandler handler = new FileHandler(FileUtil.linkPath(dir.toString(), "quickvo.log"), true);
					try {
						handler.setEncoding(resolveEncoding());
					} catch (Exception encodeFail) {
						// FileHandler构造即持有文件句柄,setEncoding失败须关闭,否则Windows下锁文件且句柄泄漏
						handler.close();
						throw encodeFail;
					}
					handler.setFormatter(new QuickVoFormatter());
					newLogger.addHandler(handler);
					handlerDir = dir;
				} catch (IOException e) {
					System.err.println(Msg.get("quickvo日志文件初始化失败:","quickvo log file init failed: ") + e.getMessage());
				}
				logger = newLogger;
			}
			return logger;
		}
	}

	public static Logger getLogger() {
		return getLogger(null);
	}

	private static String resolveEncoding() {
		String encoding = System.getProperty("logger.file.encoding");
		return encoding == null ? fileEncoding : encoding;
	}

	private static Handler findFileHandler(Logger target) {
		for (Handler handler : target.getHandlers()) {
			if (handler instanceof FileHandler) {
				return handler;
			}
		}
		return null;
	}

	/**
	 * 跟随Msg语言的日志格式化器:英文时输出US格式时间戳与英文级别名,
	 * 中文时保持SimpleFormatter默认(按系统Locale);逐条判定,支持运行中切换语言
	 */
	private static final class QuickVoFormatter extends SimpleFormatter {
		private final java.text.SimpleDateFormat enDate = new java.text.SimpleDateFormat("MMM dd, yyyy h:mm:ss a",
				java.util.Locale.US);

		@Override
		public synchronized String format(java.util.logging.LogRecord record) {
			if (Msg.isChinese()) {
				return super.format(record);
			}
			StringBuilder sb = new StringBuilder();
			sb.append(enDate.format(java.util.Date.from(record.getInstant()))).append(' ')
					.append(record.getSourceClassName());
			if (record.getSourceMethodName() != null) {
				sb.append(' ').append(record.getSourceMethodName());
			}
			sb.append('\n').append(record.getLevel().getName()).append(": ").append(formatMessage(record));
			Throwable thrown = record.getThrown();
			if (thrown != null) {
				sb.append('\n');
				java.io.StringWriter sw = new java.io.StringWriter();
				thrown.printStackTrace(new java.io.PrintWriter(sw));
				sb.append(sw);
			}
			sb.append('\n');
			return sb.toString();
		}
	}
}
