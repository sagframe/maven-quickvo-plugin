package org.sagacity.quickvo.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件处理工具(Java 17实现:try-with-resources + NIO.2 + readAllBytes)
 *
 * @author zhongxuchen
 */
public class FileUtil {

	private FileUtil() {
	}

	/**
	 * 将文件读到字符串(指定编码;编码空白时按UTF-8而非平台默认编码,避免产物在中文Windows上被按GBK解码)
	 */
	public static String readAsString(File file, String charset) throws IOException {
		byte[] fileBytes = Files.readAllBytes(file.toPath());
		if (StringUtil.isBlank(charset)) {
			return new String(fileBytes, java.nio.charset.StandardCharsets.UTF_8);
		}
		return new String(fileBytes, charset);
	}

	/**
	 * 将字符串存为文件(自动创建父目录;编码null时按UTF-8而非平台默认编码)
	 */
	public static void putStringToFile(String content, String fileName, String charset) throws IOException {
		java.nio.file.Path path = java.nio.file.Path.of(fileName);
		if (path.getParent() != null) {
			Files.createDirectories(path.getParent());
		}
		if (charset != null) {
			Files.write(path, content.getBytes(charset));
		} else {
			Files.write(path, content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		}
	}

	/**
	 * 递归获取指定目录下的文件(可按通配符过滤)
	 */
	public static List<File> getPathFiles(File parentFile, String... filters) {
		List<File> fileList = new ArrayList<>();
		if (parentFile == null || !parentFile.exists()) {
			return fileList;
		}
		collectFiles(parentFile, fileList, filters);
		return fileList;
	}

	public static List<File> getPathFiles(Object baseDir, String... filters) {
		if (baseDir == null) {
			return new ArrayList<>();
		}
		File file = baseDir instanceof String s ? new File(s) : (File) baseDir;
		return getPathFiles(file, filters);
	}

	private static void collectFiles(File parentFile, List<File> fileList, String... filters) {
		if (parentFile.isDirectory()) {
			File[] files = parentFile.listFiles();
			if (files == null) {
				return;
			}
			for (File child : files) {
				if (child.isDirectory()) {
					collectFiles(child, fileList, filters);
				} else {
					matchFilters(fileList, child, filters);
				}
			}
		} else {
			matchFilters(fileList, parentFile, filters);
		}
	}

	private static void matchFilters(List<File> fileList, File file, String... filters) {
		if (filters == null || filters.length == 0) {
			fileList.add(file);
		} else {
			for (String filter : filters) {
				if (StringUtil.matches(file.getName(), filter)) {
					fileList.add(file);
					break;
				}
			}
		}
	}

	/**
	 * 判断是否为绝对根路径(Windows盘符或Unix根目录)
	 */
	public static boolean isRootPath(String path) {
		if (path == null || path.isEmpty()) {
			return false;
		}
		if (System.getProperty("os.name").toUpperCase(java.util.Locale.ROOT).contains("WINDOWS")) {
			// Windows盘符=单字母+冒号+路径分隔符;历史正则^[a-zA-Z]+:\w*配合find语义
			// 会把"classpath:xxx"等多字母冒号前缀误判为绝对路径
			return StringUtil.matches(path, "^[a-zA-Z]:[\\\\/]");
		}
		return path.startsWith("/") || path.startsWith(File.separator);
	}

	/**
	 * 新建目录(已存在则跳过)
	 */
	public static void createFolder(String folderPath) {
		try {
			File dir = new File(folderPath);
			if (!dir.exists()) {
				dir.mkdirs();
			}
		} catch (Exception e) {
			System.err.println(Msg.get("创建目录失败: ","Failed to create directory: ") + folderPath + " -> " + e.getMessage());
		}
	}

	/**
	 * 路径拼接(自动处理分隔符;lowPath为绝对路径时直接返回)
	 */
	public static String linkPath(String topPath, String lowPath) {
		if (lowPath != null && isRootPath(lowPath)) {
			return lowPath;
		}
		String first = topPath == null ? "" : topPath;
		String second = lowPath == null ? "" : lowPath;
		if (first.concat(second).trim().isEmpty()) {
			return "";
		}
		String separator = File.separator;
		if (!first.isEmpty()) {
			char last = first.charAt(first.length() - 1);
			if (last == '/' || last == '\\') {
				first = first.substring(0, first.length() - 1);
			}
			first = first + separator;
		} else {
			first = separator;
		}
		if (!second.isEmpty() && (second.charAt(0) == '/' || second.charAt(0) == '\\')) {
			second = second.substring(1);
		}
		return first.concat(second);
	}

	/**
	 * 统一路径分隔符为当前系统格式
	 */
	public static String formatPath(String path) {
		return path.replace("\\", File.separator).replace("/", File.separator);
	}

	/**
	 * 解析相对路径中的../(回退到父目录)
	 */
	public static String skipPath(String basePath, String skipFile) {
		String realFile = formatPath(skipFile).trim();
		if (realFile.startsWith("." + File.separator)) {
			realFile = realFile.substring(2);
		}
		String pattern = ".." + File.separator;
		File tmpFile = new File(basePath);
		String lastFile = tmpFile.getPath();
		while (realFile.startsWith(pattern)) {
			lastFile = tmpFile.getParent();
			tmpFile = tmpFile.getParentFile();
			realFile = realFile.substring(3);
		}
		return linkPath(lastFile, realFile);
	}

	/**
	 * 获取classpath资源对应的文件(classpath:前缀走类加载器,否则直接File)
	 */
	public static File getFile(String fileName) {
		if (fileName == null) {
			return null;
		}
		String trimmed = fileName.trim();
		if (trimmed.toLowerCase(java.util.Locale.ROOT).startsWith("classpath:")) {
			String realPath = trimmed.substring(10).trim();
			if (realPath.charAt(0) == '/') {
				realPath = realPath.substring(1);
			}
			var url = Thread.currentThread().getContextClassLoader().getResource(realPath);
			if (url != null && "file".equals(url.getProtocol())) {
				try {
					return new File(url.toURI());
				} catch (java.net.URISyntaxException e) {
					return null;
				}
			}
			return null;
		}
		return new File(fileName);
	}

	/**
	 * 获取classpath资源流(兼容容器类加载器)
	 */
	public static InputStream getResourceAsStream(String resource) {
		String realRes = resource.charAt(0) == '/' ? resource.substring(1) : resource;
		InputStream result = Thread.currentThread().getContextClassLoader().getResourceAsStream(realRes);
		if (result == null) {
			try {
				result = new FileInputStream(realRes);
			} catch (IOException e) {
				// 资源不存在,返回null由调用方处理
			}
		}
		return result;
	}

	/**
	 * 按指定编码读取classpath文本资源(行尾统一为\r\n且保证末尾换行,保持生成物字节稳定;
	 * 编码null时按UTF-8而非平台默认编码)
	 */
	public static String inputStream2String(InputStream is, String encoding) {
		if (is == null) {
			return "";
		}
		try {
			byte[] bytes = is.readAllBytes();
			String content = encoding != null ? new String(bytes, encoding)
					: new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
			// 行尾统一为\r\n(模板行尾影响生成物字节,历史行为:readLine逐行追加\r\n)
			content = content.replace("\r\n", "\n").replace("\n", "\r\n");
			if (!content.isEmpty() && !content.endsWith("\r\n")) {
				content = content + "\r\n";
			}
			return content;
		} catch (IOException e) {
			return "";
		} finally {
			closeQuietly(is);
		}
	}

	private static void closeQuietly(java.io.Closeable... closeables) {
		for (java.io.Closeable c : closeables) {
			if (c != null) {
				try {
					c.close();
				} catch (IOException e) {
					// 静默关闭
				}
			}
		}
	}
}
