package org.sagacity.quickvo;

import java.nio.file.Path;
import org.sagacity.quickvo.utils.Msg;

/**
 * 命令行入口(本地调试/非maven场景):args[0]=quickvo.xml路径,args[1]=执行根路径(缺省当前目录)。
 *
 * @author zhongxuchen
 */
public class QuickVoCli {

	public static void main(String[] args) {
		String configFile = args != null && args.length > 0 ? args[0] : "quickvo.xml";
		String baseDir = args != null && args.length > 1 ? args[1] : System.getProperty("user.dir");
		System.out.println("\n\n=========    welcome use quickvo     ==========\n");
		QuickVoContext context = new QuickVoContext(Path.of(baseDir), configFile);
		try {
			new QuickVoRunner(context).run();
		} catch (Exception e) {
			System.err.println(Msg.get("quickvo执行失败:","quickvo failed: ") + e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}
	}
}
