/**   
* @Title: SubscriptionCallbackTuple.java 
* @Package cn.fi.obj 
* @Description: TODO
* @author layrong
* @date 2016年4月11日 下午3:01:27 
* @version V1.0   
*/
package cn.fi.obj.sub;

/**
 * 订阅回调元组，每一个订阅以及对应的回调函数
 * 
 */
public class SubscriptionCallbackTuple {
	public EventSubscription es;
	public NotificationCallback cb;//callback回调函数
}
