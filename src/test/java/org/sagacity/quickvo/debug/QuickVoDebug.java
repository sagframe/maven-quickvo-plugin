package org.sagacity.quickvo.debug;

import java.nio.file.Path;

import org.sagacity.quickvo.QuickVoContext;
import org.sagacity.quickvo.QuickVoRunner;

/**
 * IDE本地调试入口:指定quickvo.xml执行生成,便于在test目录下调试模板/方言/配置解析。
 * 用法(IDE运行配置或断点调试):
 *   无参 → 使用 src/test/resources/golden/quickvo-mysql.xml
 *   参数1 → quickvo.xml路径(绝对或相对项目根)
 *   参数2 → 执行根路径(缺省当前工作目录)
 * 输出固定到 target/golden/debug,避免污染正式源码目录。
 */
public class QuickVoDebug {

	public static void main(String[] args) {
		String configFile = args != null && args.length > 0 ? args[0]
				: "src/test/resources/golden/quickvo-mysql.xml";
		Path baseDir = Path.of(args != null && args.length > 1 ? args[1] : System.getProperty("user.dir"));
		System.out.println("quickvo调试执行: config=" + configFile + ", baseDir=" + baseDir
				+ " (产物路径=配置文件中tasks的dist,相对baseDir解析)");
		QuickVoContext context = new QuickVoContext(baseDir, configFile);
		try {
			new QuickVoRunner(context).run();
			System.out.println("调试执行完成");
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
}
