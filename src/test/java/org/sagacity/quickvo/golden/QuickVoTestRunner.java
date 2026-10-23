package org.sagacity.quickvo.golden;

import java.nio.file.Path;

import org.sagacity.quickvo.QuickVoContext;
import org.sagacity.quickvo.QuickVoRunner;

/**
 * golden测试的执行入口封装:保持签名稳定,内部走新版执行器。
 */
public final class QuickVoTestRunner {

	private QuickVoTestRunner() {
	}

	/**
	 * 以指定quickvo.xml和baseDir执行一次完整生成
	 */
	public static void run(Path configFile, Path baseDir) throws Exception {
		QuickVoContext context = new QuickVoContext(baseDir, configFile.toString());
		new QuickVoRunner(context).run();
	}
}
