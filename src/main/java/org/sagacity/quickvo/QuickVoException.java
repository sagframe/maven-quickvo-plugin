package org.sagacity.quickvo;

/**
 * quickvo执行异常:任务失败或生成物存在致命错误(如字段重复)时抛出,令maven构建失败。
 *
 * @author zhongxuchen
 */
public class QuickVoException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public QuickVoException(String message) {
		super(message);
	}

	public QuickVoException(String message, Throwable cause) {
		super(message, cause);
	}
}
