/**   
* @Title: NotificationCallbackContext.java 
* @Package cn.fi.obj 
* @Description: TODO
* @author layrong
* @date 2016年4月11日 下午3:02:24 
* @version V1.0   
*/
package cn.fi.obj.sub;

import cn.fi.obj.msg.Message;

/**
 * 回调函数对应的上下文，作为函数的参数。在收到消息后，需要将相应信息放到此上下文中传递给回调函数
 * 
 */
public class NotificationCallbackContext {
	public Message message;

	public Message getMessage() {
		return message;
	}

	public void setMessage(Message message) {
		this.message = message;
	}

	public NotificationCallbackContext(Message message) {
		super();
		this.message = message;
	}
}
