/**   
* @Title: NotificationCallback.java 
* @Package cn.fi.obj 
* @Description: TODO
* @author layrong
* @date 2016年4月11日 下午3:03:33 
* @version V1.0   
*/
package cn.fi.obj.sub;

/**
 * 订阅对应的回调函数，每当收到订阅的消息后，会执行此函数
 * 
 */
public interface NotificationCallback {
	public void exec(EventSubscription oes, NotificationCallbackContext cntx);
}